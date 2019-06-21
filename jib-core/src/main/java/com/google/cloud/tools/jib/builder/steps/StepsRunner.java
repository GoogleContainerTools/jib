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

import com.google.cloud.tools.jib.api.Credential;
import com.google.cloud.tools.jib.blob.BlobDescriptor;
import com.google.cloud.tools.jib.builder.ProgressEventDispatcher;
import com.google.cloud.tools.jib.builder.steps.PullBaseImageStep.ImageAndAuthorization;
import com.google.cloud.tools.jib.configuration.BuildConfiguration;
import com.google.cloud.tools.jib.docker.DockerClient;
import com.google.cloud.tools.jib.global.JibSystemProperties;
import com.google.cloud.tools.jib.http.Authorization;
import com.google.cloud.tools.jib.image.Image;
import com.google.common.base.Preconditions;
import com.google.common.base.Verify;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/**
 * Runs steps for building an image.
 *
 * <p>Use by first calling {@link #begin} and then calling the individual step running methods. Note
 * that order matters, so make sure that steps are run before other steps that depend on them. Wait
 * on the last step by calling the respective {@code wait...} methods.
 */
public class StepsRunner {

  /** Holds the individual step results. */
  private static class StepResults {

    private static <E> Future<E> failedFuture() {
      return Futures.immediateFailedFuture(
          new IllegalStateException("invalid usage; required step not configured"));
    }

    private Future<ImageAndAuthorization> baseImageAndAuth = failedFuture();
    private Future<List<Future<CachedLayerAndName>>> baseImageLayers = failedFuture();
    @Nullable private List<Future<CachedLayerAndName>> applicationLayers;
    private Future<Image> builtImage = failedFuture();
    private Future<Optional<Credential>> targetRegistryCredentials = failedFuture();
    private Future<Optional<Authorization>> pushAuthorization = failedFuture();
    private Future<List<Future<BlobDescriptor>>> baseImageLayerPushResults = failedFuture();
    private Future<List<Future<BlobDescriptor>>> applicationLayerPushResults = failedFuture();
    private Future<BlobDescriptor> containerConfigurationPushResult = failedFuture();
    private Future<BuildResult> buildResult = failedFuture();
  }

  /**
   * Starts building the steps to run.
   *
   * @param buildConfiguration the {@link BuildConfiguration}
   * @return a new {@link StepsRunner}
   */
  public static StepsRunner begin(BuildConfiguration buildConfiguration) {
    ExecutorService executorService =
        JibSystemProperties.isSerializedExecutionEnabled()
            ? MoreExecutors.newDirectExecutorService()
            : buildConfiguration.getExecutorService();

    return new StepsRunner(MoreExecutors.listeningDecorator(executorService), buildConfiguration);
  }

  private final StepResults results = new StepResults();

  // TODO: use plain ExecutorService; requires refactoring PushImageStep.
  private final ListeningExecutorService executorService;
  private final BuildConfiguration buildConfiguration;

  // We save steps to run by wrapping each step into a Runnable, only because of the unfortunate
  // chicken-and-egg situation arising from using ProgressEventDispatcher. The current
  // ProgressEventDispatcher model requires knowing in advance how many units of work (i.e., steps)
  // we should perform. That is, to instantiate a root ProgressEventDispatcher instance, we should
  // know ahead how many steps we will run. However, to instantiate a step, we need a root progress
  // dispatcher. So, we wrap steps into Runnables and save them to run them later. Then we can count
  // the number of Runnables and, create a root dispatcher, and run the saved Runnables.
  private final List<Runnable> stepsToRun = new ArrayList<>();

  @Nullable private String rootProgressDescription;
  @Nullable private ProgressEventDispatcher rootProgressDispatcher;

  private StepsRunner(
      ListeningExecutorService executorService, BuildConfiguration buildConfiguration) {
    this.executorService = executorService;
    this.buildConfiguration = buildConfiguration;
  }

  private void retrieveTargetRegistryCredentials() {
    ProgressEventDispatcher.Factory childProgressDispatcherFactory =
        Verify.verifyNotNull(rootProgressDispatcher).newChildProducer();

    results.targetRegistryCredentials =
        executorService.submit(
            RetrieveRegistryCredentialsStep.forTargetImage(
                buildConfiguration, childProgressDispatcherFactory));
  }

  private void authenticatePush() {
    ProgressEventDispatcher.Factory childProgressDispatcherFactory =
        Verify.verifyNotNull(rootProgressDispatcher).newChildProducer();

    results.pushAuthorization =
        executorService.submit(
            () ->
                new AuthenticatePushStep(
                        buildConfiguration,
                        childProgressDispatcherFactory,
                        results.targetRegistryCredentials.get().orElse(null))
                    .call());
  }

  private void pullBaseImage() {
    ProgressEventDispatcher.Factory childProgressDispatcherFactory =
        Verify.verifyNotNull(rootProgressDispatcher).newChildProducer();

    results.baseImageAndAuth =
        executorService.submit(
            new PullBaseImageStep(buildConfiguration, childProgressDispatcherFactory));
  }

  private void pullAndCacheBaseImageLayers() {
    ProgressEventDispatcher.Factory childProgressDispatcherFactory =
        Verify.verifyNotNull(rootProgressDispatcher).newChildProducer();

    results.baseImageLayers =
        executorService.submit(
            () ->
                scheduleCallables(
                    PullAndCacheBaseImageLayersStep.makeList(
                        buildConfiguration,
                        childProgressDispatcherFactory,
                        results.baseImageAndAuth.get())));
  }

  private void pushBaseImageLayers() {
    ProgressEventDispatcher.Factory childProgressDispatcherFactory =
        Verify.verifyNotNull(rootProgressDispatcher).newChildProducer();

    results.baseImageLayerPushResults =
        executorService.submit(
            () ->
                scheduleCallables(
                    PushLayerStep.makeList(
                        buildConfiguration,
                        childProgressDispatcherFactory,
                        results.pushAuthorization.get().orElse(null),
                        results.baseImageLayers.get())));
  }

  private void buildAndCacheApplicationLayers() {
    ProgressEventDispatcher.Factory childProgressDispatcherFactory =
        Verify.verifyNotNull(rootProgressDispatcher).newChildProducer();

    results.applicationLayers =
        scheduleCallables(
            BuildAndCacheApplicationLayerStep.makeList(
                buildConfiguration, childProgressDispatcherFactory));
  }

  private void buildImage() {
    ProgressEventDispatcher.Factory childProgressDispatcherFactory =
        Verify.verifyNotNull(rootProgressDispatcher).newChildProducer();

    results.builtImage =
        executorService.submit(
            () ->
                new BuildImageStep(
                        buildConfiguration,
                        childProgressDispatcherFactory,
                        results.baseImageAndAuth.get().getImage(),
                        realizeFutures(results.baseImageLayers.get()),
                        realizeFutures(Verify.verifyNotNull(results.applicationLayers)))
                    .call());
  }

  private void pushContainerConfiguration() {
    ProgressEventDispatcher.Factory childProgressDispatcherFactory =
        Verify.verifyNotNull(rootProgressDispatcher).newChildProducer();

    results.containerConfigurationPushResult =
        executorService.submit(
            () ->
                new PushContainerConfigurationStep(
                        buildConfiguration,
                        childProgressDispatcherFactory,
                        results.pushAuthorization.get().orElse(null),
                        results.builtImage.get())
                    .call());
  }

  private void pushApplicationLayers() {
    ProgressEventDispatcher.Factory childProgressDispatcherFactory =
        Verify.verifyNotNull(rootProgressDispatcher).newChildProducer();

    results.applicationLayerPushResults =
        executorService.submit(
            () ->
                scheduleCallables(
                    PushLayerStep.makeList(
                        buildConfiguration,
                        childProgressDispatcherFactory,
                        results.pushAuthorization.get().orElse(null),
                        Verify.verifyNotNull(results.applicationLayers))));
  }

  private void pushImage() {
    ProgressEventDispatcher.Factory childProgressDispatcherFactory =
        Verify.verifyNotNull(rootProgressDispatcher).newChildProducer();

    results.buildResult =
        executorService.submit(
            () -> {
              realizeFutures(results.baseImageLayerPushResults.get());
              realizeFutures(results.applicationLayerPushResults.get());

              return new PushImageStep(
                      executorService,
                      buildConfiguration,
                      childProgressDispatcherFactory,
                      results.pushAuthorization.get().orElse(null),
                      results.containerConfigurationPushResult.get(),
                      results.builtImage.get())
                  .call();
            });
  }

  private void loadDocker(DockerClient dockerClient) {
    ProgressEventDispatcher.Factory childProgressDispatcherFactory =
        Verify.verifyNotNull(rootProgressDispatcher).newChildProducer();

    results.buildResult =
        executorService.submit(
            () ->
                new LoadDockerStep(
                        buildConfiguration,
                        childProgressDispatcherFactory,
                        dockerClient,
                        results.builtImage.get())
                    .call());
  }

  private void writeTarFile(Path outputPath) {
    ProgressEventDispatcher.Factory childProgressDispatcherFactory =
        Verify.verifyNotNull(rootProgressDispatcher).newChildProducer();

    results.buildResult =
        executorService.submit(
            () ->
                new WriteTarFileStep(
                        buildConfiguration,
                        childProgressDispatcherFactory,
                        outputPath,
                        results.builtImage.get())
                    .call());
  }

  public BuildResult run() throws ExecutionException, InterruptedException {
    Preconditions.checkNotNull(rootProgressDescription);

    try (ProgressEventDispatcher progressEventDispatcher =
        ProgressEventDispatcher.newRoot(
            buildConfiguration.getEventHandlers(), rootProgressDescription, stepsToRun.size())) {
      rootProgressDispatcher = progressEventDispatcher;

      stepsToRun.forEach(Runnable::run);
      return results.buildResult.get();

    } catch (ExecutionException ex) {
      ExecutionException unrolled = ex;
      while (unrolled.getCause() instanceof ExecutionException) {
        unrolled = (ExecutionException) unrolled.getCause();
      }
      throw unrolled;
    }
  }

  private static <E> List<E> realizeFutures(List<Future<E>> futures)
      throws InterruptedException, ExecutionException {
    List<E> values = new ArrayList<>();
    for (Future<E> future : futures) {
      values.add(future.get());
    }
    return values;
  }

  private <E> List<Future<E>> scheduleCallables(ImmutableList<? extends Callable<E>> callables) {
    return callables.stream().map(executorService::submit).collect(Collectors.toList());
  }

  public StepsRunner dockerLoadSteps(DockerClient dockerClient) {
    rootProgressDescription = "building image to Docker daemon";
    // build and cache
    stepsToRun.add(this::pullBaseImage);
    stepsToRun.add(this::pullAndCacheBaseImageLayers);
    stepsToRun.add(this::buildAndCacheApplicationLayers);
    stepsToRun.add(this::buildImage);
    // load to Docker
    stepsToRun.add(() -> loadDocker(dockerClient));
    return this;
  }

  public StepsRunner tarBuildSteps(Path outputPath) {
    rootProgressDescription = "building image to tar file";
    // build and cache
    stepsToRun.add(this::pullBaseImage);
    stepsToRun.add(this::pullAndCacheBaseImageLayers);
    stepsToRun.add(this::buildAndCacheApplicationLayers);
    stepsToRun.add(this::buildImage);
    // create a tar
    stepsToRun.add(() -> writeTarFile(outputPath));
    return this;
  }

  public StepsRunner registryPushSteps() {
    rootProgressDescription = "building image to registry";
    // build and cache
    stepsToRun.add(this::pullBaseImage);
    stepsToRun.add(this::pullAndCacheBaseImageLayers);
    stepsToRun.add(this::buildAndCacheApplicationLayers);
    stepsToRun.add(this::buildImage);
    // push to registry
    stepsToRun.add(this::retrieveTargetRegistryCredentials);
    stepsToRun.add(this::authenticatePush);
    stepsToRun.add(this::pushBaseImageLayers);
    stepsToRun.add(this::pushApplicationLayers);
    stepsToRun.add(this::pushContainerConfiguration);
    stepsToRun.add(this::pushImage);
    return this;
  }
}
