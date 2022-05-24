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

import com.google.cloud.tools.jib.api.DescriptorDigest;
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
import com.google.cloud.tools.jib.image.Layer;
import com.google.cloud.tools.jib.image.json.ManifestTemplate;
import com.google.cloud.tools.jib.registry.ManifestAndDigest;
import com.google.cloud.tools.jib.registry.RegistryClient;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Verify;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Consumer;
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

    @Nullable private List<Future<PreparedLayer>> applicationLayers;
    private Future<ManifestTemplate> manifestListOrSingleManifest = failedFuture();
    private Future<RegistryClient> targetRegistryClient = failedFuture();
    private Future<List<Future<BlobDescriptor>>> applicationLayerPushResults = failedFuture();
    private Future<Optional<ManifestAndDigest<ManifestTemplate>>> manifestCheckResult =
        failedFuture();
    private Future<List<Future<BuildResult>>> imagePushResults = failedFuture();
    private Future<BuildResult> buildResult = failedFuture();

    private Future<ImagesAndRegistryClient> baseImagesAndRegistryClient = failedFuture();
    private Future<Map<Image, List<Future<PreparedLayer>>>> baseImagesAndLayers = failedFuture();
    private Future<Map<Image, List<Future<BlobDescriptor>>>> baseImagesAndLayerPushResults =
        failedFuture();
    private Future<Map<Image, Future<BlobDescriptor>>> baseImagesAndContainerConfigPushResults =
        failedFuture();
    private Future<Map<Image, Future<Image>>> baseImagesAndBuiltImages = failedFuture();
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

  private static <E> List<E> realizeFutures(Collection<Future<E>> futures)
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

  // Instead of directly running each step, we first save them as a lambda. This is only because of
  // the unfortunate chicken-and-egg situation when using ProgressEventDispatcher. The current
  // ProgressEventDispatcher model requires allocating the total units of work (i.e., steps)
  // up front. That is, to instantiate a root ProgressEventDispatcher, we should know ahead how many
  // steps we will run. However, to run a step, we need a root progress dispatcher. So, we take each
  // step as a lambda and save them to run later. Then we can count the number of lambdas, create a
  // root dispatcher with the count, and run the saved lambdas using the dispatcher.
  private final List<Consumer<ProgressEventDispatcher.Factory>> stepsToRun = new ArrayList<>();

  @Nullable private String rootProgressDescription;

  @VisibleForTesting
  StepsRunner(ListeningExecutorService executorService, BuildContext buildContext) {
    this.executorService = executorService;
    this.buildContext = buildContext;
  }

  /**
   * Add steps for loading an image to docker daemon.
   *
   * @param dockerClient the docker client to load the image to
   * @return this
   */
  public StepsRunner dockerLoadSteps(DockerClient dockerClient) {
    rootProgressDescription = "building image to Docker daemon";

    addRetrievalSteps(true); // always pull layers for docker builds
    stepsToRun.add(this::buildAndCacheApplicationLayers);
    stepsToRun.add(this::buildImages);

    // load to Docker
    stepsToRun.add(
        progressDispatcherFactory -> loadDocker(dockerClient, progressDispatcherFactory));
    return this;
  }

  /**
   * Add steps for writing an image as a tar file archive.
   *
   * @param outputPath the target file path to write the image to
   * @return this
   */
  public StepsRunner tarBuildSteps(Path outputPath) {
    rootProgressDescription = "building image to tar file";

    addRetrievalSteps(true); // always pull layers for tar builds
    stepsToRun.add(this::buildAndCacheApplicationLayers);
    stepsToRun.add(this::buildImages);

    // create a tar
    stepsToRun.add(
        progressDispatcherFactory -> writeTarFile(outputPath, progressDispatcherFactory));
    return this;
  }

  /**
   * Add steps for pushing images to a remote registry. The registry is determined by the image
   * name.
   *
   * @return this
   */
  public StepsRunner registryPushSteps() {
    rootProgressDescription = "building images to registry";
    boolean layersRequiredLocally = buildContext.getAlwaysCacheBaseImage();

    stepsToRun.add(this::authenticateBearerPush);

    addRetrievalSteps(layersRequiredLocally);
    stepsToRun.add(this::buildAndCacheApplicationLayers);
    stepsToRun.add(this::buildImages);
    stepsToRun.add(this::buildManifestListOrSingleManifest);

    // push to registry
    stepsToRun.add(this::pushBaseImagesLayers);
    stepsToRun.add(this::pushApplicationLayers);
    stepsToRun.add(this::pushContainerConfigurations);
    stepsToRun.add(this::checkManifestInTargetRegistry);
    stepsToRun.add(this::pushImages);
    stepsToRun.add(this::pushManifestList);
    return this;
  }

  /**
   * Run all steps and return a BuildResult after a build is completed.
   *
   * @return a {@link BuildResult} with build metadata
   * @throws ExecutionException if an error occurred during asynchronous execution of steps
   * @throws InterruptedException if the build was interrupted while waiting for results
   */
  public BuildResult run() throws ExecutionException, InterruptedException {
    Preconditions.checkNotNull(rootProgressDescription);

    try (ProgressEventDispatcher progressEventDispatcher =
        ProgressEventDispatcher.newRoot(
            buildContext.getEventHandlers(), rootProgressDescription, stepsToRun.size())) {
      stepsToRun.forEach(step -> step.accept(progressEventDispatcher.newChildProducer()));
      return results.buildResult.get();

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
      stepsToRun.add(
          progressDispatcherFactory ->
              obtainBaseImagesLayers(layersRequiredLocally, progressDispatcherFactory));
    }
  }

  private void authenticateBearerPush(ProgressEventDispatcher.Factory progressDispatcherFactory) {
    results.targetRegistryClient =
        executorService.submit(new AuthenticatePushStep(buildContext, progressDispatcherFactory));
  }

  private void saveDocker(ProgressEventDispatcher.Factory progressDispatcherFactory) {
    Optional<DockerClient> dockerClient =
        buildContext.getBaseImageConfiguration().getDockerClient();
    Preconditions.checkArgument(dockerClient.isPresent());

    assignLocalImageResult(
        executorService.submit(
            LocalBaseImageSteps.retrieveDockerDaemonLayersStep(
                buildContext,
                progressDispatcherFactory,
                dockerClient.get(),
                tempDirectoryProvider)));
  }

  private void extractTar(ProgressEventDispatcher.Factory progressDispatcherFactory) {
    Optional<Path> tarPath = buildContext.getBaseImageConfiguration().getTarPath();
    Preconditions.checkArgument(tarPath.isPresent());

    assignLocalImageResult(
        executorService.submit(
            LocalBaseImageSteps.retrieveTarLayersStep(
                buildContext, progressDispatcherFactory, tarPath.get(), tempDirectoryProvider)));
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

  @VisibleForTesting
  void pullBaseImages(ProgressEventDispatcher.Factory progressDispatcherFactory) {
    results.baseImagesAndRegistryClient =
        executorService.submit(new PullBaseImageStep(buildContext, progressDispatcherFactory));
  }

  private void obtainBaseImagesLayers(
      boolean layersRequiredLocally, ProgressEventDispatcher.Factory progressDispatcherFactory) {
    results.baseImagesAndLayers =
        executorService.submit(
            () -> {
              try (ProgressEventDispatcher progressDispatcher =
                  progressDispatcherFactory.create(
                      "scheduling obtaining base images layers",
                      results.baseImagesAndRegistryClient.get().images.size())) {

                Map<DescriptorDigest, Future<PreparedLayer>> preparedLayersCache = new HashMap<>();
                Map<Image, List<Future<PreparedLayer>>> baseImagesAndLayers = new LinkedHashMap<>();
                for (Image baseImage : results.baseImagesAndRegistryClient.get().images) {
                  List<Future<PreparedLayer>> layers =
                      obtainBaseImageLayers(
                          baseImage,
                          layersRequiredLocally,
                          preparedLayersCache,
                          progressDispatcher.newChildProducer());
                  baseImagesAndLayers.put(baseImage, layers);
                }
                return baseImagesAndLayers;
              }
            });
  }

  // This method updates the given "preparedLayersCache" and should not be called concurrently.
  @VisibleForTesting
  List<Future<PreparedLayer>> obtainBaseImageLayers(
      Image baseImage,
      boolean layersRequiredLocally,
      Map<DescriptorDigest, Future<PreparedLayer>> preparedLayersCache,
      ProgressEventDispatcher.Factory progressDispatcherFactory)
      throws InterruptedException, ExecutionException {
    List<Future<PreparedLayer>> preparedLayers = new ArrayList<>();

    try (ProgressEventDispatcher progressDispatcher =
        progressDispatcherFactory.create(
            "launching base image layer pullers", baseImage.getLayers().size())) {
      for (Layer layer : baseImage.getLayers()) {
        DescriptorDigest digest = layer.getBlobDescriptor().getDigest();
        Future<PreparedLayer> preparedLayer = preparedLayersCache.get(digest);

        if (preparedLayer != null) {
          progressDispatcher.dispatchProgress(1);
        } else { // If we haven't obtained this layer yet, launcher a puller.
          preparedLayer =
              executorService.submit(
                  layersRequiredLocally
                      ? ObtainBaseImageLayerStep.forForcedDownload(
                          buildContext,
                          progressDispatcher.newChildProducer(),
                          layer,
                          results.baseImagesAndRegistryClient.get().registryClient)
                      : ObtainBaseImageLayerStep.forSelectiveDownload(
                          buildContext,
                          progressDispatcher.newChildProducer(),
                          layer,
                          results.baseImagesAndRegistryClient.get().registryClient,
                          results.targetRegistryClient.get()));
          preparedLayersCache.put(digest, preparedLayer);
        }
        preparedLayers.add(preparedLayer);
      }
      return preparedLayers;
    }
  }

  private void pushBaseImagesLayers(ProgressEventDispatcher.Factory progressDispatcherFactory) {
    results.baseImagesAndLayerPushResults =
        executorService.submit(
            () -> {
              try (ProgressEventDispatcher progressDispatcher =
                  progressDispatcherFactory.create(
                      "scheduling pushing base images layers",
                      results.baseImagesAndLayers.get().size())) {

                Map<Image, List<Future<BlobDescriptor>>> layerPushResults = new LinkedHashMap<>();
                for (Map.Entry<Image, List<Future<PreparedLayer>>> entry :
                    results.baseImagesAndLayers.get().entrySet()) {
                  Image baseImage = entry.getKey();
                  List<Future<PreparedLayer>> baseLayers = entry.getValue();

                  List<Future<BlobDescriptor>> pushResults =
                      pushBaseImageLayers(baseLayers, progressDispatcher.newChildProducer());
                  layerPushResults.put(baseImage, pushResults);
                }
                return layerPushResults;
              }
            });
  }

  private List<Future<BlobDescriptor>> pushBaseImageLayers(
      List<Future<PreparedLayer>> baseLayers,
      ProgressEventDispatcher.Factory progressDispatcherFactory)
      throws InterruptedException, ExecutionException {
    return scheduleCallables(
        PushLayerStep.makeList(
            buildContext,
            progressDispatcherFactory,
            results.targetRegistryClient.get(),
            baseLayers));
  }

  private void buildAndCacheApplicationLayers(
      ProgressEventDispatcher.Factory progressDispatcherFactory) {
    results.applicationLayers =
        scheduleCallables(
            BuildAndCacheApplicationLayerStep.makeList(buildContext, progressDispatcherFactory));
  }

  private void buildImages(ProgressEventDispatcher.Factory progressDispatcherFactory) {
    results.baseImagesAndBuiltImages =
        executorService.submit(
            () -> {
              try (ProgressEventDispatcher progressDispatcher =
                  progressDispatcherFactory.create(
                      "scheduling building manifests", results.baseImagesAndLayers.get().size())) {

                Map<Image, Future<Image>> baseImagesAndBuiltImages = new LinkedHashMap<>();
                for (Map.Entry<Image, List<Future<PreparedLayer>>> entry :
                    results.baseImagesAndLayers.get().entrySet()) {
                  Image baseImage = entry.getKey();
                  List<Future<PreparedLayer>> baseLayers = entry.getValue();

                  Future<Image> builtImage =
                      buildImage(baseImage, baseLayers, progressDispatcher.newChildProducer());
                  baseImagesAndBuiltImages.put(baseImage, builtImage);
                }
                return baseImagesAndBuiltImages;
              }
            });
  }

  private Future<Image> buildImage(
      Image baseImage,
      List<Future<PreparedLayer>> baseLayers,
      ProgressEventDispatcher.Factory progressDispatcherFactory) {
    return executorService.submit(
        () ->
            new BuildImageStep(
                    buildContext,
                    progressDispatcherFactory,
                    baseImage,
                    realizeFutures(baseLayers),
                    realizeFutures(Verify.verifyNotNull(results.applicationLayers)))
                .call());
  }

  private void buildManifestListOrSingleManifest(
      ProgressEventDispatcher.Factory progressDispatcherFactory) {
    results.manifestListOrSingleManifest =
        executorService.submit(
            () ->
                new BuildManifestListOrSingleManifestStep(
                        buildContext,
                        progressDispatcherFactory,
                        realizeFutures(results.baseImagesAndBuiltImages.get().values()))
                    .call());
  }

  private void pushContainerConfigurations(
      ProgressEventDispatcher.Factory progressDispatcherFactory) {
    results.baseImagesAndContainerConfigPushResults =
        executorService.submit(
            () -> {
              try (ProgressEventDispatcher progressDispatcher =
                  progressDispatcherFactory.create(
                      "scheduling pushing container configurations",
                      results.baseImagesAndBuiltImages.get().size())) {

                Map<Image, Future<BlobDescriptor>> configPushResults = new LinkedHashMap<>();
                for (Map.Entry<Image, Future<Image>> entry :
                    results.baseImagesAndBuiltImages.get().entrySet()) {
                  Image baseImage = entry.getKey();
                  Future<Image> builtImage = entry.getValue();

                  Future<BlobDescriptor> pushResult =
                      pushContainerConfiguration(builtImage, progressDispatcher.newChildProducer());
                  configPushResults.put(baseImage, pushResult);
                }
                return configPushResults;
              }
            });
  }

  private Future<BlobDescriptor> pushContainerConfiguration(
      Future<Image> builtImage, ProgressEventDispatcher.Factory progressDispatcherFactory) {
    return executorService.submit(
        () ->
            new PushContainerConfigurationStep(
                    buildContext,
                    progressDispatcherFactory,
                    results.targetRegistryClient.get(),
                    builtImage.get())
                .call());
  }

  private void pushApplicationLayers(ProgressEventDispatcher.Factory progressDispatcherFactory) {
    results.applicationLayerPushResults =
        executorService.submit(
            () ->
                scheduleCallables(
                    PushLayerStep.makeList(
                        buildContext,
                        progressDispatcherFactory,
                        results.targetRegistryClient.get(),
                        Verify.verifyNotNull(results.applicationLayers))));
  }

  private void checkManifestInTargetRegistry(
      ProgressEventDispatcher.Factory progressDispatcherFactory) {
    results.manifestCheckResult =
        executorService.submit(
            () ->
                new CheckManifestStep(
                        buildContext,
                        progressDispatcherFactory,
                        results.targetRegistryClient.get(),
                        results.manifestListOrSingleManifest.get())
                    .call());
  }

  private void pushImages(ProgressEventDispatcher.Factory progressDispatcherFactory) {
    results.imagePushResults =
        executorService.submit(
            () -> {
              try (ProgressEventDispatcher progressDispatcher =
                  progressDispatcherFactory.create(
                      "scheduling pushing manifests",
                      results.baseImagesAndBuiltImages.get().size())) {

                realizeFutures(results.applicationLayerPushResults.get());

                List<Future<BuildResult>> buildResults = new ArrayList<>();
                for (Map.Entry<Image, Future<Image>> entry :
                    results.baseImagesAndBuiltImages.get().entrySet()) {
                  Image baseImage = entry.getKey();
                  Future<Image> builtImage = entry.getValue();

                  buildResults.add(
                      pushImage(baseImage, builtImage, progressDispatcher.newChildProducer()));
                }
                return buildResults;
              }
            });
  }

  private Future<BuildResult> pushImage(
      Image baseImage,
      Future<Image> builtImage,
      ProgressEventDispatcher.Factory progressDispatcherFactory) {
    return executorService.submit(
        () -> {
          realizeFutures(
              Verify.verifyNotNull(results.baseImagesAndLayerPushResults.get().get(baseImage)));

          Future<BlobDescriptor> containerConfigPushResult =
              results.baseImagesAndContainerConfigPushResults.get().get(baseImage);

          List<Future<BuildResult>> manifestPushResults =
              scheduleCallables(
                  PushImageStep.makeList(
                      buildContext,
                      progressDispatcherFactory,
                      results.targetRegistryClient.get(),
                      Verify.verifyNotNull(containerConfigPushResult).get(),
                      builtImage.get(),
                      results.manifestCheckResult.get().isPresent()));

          realizeFutures(manifestPushResults);

          return manifestPushResults.isEmpty()
              ? new BuildResult(
                  results.manifestCheckResult.get().get().getDigest(),
                  Verify.verifyNotNull(containerConfigPushResult).get().getDigest(),
                  determineImagePushed(results.manifestCheckResult.get()))
              // Manifest pushers return the same BuildResult.
              : manifestPushResults.get(0).get();
        });
  }

  @VisibleForTesting
  boolean determineImagePushed(Optional<ManifestAndDigest<ManifestTemplate>> manifestResult) {

    return !(JibSystemProperties.skipExistingImages() && manifestResult.isPresent());
  }

  private void pushManifestList(ProgressEventDispatcher.Factory progressDispatcherFactory) {
    results.buildResult =
        executorService.submit(
            () -> {
              realizeFutures(results.imagePushResults.get());
              List<Future<BuildResult>> manifestListPushResults =
                  scheduleCallables(
                      PushImageStep.makeListForManifestList(
                          buildContext,
                          progressDispatcherFactory,
                          results.targetRegistryClient.get(),
                          results.manifestListOrSingleManifest.get(),
                          results.manifestCheckResult.get().isPresent()));

              realizeFutures(manifestListPushResults);
              return manifestListPushResults.isEmpty()
                  ? results.imagePushResults.get().get(0).get()
                  : manifestListPushResults.get(0).get();
            });
  }

  private void loadDocker(
      DockerClient dockerClient, ProgressEventDispatcher.Factory progressDispatcherFactory) {
    results.buildResult =
        executorService.submit(
            () -> {
              Verify.verify(
                  results.baseImagesAndBuiltImages.get().size() == 1,
                  "multi-platform image building not supported when pushing to Docker engine");
              Image builtImage =
                  results.baseImagesAndBuiltImages.get().values().iterator().next().get();
              return new LoadDockerStep(
                      buildContext, progressDispatcherFactory, dockerClient, builtImage)
                  .call();
            });
  }

  private void writeTarFile(
      Path outputPath, ProgressEventDispatcher.Factory progressDispatcherFactory) {
    results.buildResult =
        executorService.submit(
            () -> {
              Verify.verify(
                  results.baseImagesAndBuiltImages.get().size() == 1,
                  "multi-platform image building not supported when building a local tar image");
              Image builtImage =
                  results.baseImagesAndBuiltImages.get().values().iterator().next().get();

              return new WriteTarFileStep(
                      buildContext, progressDispatcherFactory, outputPath, builtImage)
                  .call();
            });
  }

  private <E> List<Future<E>> scheduleCallables(ImmutableList<? extends Callable<E>> callables) {
    return callables.stream().map(executorService::submit).collect(Collectors.toList());
  }
}
