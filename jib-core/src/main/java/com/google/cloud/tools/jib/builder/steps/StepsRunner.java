/*
 * Copyright 2018 Google LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.cloud.tools.jib.builder.steps;

import com.google.cloud.tools.jib.blob.BlobDescriptor;
import com.google.cloud.tools.jib.builder.ProgressEventDispatcher;
import com.google.cloud.tools.jib.builder.steps.PullBaseImageStep.ImageAndAuthorization;
import com.google.cloud.tools.jib.configuration.BuildConfiguration;
import com.google.cloud.tools.jib.configuration.credentials.Credential;
import com.google.cloud.tools.jib.docker.DockerClient;
import com.google.cloud.tools.jib.global.JibSystemProperties;
import com.google.cloud.tools.jib.http.Authorization;
import com.google.cloud.tools.jib.image.Image;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Function;
import javax.annotation.Nullable;

/**
 * Runs steps for building an image.
 *
 * <p>Use by first calling {@link #begin} and then calling the individual step running methods. Note
 * that order matters, so make sure that steps are run before other steps that depend on them. Wait
 * on the last step by calling the respective {@code wait...} methods.
 */
public class StepsRunner {

  /** Holds the individual future results. */
  private static class StepResults {
    @Nullable private Future<ImageAndAuthorization> baseImageAndAuth;
    @Nullable private Future<List<Future<CachedLayerAndName>>> baseImageLayers;
    @Nullable private List<Future<CachedLayerAndName>> applicationLayers;
    @Nullable private Future<Image> builtImage;
    @Nullable private Future<Credential> targetRegistryCredentials;
    @Nullable private Future<Authorization> pushAuthorization;
    @Nullable private Future<List<Future<BlobDescriptor>>> baseImageLayerPushResults;
    @Nullable private Future<List<Future<BlobDescriptor>>> applicationLayerPushResults;
    @Nullable private Future<BlobDescriptor> containerConfigurationPushResult;
    @Nullable private Future<BuildResult> buildResult;
  }

  @VisibleForTesting
  static <E> List<E> realizeFutures(List<Future<E>> futures)
      throws InterruptedException, ExecutionException {
    List<E> values = new ArrayList<>();
    for (Future<E> future : futures) {
      values.add(future.get());
    }
    return values;
  }

  @VisibleForTesting
  static ListeningExecutorService getListeningExecutorService(
      BuildConfiguration buildConfiguration) {
    ExecutorService executorService =
        JibSystemProperties.isSerializedExecutionEnabled()
            ? MoreExecutors.newDirectExecutorService()
            : buildConfiguration.getExecutorService();
    return MoreExecutors.listeningDecorator(executorService);
  }

  private final StepResults results = new StepResults();
  private Function<BuildConfiguration, BuildResult> buildPlan;

  private final ListeningExecutorService listeningExecutorService;
  private final BuildConfiguration buildConfiguration;

  private final Runnable rootProgressCloser;
  private final Queue<ProgressEventDispatcher.Factory> childProgressDispatcherSupplier =
      new ArrayDeque<>();

  private StepsRunner(
      ListeningExecutorService listeningExecutorService,
      BuildConfiguration buildConfiguration,
      String rootProgressDescription,
      int rootProgressUnits) {
    this.listeningExecutorService = listeningExecutorService;
    this.buildConfiguration = buildConfiguration;

    ProgressEventDispatcher rootProgressDispatcher =
        ProgressEventDispatcher.newRoot(
            buildConfiguration.getEventHandlers(), rootProgressDescription, rootProgressUnits);
    for (int i = 0; i < rootProgressUnits; i++) {
      childProgressDispatcherSupplier.add(rootProgressDispatcher.newChildProducer());
    }
    rootProgressCloser = () -> rootProgressDispatcher.close();
  }

  private void retrieveTargetRegistryCredentials() {
    results.targetRegistryCredentials =
        listeningExecutorService.submit(
            RetrieveRegistryCredentialsStep.forTargetImage(
                buildConfiguration, childProgressDispatcherSupplier.remove()));
  }

  private void authenticatePush() {
    results.pushAuthorization =
        listeningExecutorService.submit(
            () ->
                new AuthenticatePushStep(
                        buildConfiguration,
                        childProgressDispatcherSupplier.remove(),
                        Preconditions.checkNotNull(results.targetRegistryCredentials).get())
                    .call());
  }

  private void pullBaseImage() {
    results.baseImageAndAuth =
        listeningExecutorService.submit(
            new PullBaseImageStep(buildConfiguration, childProgressDispatcherSupplier.remove()));
  }

  private void pullAndCacheBaseImageLayers() {
    results.baseImageLayers =
        listeningExecutorService.submit(
            () ->
                scheduleCallables(
                    PullAndCacheBaseImageLayersStep.makeList(
                        buildConfiguration,
                        childProgressDispatcherSupplier.remove(),
                        results.baseImageAndAuth.get())));
  }

  private void pushBaseImageLayers() {
    results.baseImageLayerPushResults =
        listeningExecutorService.submit(
            () ->
                scheduleCallables(
                    PushLayerStep.makeList(
                        buildConfiguration,
                        childProgressDispatcherSupplier.remove(),
                        Preconditions.checkNotNull(results.pushAuthorization).get(),
                        Preconditions.checkNotNull(results.baseImageLayers).get())));
  }

  private void buildAndCacheApplicationLayers() {
    results.applicationLayers =
        scheduleCallables(
            BuildAndCacheApplicationLayerStep.makeList(
                buildConfiguration, childProgressDispatcherSupplier.remove()));
  }

  private void buildImage() {
    results.builtImage =
        listeningExecutorService.submit(
            () ->
                new BuildImageStep(
                        buildConfiguration,
                        childProgressDispatcherSupplier.remove(),
                        Preconditions.checkNotNull(results.baseImageAndAuth).get().getImage(),
                        realizeFutures(Preconditions.checkNotNull(results.baseImageLayers).get()),
                        realizeFutures(Preconditions.checkNotNull(results.applicationLayers)))
                    .call());
  }

  private void pushContainerConfiguration() {
    results.containerConfigurationPushResult =
        listeningExecutorService.submit(
            () ->
                new PushContainerConfigurationStep(
                        buildConfiguration,
                        childProgressDispatcherSupplier.remove(),
                        Preconditions.checkNotNull(results.pushAuthorization).get(),
                        Preconditions.checkNotNull(results.builtImage).get())
                    .call());
  }

  private void pushApplicationLayers() {
    results.applicationLayerPushResults =
        listeningExecutorService.submit(
            () ->
                scheduleCallables(
                    PushLayerStep.makeList(
                        buildConfiguration,
                        childProgressDispatcherSupplier.remove(),
                        Preconditions.checkNotNull(results.pushAuthorization).get(),
                        Preconditions.checkNotNull(results.applicationLayers))));
  }

  private BuildResult pushImage() throws InterruptedException, ExecutionException, IOException {
    realizeFutures(Preconditions.checkNotNull(results.baseImageLayerPushResults).get());
    realizeFutures(Preconditions.checkNotNull(results.applicationLayerPushResults).get());

    return new PushImageStep(
            listeningExecutorService,
            buildConfiguration,
            childProgressDispatcherSupplier.remove(),
            Preconditions.checkNotNull(results.pushAuthorization).get(),
            Preconditions.checkNotNull(results.containerConfigurationPushResult).get(),
            Preconditions.checkNotNull(results.builtImage).get())
        .call();
  }

  private BuildResult loadDocker(DockerClient dockerClient)
      throws InterruptedException, ExecutionException, IOException {
    realizeFutures(Preconditions.checkNotNull(results.baseImageLayers).get());
    realizeFutures(Preconditions.checkNotNull(results.applicationLayers));

    return new LoadDockerStep(
            buildConfiguration,
            childProgressDispatcherSupplier.remove(),
            dockerClient,
            Preconditions.checkNotNull(results.builtImage.get()))
        .call();
  }

  private BuildResult writeTarFile(Path outputPath)
      throws InterruptedException, ExecutionException, IOException {
    realizeFutures(Preconditions.checkNotNull(results.baseImageLayers).get());
    realizeFutures(Preconditions.checkNotNull(results.applicationLayers));

    return new WriteTarFileStep(
            buildConfiguration,
            childProgressDispatcherSupplier.remove(),
            outputPath,
            Preconditions.checkNotNull(results.builtImage).get())
        .call();
  }

  public BuildResult run(BuildConfiguration buildConfiguration)
      throws ExecutionException, InterruptedException {
    Preconditions.checkState(childProgressDispatcherSupplier.isEmpty());

    try {
      return Preconditions.checkNotNull(results.buildResult).get();
    } finally {
      rootProgressCloser.run();
    }
  }

  private void buildAndCache() {
    pullBaseImage();
    pullAndCacheBaseImageLayers();
    buildAndCacheApplicationLayers();
    buildImage();
  }

  public StepsRunner forDockerBuild(DockerClient dockerClient) {
    buildAndCache();
    loadDocker(dockerClient);
    return this;
  }

  public StepsRunner buildToTar(Path outputPath) {
    //    rootProgressDescription = "building image to Docker daemon";
    //    rootProgressDescription = "building image to tar file";
    //    rootProgressDescription = "building image to registry";

    buildAndCache();
    writeTarFile(outputPath);
    return this;
  }

  public StepsRunner buildToRegistry(BuildConfiguration buildConfiguration) {
    buildAndCache();
    retrieveTargetRegistryCredentials();
    authenticatePush();
    pushBaseImageLayers();
    pushContainerConfiguration();
    pushApplicationLayers();
    pushImage();
    return this;
  }

  private <E> List<Future<E>> scheduleCallables(ImmutableList<? extends Callable<E>> tasks) {
    List<Future<E>> futures = new ArrayList<>();
    for (Callable<E> task : tasks) {
      futures.add(listeningExecutorService.submit(task));
    }
    return futures;
  }
}
