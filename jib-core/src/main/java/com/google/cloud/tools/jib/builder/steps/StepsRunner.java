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
import com.google.cloud.tools.jib.builder.ProgressEventDispatcher.Factory;
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
import java.util.Collection;
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
    private Future<Map<Future<Image>, Image>> builtImagesAndBaseImages = failedFuture();
    private Future<ManifestTemplate> manifestListOrSingleManifest = failedFuture();
    private Future<RegistryClient> targetRegistryClient = failedFuture();
    public Future<Map<Image, List<Future<BlobDescriptor>>>> baseImagesAndLayerPushResults =
        failedFuture();
    private Future<List<Future<BlobDescriptor>>> applicationLayerPushResults = failedFuture();
    private Future<Map<Future<Image>, Future<BlobDescriptor>>>
        builtImagesAndContainerConfigurationPushResults = failedFuture();
    private Future<Optional<ManifestAndDigest<ManifestTemplate>>> manifestCheckResult =
        failedFuture();
    public Future<List<Future<BuildResult>>> imagePushResults = failedFuture();
    private Future<BuildResult> buildResult = failedFuture();
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
    stepsToRun.add(this::buildManifestListOrSingleManifest);

    // push to registry
    stepsToRun.add(this::pushBaseImageLayers);
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
              // TODO: ideally, progressDispatcher should be closed at the right moment, after the
              // scheduled threads have completed. However, it can be tricky and cumbersome to track
              // completion, so it may just be better to delay closing until everything ends. At
              // least, we must ensure that it's not closed prematurely. (Garbage collection doesn't
              // auto-close it wit the current implementation.)
              ProgressEventDispatcher progressDispatcher =
                  childProgressDispatcherFactory.create(
                      "scheduling obtaining base image layers",
                      results.baseImagesAndRegistryClient.get().images.size());

              Map<Image, List<Future<PreparedLayer>>> baseImagesAndLayers = new HashMap<>();
              for (Image image : results.baseImagesAndRegistryClient.get().images) {
                List<Future<PreparedLayer>> layers =
                    scheduleCallables(
                        layersRequiredLocally
                            ? ObtainBaseImageLayerStep.makeListForForcedDownload(
                                buildContext,
                                progressDispatcher.newChildProducer(),
                                image,
                                results.baseImagesAndRegistryClient.get().registryClient)
                            : ObtainBaseImageLayerStep.makeListForSelectiveDownload(
                                buildContext,
                                progressDispatcher.newChildProducer(),
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

    results.baseImagesAndLayerPushResults =
        executorService.submit(
            () -> {
              // TODO: ideally, progressDispatcher should be closed at the right moment, after the
              // scheduled threads have completed. However, it can be tricky and cumbersome to track
              // completion, so it may just be better to delay closing until everything ends. At
              // least, we must ensure that it's not closed prematurely. (Garbage collection doesn't
              // auto-close it wit the current implementation.)
              ProgressEventDispatcher progressDispatcher =
                  childProgressDispatcherFactory.create(
                      "scheduling pushing base image layers",
                      results.baseImagesAndLayers.get().size());

              Map<Image, List<Future<BlobDescriptor>>> pushResults = new HashMap<>();
              for (Map.Entry<Image, List<Future<PreparedLayer>>> entry :
                  results.baseImagesAndLayers.get().entrySet()) {
                Image baseImage = entry.getKey();
                List<Future<PreparedLayer>> baseImageLayers = entry.getValue();

                List<Future<BlobDescriptor>> baseImageLayerPushResult =
                    scheduleCallables(
                        PushLayerStep.makeList(
                            buildContext,
                            progressDispatcher.newChildProducer(),
                            results.targetRegistryClient.get(),
                            Verify.verifyNotNull(baseImageLayers)));
                pushResults.put(baseImage, baseImageLayerPushResult);
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
    results.builtImagesAndBaseImages =
        executorService.submit(
            () -> {
              // TODO: ideally, progressDispatcher should be closed at the right moment, after the
              // scheduled threads have completed. However, it can be tricky and cumbersome to track
              // completion, so it may just be better to delay closing until everything ends. At
              // least, we must ensure that it's not closed prematurely. (Garbage collection doesn't
              // auto-close it wit the current implementation.)
              ProgressEventDispatcher progressDispatcher =
                  childProgressDispatcherFactory.create(
                      "scheduling building manifests", results.baseImagesAndLayers.get().size());

              Map<Future<Image>, Image> builtImagesAndBaseImages = new HashMap<>();
              for (Map.Entry<Image, List<Future<PreparedLayer>>> entry :
                  results.baseImagesAndLayers.get().entrySet()) {
                ProgressEventDispatcher.Factory progressDispatcherFactory =
                    progressDispatcher.newChildProducer();
                Future<Image> builtImage =
                    executorService.submit(
                        () ->
                            new BuildImageStep(
                                    buildContext,
                                    progressDispatcherFactory,
                                    entry.getKey(), // base Image
                                    realizeFutures(
                                        Verify.verifyNotNull(entry.getValue())), // layers
                                    realizeFutures(Verify.verifyNotNull(results.applicationLayers)))
                                .call());
                builtImagesAndBaseImages.put(builtImage, entry.getKey() /* base Image */);
              }
              return builtImagesAndBaseImages;
            });
  }

  private void buildManifestListOrSingleManifest() {
    ProgressEventDispatcher.Factory childProgressDispatcherFactory =
        Verify.verifyNotNull(rootProgressDispatcher).newChildProducer();

    results.manifestListOrSingleManifest =
        executorService.submit(
            () ->
                new BuildManifestListOrSingleManifestStep(
                        buildContext,
                        childProgressDispatcherFactory,
                        realizeFutures(results.builtImagesAndBaseImages.get().keySet()))
                    .call());
  }

  private void pushContainerConfigurations() {
    ProgressEventDispatcher.Factory childProgressDispatcherFactory =
        Verify.verifyNotNull(rootProgressDispatcher).newChildProducer();

    results.builtImagesAndContainerConfigurationPushResults =
        executorService.submit(
            () -> {
              // TODO: ideally, progressDispatcher should be closed at the right moment, after the
              // scheduled threads have completed. However, it can be tricky and cumbersome to track
              // completion, so it may just be better to delay closing until everything ends. At
              // least, we must ensure that it's not closed prematurely. (Garbage collection doesn't
              // auto-close it wit the current implementation.)
              ProgressEventDispatcher progressDispatcher =
                  childProgressDispatcherFactory.create(
                      "scheduling pushing container configurations",
                      results.builtImagesAndBaseImages.get().size());

              Map<Future<Image>, Future<BlobDescriptor>> pushResults = new HashMap<>();
              for (Future<Image> builtImage : results.builtImagesAndBaseImages.get().keySet()) {
                ProgressEventDispatcher.Factory progressDispatcherFactory =
                    progressDispatcher.newChildProducer();
                Future<BlobDescriptor> configPushResult =
                    executorService.submit(
                        () ->
                            new PushContainerConfigurationStep(
                                    buildContext,
                                    progressDispatcherFactory,
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

  private void checkManifestInTargetRegistry() {
    ProgressEventDispatcher.Factory childProgressDispatcherFactory =
        Verify.verifyNotNull(rootProgressDispatcher).newChildProducer();

    results.manifestCheckResult =
        executorService.submit(
            () ->
                new CheckManifestStep(
                        buildContext,
                        childProgressDispatcherFactory,
                        results.targetRegistryClient.get(),
                        results.manifestListOrSingleManifest.get())
                    .call());
  }

  private void pushImages() {
    ProgressEventDispatcher.Factory childProgressDispatcherFactory =
        Verify.verifyNotNull(rootProgressDispatcher).newChildProducer();

    results.imagePushResults =
        executorService.submit(
            () -> {
              // TODO: ideally, progressDispatcher should be closed at the right moment, after the
              // scheduled threads have completed. However, it can be tricky and cumbersome to track
              // completion, so it may just be better to delay closing until everything ends. At
              // least, we must ensure that it's not closed prematurely. (Garbage collection doesn't
              // auto-close it wit the current implementation.)
              ProgressEventDispatcher progressDispatcher =
                  childProgressDispatcherFactory.create(
                      "scheduling pushing manifests",
                      results.builtImagesAndBaseImages.get().size());

              realizeFutures(results.applicationLayerPushResults.get());

              List<Future<BuildResult>> buildResults = new ArrayList<>();
              for (Map.Entry<Future<Image>, Image> entry :
                  results.builtImagesAndBaseImages.get().entrySet()) {
                buildResults.add(
                    pushImage(
                        entry.getKey(), entry.getValue(), progressDispatcher.newChildProducer()));
              }
              return buildResults;
            });
  }

  private Future<BuildResult> pushImage(
      Future<Image> builtImage, Image baseImage, Factory progressDispatcherFactory) {
    return executorService.submit(
        () -> {
          realizeFutures(
              Verify.verifyNotNull(results.baseImagesAndLayerPushResults.get().get(baseImage)));

          Future<BlobDescriptor> containerConfigPushResult =
              results.builtImagesAndContainerConfigurationPushResults.get().get(builtImage);

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
                  Verify.verifyNotNull(containerConfigPushResult).get().getDigest())
              // Manifest pushers return the same BuildResult.
              : manifestPushResults.get(0).get();
        });
  }

  private void pushManifestList() {
    ProgressEventDispatcher.Factory childProgressDispatcherFactory =
        Verify.verifyNotNull(rootProgressDispatcher).newChildProducer();

    results.buildResult =
        executorService.submit(
            () -> {
              realizeFutures(results.imagePushResults.get());
              List<Future<BuildResult>> manifestListPushResults =
                  scheduleCallables(
                      PushImageStep.makeListForManifestList(
                          buildContext,
                          childProgressDispatcherFactory,
                          results.targetRegistryClient.get(),
                          results.manifestListOrSingleManifest.get(),
                          results.manifestCheckResult.get().isPresent()));

              realizeFutures(manifestListPushResults);
              return manifestListPushResults.isEmpty()
                  ? results.imagePushResults.get().get(0).get()
                  : manifestListPushResults.get(0).get();
            });
  }

  private void loadDocker(DockerClient dockerClient) {
    ProgressEventDispatcher.Factory childProgressDispatcherFactory =
        Verify.verifyNotNull(rootProgressDispatcher).newChildProducer();

    results.buildResult =
        executorService.submit(
            () -> {
              Verify.verify(
                  results.builtImagesAndBaseImages.get().size() == 1,
                  "multi-platform image building not supported when pushing to Docker engine");
              Image builtImage =
                  results.builtImagesAndBaseImages.get().keySet().iterator().next().get();
              return new LoadDockerStep(
                      buildContext, childProgressDispatcherFactory, dockerClient, builtImage)
                  .call();
            });
  }

  private void writeTarFile(Path outputPath) {
    ProgressEventDispatcher.Factory childProgressDispatcherFactory =
        Verify.verifyNotNull(rootProgressDispatcher).newChildProducer();

    results.buildResult =
        executorService.submit(
            () -> {
              Verify.verify(
                  results.builtImagesAndBaseImages.get().size() == 1,
                  "multi-platform image building not supported when building a local tar image");
              Image builtImage =
                  results.builtImagesAndBaseImages.get().keySet().iterator().next().get();

              return new WriteTarFileStep(
                      buildContext, childProgressDispatcherFactory, outputPath, builtImage)
                  .call();
            });
  }

  private <E> List<Future<E>> scheduleCallables(ImmutableList<? extends Callable<E>> callables) {
    return callables.stream().map(executorService::submit).collect(Collectors.toList());
  }
}
