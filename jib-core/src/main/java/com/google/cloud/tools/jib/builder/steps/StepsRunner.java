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
import com.google.cloud.tools.jib.builder.steps.LocalBaseImageSteps.LocalImage;
import com.google.cloud.tools.jib.builder.steps.PullBaseImageStep.ImagesAndRegistryClient;
import com.google.cloud.tools.jib.configuration.BuildContext;
import com.google.cloud.tools.jib.configuration.ImageConfiguration;
import com.google.cloud.tools.jib.docker.DockerClient;
import com.google.cloud.tools.jib.filesystem.TempDirectoryProvider;
import com.google.cloud.tools.jib.global.JibSystemProperties;
import com.google.cloud.tools.jib.image.Image;
import com.google.cloud.tools.jib.image.json.ManifestTemplate;
import com.google.cloud.tools.jib.registry.ManifestAndDigest;
import com.google.cloud.tools.jib.registry.RegistryClient;
import com.google.common.base.Preconditions;
import com.google.common.base.Verify;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    private Future<ImagesAndRegistryClient> baseImagesAndRegistryClient = failedFuture();
    private Future<Map<Image, List<Future<PreparedLayer>>>> baseImagesAndLayers = failedFuture();
    @Nullable private List<Future<PreparedLayer>> applicationLayers;
    private Future<List<Future<Image>>> builtImages = failedFuture();
    private Future<RegistryClient> targetRegistryClient = failedFuture();
    private Future<List<List<Future<BlobDescriptor>>>> baseImageLayerPushResults = failedFuture();
    private Future<List<Future<BlobDescriptor>>> applicationLayerPushResults = failedFuture();
    private Future<Map<Future<Image>, Future<BlobDescriptor>>>
        builtImagesAndContainerConfigurationPushResults = failedFuture();
    private Future<List<Future<BuildResult>>> buildResults = failedFuture();
    private Future<Optional<ManifestAndDigest<ManifestTemplate>>> manifestCheckResult =
        failedFuture();
  }

  /**
   * Starts building the steps to run.
   *
   * @param buildContext the {@link BuildContext}
   * @return a new {@link StepsRunner}
   */
  public static StepsRunner begin(BuildContext buildContext) {
    ExecutorService executorService =
        JibSystemProperties.serializeExecution()
            ? MoreExecutors.newDirectExecutorService()
            : buildContext.getExecutorService();

    return new StepsRunner(MoreExecutors.listeningDecorator(executorService), buildContext);
  }

  private static <E> List<E> realizeFutures(List<Future<E>> futures)
      throws InterruptedException, ExecutionException {
    List<E> values = new ArrayList<>();
    for (Future<E> future : futures) {
      values.add(future.get());
    }
    return values;
  }

  private final StepResults results = new StepResults();

  private final ExecutorService executorService;
  private final BuildContext buildContext;
  private final TempDirectoryProvider tempDirectoryProvider = new TempDirectoryProvider();

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

  private StepsRunner(ListeningExecutorService executorService, BuildContext buildContext) {
    this.executorService = executorService;
    this.buildContext = buildContext;
  }

  /**
   * Add steps for loading an image to docker daemon.
   *
   * @param dockerClient the docker client to load the image to
   * @return this StepsRunner instance
   */
  public StepsRunner dockerLoadSteps(DockerClient dockerClient) {
    rootProgressDescription = "building image to Docker daemon";

    addRetrievalSteps(true); // always pull layers for docker builds
    stepsToRun.add(this::buildAndCacheApplicationLayers);
    stepsToRun.add(this::buildImages);

    // load to Docker
    stepsToRun.add(() -> loadDocker(dockerClient));
    return this;
  }

  /**
   * Add steps for writing an image as a tar file archive.
   *
   * @param outputPath the target file path to write the image to
   * @return this StepsRunner instance
   */
  public StepsRunner tarBuildSteps(Path outputPath) {
    rootProgressDescription = "building image to tar file";

    addRetrievalSteps(true); // always pull layers for tar builds
    stepsToRun.add(this::buildAndCacheApplicationLayers);
    stepsToRun.add(this::buildImages);

    // create a tar
    stepsToRun.add(() -> writeTarFile(outputPath));
    return this;
  }

  /**
   * Add steps for pushing an image to a remote registry. The registry is determined by the image
   * name.
   *
   * @return this StepsRunner instance.
   */
  public StepsRunner registryPushSteps() {
    rootProgressDescription = "building image to registry";
    boolean layersRequiredLocally = buildContext.getAlwaysCacheBaseImage();

    stepsToRun.add(this::authenticateBearerPush);

    addRetrievalSteps(layersRequiredLocally);
    stepsToRun.add(this::buildAndCacheApplicationLayers);
    stepsToRun.add(this::buildImages);

    // push to registry
    stepsToRun.add(this::pushBaseImageLayers);
    stepsToRun.add(this::pushApplicationLayers);
    stepsToRun.add(this::pushContainerConfiguration);
    stepsToRun.add(this::checkImageInTargetRegistry);
    stepsToRun.add(this::pushImages);
    return this;
  }

  /**
   * Run all steps and return a BuildResult after a build is completed.
   *
   * @return a {@link BuildResult} with build metadata
   * @throws ExecutionException if an error occured during asynchronous execution of steps
   * @throws InterruptedException if the build was interrupted while waiting for results
   */
  public BuildResult run() throws ExecutionException, InterruptedException {
    Preconditions.checkNotNull(rootProgressDescription);

    try (ProgressEventDispatcher progressEventDispatcher =
        ProgressEventDispatcher.newRoot(
            buildContext.getEventHandlers(), rootProgressDescription, stepsToRun.size())) {
      rootProgressDispatcher = progressEventDispatcher;

      stepsToRun.forEach(Runnable::run);
      return results.buildResults.get().get(0).get();

    } catch (ExecutionException ex) {
      ExecutionException unrolled = ex;
      while (unrolled.getCause() instanceof ExecutionException) {
        unrolled = (ExecutionException) unrolled.getCause();
      }
      throw unrolled;

    } finally {
      tempDirectoryProvider.close();
    }
  }

  private void addRetrievalSteps(boolean layersRequiredLocally) {
    ImageConfiguration baseImageConfiguration = buildContext.getBaseImageConfiguration();

    if (baseImageConfiguration.getTarPath().isPresent()) {
      // If tarPath is present, a TarImage was used
      stepsToRun.add(this::extractTar);

    } else if (baseImageConfiguration.getDockerClient().isPresent()) {
      // If dockerClient is present, a DockerDaemonImage was used
      stepsToRun.add(this::saveDocker);

    } else {
      // Otherwise default to RegistryImage
      stepsToRun.add(this::pullBaseImages);
      stepsToRun.add(() -> obtainBaseImageLayers(layersRequiredLocally));
    }
  }

  private void authenticateBearerPush() {
    ProgressEventDispatcher.Factory childProgressDispatcherFactory =
        Verify.verifyNotNull(rootProgressDispatcher).newChildProducer();

    results.targetRegistryClient =
        executorService.submit(
            () -> new AuthenticatePushStep(buildContext, childProgressDispatcherFactory).call());
  }

  private void saveDocker() {
    Optional<DockerClient> dockerClient =
        buildContext.getBaseImageConfiguration().getDockerClient();
    Preconditions.checkArgument(dockerClient.isPresent());
    ProgressEventDispatcher.Factory childProgressDispatcherFactory =
        Verify.verifyNotNull(rootProgressDispatcher).newChildProducer();
    assignLocalImageResult(
        executorService.submit(
            LocalBaseImageSteps.retrieveDockerDaemonLayersStep(
                buildContext,
                childProgressDispatcherFactory,
                dockerClient.get(),
                tempDirectoryProvider)));
  }

  private void extractTar() {
    Optional<Path> tarPath = buildContext.getBaseImageConfiguration().getTarPath();
    Preconditions.checkArgument(tarPath.isPresent());
    ProgressEventDispatcher.Factory childProgressDispatcherFactory =
        Verify.verifyNotNull(rootProgressDispatcher).newChildProducer();
    assignLocalImageResult(
        executorService.submit(
            LocalBaseImageSteps.retrieveTarLayersStep(
                buildContext,
                childProgressDispatcherFactory,
                tarPath.get(),
                tempDirectoryProvider)));
  }

  private void assignLocalImageResult(Future<LocalImage> localImage) {
    results.baseImagesAndRegistryClient =
        executorService.submit(
            () ->
                LocalBaseImageSteps.returnImageAndRegistryClientStep(
                        realizeFutures(localImage.get().layers),
                        localImage.get().configurationTemplate)
                    .call());

    results.baseImagesAndLayers =
        executorService.submit(
            () ->
                Collections.singletonMap(
                    results.baseImagesAndRegistryClient.get().images.get(0),
                    localImage.get().layers));
  }

  private void pullBaseImages() {
    ProgressEventDispatcher.Factory childProgressDispatcherFactory =
        Verify.verifyNotNull(rootProgressDispatcher).newChildProducer();

    results.baseImagesAndRegistryClient =
        executorService.submit(new PullBaseImageStep(buildContext, childProgressDispatcherFactory));
  }

  private void obtainBaseImageLayers(boolean layersRequiredLocally) {
    ProgressEventDispatcher.Factory childProgressDispatcherFactory =
        Verify.verifyNotNull(rootProgressDispatcher).newChildProducer();
    results.baseImagesAndLayers =
        executorService.submit(
            () -> {
              Map<Image, List<Future<PreparedLayer>>> baseImagesAndLayers = new HashMap<>();
              for (Image image : results.baseImagesAndRegistryClient.get().images) {
                List<Future<PreparedLayer>> layers =
                    scheduleCallables(
                        layersRequiredLocally
                            ? ObtainBaseImageLayerStep.makeListForForcedDownload(
                                buildContext,
                                childProgressDispatcherFactory,
                                image,
                                results.baseImagesAndRegistryClient.get().registryClient)
                            : ObtainBaseImageLayerStep.makeListForSelectiveDownload(
                                buildContext,
                                childProgressDispatcherFactory,
                                image,
                                results.baseImagesAndRegistryClient.get().registryClient,
                                results.targetRegistryClient.get()));
                baseImagesAndLayers.put(image, layers);
              }
              return baseImagesAndLayers;
            });
  }

  private void pushBaseImageLayers() {
    ProgressEventDispatcher.Factory childProgressDispatcherFactory =
        Verify.verifyNotNull(rootProgressDispatcher).newChildProducer();

    results.baseImageLayerPushResults =
        executorService.submit(
            () -> {
              List<List<Future<BlobDescriptor>>> pushResults = new ArrayList<>();
              for (List<Future<PreparedLayer>> baseImageLayers :
                  results.baseImagesAndLayers.get().values()) {
                List<Future<BlobDescriptor>> baseImageLayerPushResult =
                    scheduleCallables(
                        PushLayerStep.makeList(
                            buildContext,
                            childProgressDispatcherFactory,
                            results.targetRegistryClient.get(),
                            Verify.verifyNotNull(baseImageLayers)));
                pushResults.add(baseImageLayerPushResult);
              }
              return pushResults;
            });
  }

  private void buildAndCacheApplicationLayers() {
    ProgressEventDispatcher.Factory childProgressDispatcherFactory =
        Verify.verifyNotNull(rootProgressDispatcher).newChildProducer();

    results.applicationLayers =
        scheduleCallables(
            BuildAndCacheApplicationLayerStep.makeList(
                buildContext, childProgressDispatcherFactory));
  }

  private void buildImages() {
    ProgressEventDispatcher.Factory childProgressDispatcherFactory =
        Verify.verifyNotNull(rootProgressDispatcher).newChildProducer();
    results.builtImages =
        executorService.submit(
            () -> {
              List<Future<Image>> builtImages = new ArrayList<>();
              for (Map.Entry<Image, List<Future<PreparedLayer>>> entry :
                  results.baseImagesAndLayers.get().entrySet()) {
                Future<Image> builtImage =
                    executorService.submit(
                        () ->
                            new BuildImageStep(
                                    buildContext,
                                    childProgressDispatcherFactory,
                                    entry.getKey(),
                                    realizeFutures(Verify.verifyNotNull(entry.getValue())),
                                    realizeFutures(Verify.verifyNotNull(results.applicationLayers)))
                                .call());
                builtImages.add(builtImage);
              }
              return builtImages;
            });
  }

  private void pushContainerConfiguration() {
    ProgressEventDispatcher.Factory childProgressDispatcherFactory =
        Verify.verifyNotNull(rootProgressDispatcher).newChildProducer();

    results.builtImagesAndContainerConfigurationPushResults =
        executorService.submit(
            () -> {
              Map<Future<Image>, Future<BlobDescriptor>> pushResults = new HashMap<>();
              for (Future<Image> builtImage : results.builtImages.get()) {
                Future<BlobDescriptor> configPushResult =
                    executorService.submit(
                        () ->
                            new PushContainerConfigurationStep(
                                    buildContext,
                                    childProgressDispatcherFactory,
                                    results.targetRegistryClient.get(),
                                    builtImage.get())
                                .call());
                pushResults.put(builtImage, configPushResult);
              }
              return pushResults;
            });
  }

  private void pushApplicationLayers() {
    ProgressEventDispatcher.Factory childProgressDispatcherFactory =
        Verify.verifyNotNull(rootProgressDispatcher).newChildProducer();

    results.applicationLayerPushResults =
        executorService.submit(
            () ->
                scheduleCallables(
                    PushLayerStep.makeList(
                        buildContext,
                        childProgressDispatcherFactory,
                        results.targetRegistryClient.get(),
                        Verify.verifyNotNull(results.applicationLayers))));
  }

  private void checkImageInTargetRegistry() {
    ProgressEventDispatcher.Factory childProgressDispatcherFactory =
        Verify.verifyNotNull(rootProgressDispatcher).newChildProducer();

    results.manifestCheckResult =
        executorService.submit(
            () ->
                new CheckImageStep(
                        buildContext,
                        childProgressDispatcherFactory,
                        results.targetRegistryClient.get(),
                        Verify.verifyNotNull(
                                results
                                    .builtImagesAndContainerConfigurationPushResults
                                    .get()
                                    .get(results.builtImages.get().get(0)))
                            .get(),
                        results.builtImages.get().get(0).get())
                    .call());
  }

  private void pushImages() {
    ProgressEventDispatcher.Factory childProgressDispatcherFactory =
        Verify.verifyNotNull(rootProgressDispatcher).newChildProducer();

    results.buildResults =
        executorService.submit(
            () -> {
              realizeFutures(results.applicationLayerPushResults.get());

              List<Future<BuildResult>> buildResults = new ArrayList<>();

              for (Map.Entry<Future<Image>, Future<BlobDescriptor>> entry :
                  results.builtImagesAndContainerConfigurationPushResults.get().entrySet()) {

                Future<BuildResult> buildResult =
                    executorService.submit(
                        () -> {
                          realizeFutures(results.baseImageLayerPushResults.get().get(0));

                          List<Future<BuildResult>> manifestPushResults =
                              scheduleCallables(
                                  PushImageStep.makeList(
                                      buildContext,
                                      childProgressDispatcherFactory,
                                      results.targetRegistryClient.get(),
                                      Verify.verifyNotNull(entry.getValue()).get(),
                                      entry.getKey().get(),
                                      results.manifestCheckResult.get().isPresent()));

                          realizeFutures(manifestPushResults);
                          return manifestPushResults.isEmpty()
                              ? new BuildResult(
                                  results.manifestCheckResult.get().get().getDigest(),
                                  Verify.verifyNotNull(entry.getValue()).get().getDigest())
                              // Manifest pushers return the same BuildResult.
                              : manifestPushResults.get(0).get();
                        });

                buildResults.add(buildResult);
              }
              return buildResults;
            });

    //    results.buildResult = executorService.submit(() ->
    // results.buildResults.get().get(0).get());
  }

  private void loadDocker(DockerClient dockerClient) {
    ProgressEventDispatcher.Factory childProgressDispatcherFactory =
        Verify.verifyNotNull(rootProgressDispatcher).newChildProducer();

    results.buildResults =
        executorService.submit(
            () -> {
              List<Future<BuildResult>> buildResults = new ArrayList<>();
              Future<BuildResult> buildResult =
                  executorService.submit(
                      () ->
                          new LoadDockerStep(
                                  buildContext,
                                  childProgressDispatcherFactory,
                                  dockerClient,
                                  results.builtImages.get().get(0).get())
                              .call());
              buildResults.add(buildResult);
              return buildResults;
            });
  }

  private void writeTarFile(Path outputPath) {
    ProgressEventDispatcher.Factory childProgressDispatcherFactory =
        Verify.verifyNotNull(rootProgressDispatcher).newChildProducer();

    results.buildResults =
        executorService.submit(
            () -> {
              List<Future<BuildResult>> buildResults = new ArrayList<>();
              Future<BuildResult> buildResult =
                  executorService.submit(
                      () ->
                          new WriteTarFileStep(
                                  buildContext,
                                  childProgressDispatcherFactory,
                                  outputPath,
                                  results.builtImages.get().get(0).get())
                              .call());
              buildResults.add(buildResult);
              return buildResults;
            });
  }

  private <E> List<Future<E>> scheduleCallables(ImmutableList<? extends Callable<E>> callables) {
    return callables.stream().map(executorService::submit).collect(Collectors.toList());
  }
}
