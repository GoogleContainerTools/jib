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
import com.google.cloud.tools.jib.api.ImageReference;
import com.google.cloud.tools.jib.api.LogEvent;
import com.google.cloud.tools.jib.api.RegistryException;
import com.google.cloud.tools.jib.api.RegistryUnauthorizedException;
import com.google.cloud.tools.jib.api.buildplan.Platform;
import com.google.cloud.tools.jib.blob.Blobs;
import com.google.cloud.tools.jib.builder.ProgressEventDispatcher;
import com.google.cloud.tools.jib.builder.TimerEventDispatcher;
import com.google.cloud.tools.jib.builder.steps.PullBaseImageStep.ImagesAndRegistryClient;
import com.google.cloud.tools.jib.cache.Cache;
import com.google.cloud.tools.jib.cache.CacheCorruptedException;
import com.google.cloud.tools.jib.configuration.BuildContext;
import com.google.cloud.tools.jib.configuration.ImageConfiguration;
import com.google.cloud.tools.jib.event.EventHandlers;
import com.google.cloud.tools.jib.event.events.ProgressEvent;
import com.google.cloud.tools.jib.image.Image;
import com.google.cloud.tools.jib.image.LayerCountMismatchException;
import com.google.cloud.tools.jib.image.LayerPropertyNotFoundException;
import com.google.cloud.tools.jib.image.json.BadContainerConfigurationFormatException;
import com.google.cloud.tools.jib.image.json.BuildableManifestTemplate;
import com.google.cloud.tools.jib.image.json.ContainerConfigurationTemplate;
import com.google.cloud.tools.jib.image.json.ImageMetadataTemplate;
import com.google.cloud.tools.jib.image.json.JsonToImageTranslator;
import com.google.cloud.tools.jib.image.json.ManifestAndConfigTemplate;
import com.google.cloud.tools.jib.image.json.ManifestListTemplate;
import com.google.cloud.tools.jib.image.json.ManifestTemplate;
import com.google.cloud.tools.jib.image.json.PlatformNotFoundInBaseImageException;
import com.google.cloud.tools.jib.image.json.UnknownManifestFormatException;
import com.google.cloud.tools.jib.image.json.UnlistedPlatformInManifestListException;
import com.google.cloud.tools.jib.image.json.V21ManifestTemplate;
import com.google.cloud.tools.jib.json.JsonTemplateMapper;
import com.google.cloud.tools.jib.registry.ManifestAndDigest;
import com.google.cloud.tools.jib.registry.RegistryClient;
import com.google.cloud.tools.jib.registry.RegistryUnauthorizedExceptionHandler;
import com.google.cloud.tools.jib.registry.credentials.CredentialRetrievalException;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Verify;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Supplier;
import javax.annotation.Nullable;

/** Pulls the base image manifests for the specified platforms. */
class PullBaseImageStep implements Callable<ImagesAndRegistryClient> {

  private static final String DESCRIPTION = "Pulling base image manifest";

  /** Structure for the result returned by this step. */
  static class ImagesAndRegistryClient {

    final List<Image> images;
    @Nullable final RegistryClient registryClient;

    ImagesAndRegistryClient(List<Image> images, @Nullable RegistryClient registryClient) {
      this.images = images;
      this.registryClient = registryClient;
    }
  }

  private final BuildContext buildContext;
  private final ProgressEventDispatcher.Factory progressDispatcherFactory;

  PullBaseImageStep(
      BuildContext buildContext, ProgressEventDispatcher.Factory progressDispatcherFactory) {
    this.buildContext = buildContext;
    this.progressDispatcherFactory = progressDispatcherFactory;
  }

  @Override
  public ImagesAndRegistryClient call()
      throws IOException, RegistryException, LayerPropertyNotFoundException,
          LayerCountMismatchException, BadContainerConfigurationFormatException,
          CacheCorruptedException, CredentialRetrievalException {
    EventHandlers eventHandlers = buildContext.getEventHandlers();
    try (ProgressEventDispatcher progressDispatcher =
            progressDispatcherFactory.create("pulling base image manifest", 4);
        TimerEventDispatcher ignored1 = new TimerEventDispatcher(eventHandlers, DESCRIPTION)) {

      // Skip this step if this is a scratch image
      ImageReference imageReference = buildContext.getBaseImageConfiguration().getImage();
      if (imageReference.isScratch()) {
        Set<Platform> platforms = buildContext.getContainerConfiguration().getPlatforms();
        Verify.verify(!platforms.isEmpty());

        eventHandlers.dispatch(LogEvent.progress("Getting scratch base image..."));
        ImmutableList.Builder<Image> images = ImmutableList.builder();
        for (Platform platform : platforms) {
          Image.Builder imageBuilder = Image.builder(buildContext.getTargetFormat());
          imageBuilder.setArchitecture(platform.getArchitecture()).setOs(platform.getOs());
          images.add(imageBuilder.build());
        }
        return new ImagesAndRegistryClient(images.build(), null);
      }

      eventHandlers.dispatch(
          LogEvent.progress("Getting manifest for base image " + imageReference + "..."));

      if (buildContext.isOffline()) {
        List<Image> images = getCachedBaseImages();
        if (!images.isEmpty()) {
          return new ImagesAndRegistryClient(images, null);
        }
        throw new IOException(
            "Cannot run Jib in offline mode; " + imageReference + " not found in local Jib cache");

      } else if (imageReference.getDigest().isPresent()) {
        List<Image> images = getCachedBaseImages();
        if (!images.isEmpty()) {
          RegistryClient onDemandAuthRegistryClient = createOnDemandAuthenticatingRegistryClient();
          return new ImagesAndRegistryClient(images, onDemandAuthRegistryClient);
        }
      }

      Optional<ImagesAndRegistryClient> mirrorPull =
          tryMirrors(buildContext, progressDispatcher.newChildProducer());
      if (mirrorPull.isPresent()) {
        return mirrorPull.get();
      }

      RegistryClient onDemandAuthRegistryClient = createOnDemandAuthenticatingRegistryClient();
      return new ImagesAndRegistryClient(
          pullBaseImages(onDemandAuthRegistryClient, progressDispatcher.newChildProducer()),
          onDemandAuthRegistryClient);
    }
  }

  private RegistryClient createOnDemandAuthenticatingRegistryClient()
      throws CredentialRetrievalException {
    Credential credential =
        RegistryCredentialRetriever.getBaseImageCredential(buildContext).orElse(null);
    return buildContext
        .newBaseImageRegistryClientFactory()
        .setCredential(credential)
        .setUnauthorizedExceptionHandlerSupplier(
            () -> PullBaseImageStep::handleRegistryUnauthorizedException)
        .newRegistryClient();
  }

  /**
   * Handles an unauthorized exception by performing authentication on demand.
   *
   * @param registryClient the registry client to be reconfigured
   * @param ex the exception that was caught
   * @return a supplier of the next handler, used only if another exception is thrown
   * @throws RegistryException if a registry error occurs
   * @throws IOException if an I/O error occurs
   */
  static Supplier<RegistryUnauthorizedExceptionHandler> handleRegistryUnauthorizedException(
      final RegistryClient registryClient, final RegistryUnauthorizedException ex)
      throws RegistryException, IOException {
    // Double indentation keeps code at same level as original code
    {
      {
        final EventHandlers eventHandlers = registryClient.getEventHandlers();
        final String imageReference = ex.getImageReference();
        String wwwAuthenticate = ex.getHttpResponseException().getHeaders().getAuthenticate();
        if (wwwAuthenticate != null) {
          eventHandlers.dispatch(
              LogEvent.debug("WWW-Authenticate for " + imageReference + ": " + wwwAuthenticate));
          registryClient.authPullByWwwAuthenticate(wwwAuthenticate);
        } else {
          // Not getting WWW-Authenticate is unexpected in practice, and we may just blame the
          // server and fail. However, to keep some old behavior, try a few things as a last resort.
          // TODO: consider removing this fallback branch.
          eventHandlers.dispatch(
              LogEvent.debug("Trying bearer auth as fallback for " + imageReference + "..."));
          if (!registryClient.doPullBearerAuth()) {
            eventHandlers.dispatch(
                LogEvent.error("Failed to bearer auth for pull of " + imageReference));
            throw ex;
          }
        }

        // If another exception occurs, use the default behavior
        return RegistryClient::defaultRegistryUnauthorizedExceptionHandler;
      }
    }
  }

  @VisibleForTesting
  Optional<ImagesAndRegistryClient> tryMirrors(
      BuildContext buildContext, ProgressEventDispatcher.Factory progressDispatcherFactory)
      throws LayerCountMismatchException, BadContainerConfigurationFormatException {
    EventHandlers eventHandlers = buildContext.getEventHandlers();

    Collection<Map.Entry<String, String>> mirrorEntries =
        buildContext.getRegistryMirrors().entries();
    try (ProgressEventDispatcher progressDispatcher1 =
            progressDispatcherFactory.create("trying mirrors", mirrorEntries.size());
        TimerEventDispatcher ignored1 = new TimerEventDispatcher(eventHandlers, "trying mirrors")) {
      for (Map.Entry<String, String> entry : mirrorEntries) {
        String registry = entry.getKey();
        String mirror = entry.getValue();
        eventHandlers.dispatch(LogEvent.debug("mirror config: " + registry + " --> " + mirror));

        if (!buildContext.getBaseImageConfiguration().getImageRegistry().equals(registry)) {
          progressDispatcher1.dispatchProgress(1);
          continue;
        }

        eventHandlers.dispatch(LogEvent.info("trying mirror " + mirror + " for the base image"));
        try (ProgressEventDispatcher progressDispatcher2 =
            progressDispatcher1.newChildProducer().create("trying mirror " + mirror, 2)) {
          RegistryClient registryClient =
              buildContext.newBaseImageRegistryClientFactory(mirror).newRegistryClient();
          List<Image> images = pullPublicImages(registryClient, progressDispatcher2);
          eventHandlers.dispatch(LogEvent.info("pulled manifest from mirror " + mirror));
          return Optional.of(new ImagesAndRegistryClient(images, registryClient));

        } catch (IOException | RegistryException ex) {
          // Ignore errors from this mirror and continue.
          eventHandlers.dispatch(
              LogEvent.debug(
                  "failed to get manifest from mirror " + mirror + ": " + ex.getMessage()));
        }
      }
      return Optional.empty();
    }
  }

  private List<Image> pullPublicImages(
      RegistryClient registryClient, ProgressEventDispatcher progressDispatcher)
      throws IOException, RegistryException, LayerCountMismatchException,
          BadContainerConfigurationFormatException {
    try {
      // First, try with no credentials. This works with public GCR images.
      return pullBaseImages(registryClient, progressDispatcher.newChildProducer());

    } catch (RegistryUnauthorizedException ex) {
      // in case if a registry requires bearer auth
      registryClient.doPullBearerAuth();
      return pullBaseImages(registryClient, progressDispatcher.newChildProducer());
    }
  }

  /**
   * Pulls the base images specified in the platforms list.
   *
   * @param registryClient to communicate with remote registry
   * @param progressDispatcherFactory the {@link ProgressEventDispatcher.Factory} for emitting
   *     {@link ProgressEvent}s
   * @return the list of pulled base images and a registry client
   * @throws IOException when an I/O exception occurs during the pulling
   * @throws RegistryException if communicating with the registry caused a known error
   * @throws LayerCountMismatchException if the manifest and configuration contain conflicting layer
   *     information
   * @throws LayerPropertyNotFoundException if adding image layers fails
   * @throws BadContainerConfigurationFormatException if the container configuration is in a bad
   *     format
   */
  private List<Image> pullBaseImages(
      RegistryClient registryClient, ProgressEventDispatcher.Factory progressDispatcherFactory)
      throws IOException, RegistryException, LayerPropertyNotFoundException,
          LayerCountMismatchException, BadContainerConfigurationFormatException {
    Cache cache = buildContext.getBaseImageLayersCache();
    EventHandlers eventHandlers = buildContext.getEventHandlers();
    ImageConfiguration baseImageConfig = buildContext.getBaseImageConfiguration();

    try (ProgressEventDispatcher progressDispatcher1 =
        progressDispatcherFactory.create("pulling base image manifest and container config", 2)) {
      ManifestAndDigest<?> manifestAndDigest =
          registryClient.pullManifest(baseImageConfig.getImageQualifier());
      eventHandlers.dispatch(
          LogEvent.lifecycle("Using base image with digest: " + manifestAndDigest.getDigest()));
      progressDispatcher1.dispatchProgress(1);
      ProgressEventDispatcher.Factory childProgressDispatcherFactory =
          progressDispatcher1.newChildProducer();

      ManifestTemplate manifestTemplate = manifestAndDigest.getManifest();
      if (manifestTemplate instanceof V21ManifestTemplate) {
        V21ManifestTemplate v21Manifest = (V21ManifestTemplate) manifestTemplate;
        cache.writeMetadata(baseImageConfig.getImage(), v21Manifest);
        return Collections.singletonList(JsonToImageTranslator.toImage(v21Manifest));

      } else if (manifestTemplate instanceof BuildableManifestTemplate) {
        // V22ManifestTemplate or OciManifestTemplate
        BuildableManifestTemplate imageManifest = (BuildableManifestTemplate) manifestTemplate;
        ContainerConfigurationTemplate containerConfig =
            pullContainerConfigJson(
                manifestAndDigest, registryClient, childProgressDispatcherFactory);
        PlatformChecker.checkManifestPlatform(buildContext, containerConfig);
        cache.writeMetadata(baseImageConfig.getImage(), imageManifest, containerConfig);
        return Collections.singletonList(
            JsonToImageTranslator.toImage(imageManifest, containerConfig));
      }

      Verify.verify(manifestTemplate instanceof ManifestListTemplate);

      List<ManifestAndConfigTemplate> manifestsAndConfigs = new ArrayList<>();
      ImmutableList.Builder<Image> images = ImmutableList.builder();
      Set<Platform> platforms = buildContext.getContainerConfiguration().getPlatforms();
      try (ProgressEventDispatcher progressDispatcher2 =
          childProgressDispatcherFactory.create(
              "pulling platform-specific manifests and container configs", 2L * platforms.size())) {
        // If a manifest list, search for the manifests matching the given platforms.
        for (Platform platform : platforms) {
          String message = "Searching for architecture=%s, os=%s in the base image manifest list";
          eventHandlers.dispatch(
              LogEvent.info(String.format(message, platform.getArchitecture(), platform.getOs())));

          String manifestDigest =
              lookUpPlatformSpecificImageManifest(
                  (ManifestListTemplate) manifestTemplate, platform);
          // TODO: pull multiple manifests (+ container configs) in parallel.
          ManifestAndDigest<?> imageManifestAndDigest = registryClient.pullManifest(manifestDigest);
          progressDispatcher2.dispatchProgress(1);

          BuildableManifestTemplate imageManifest =
              (BuildableManifestTemplate) imageManifestAndDigest.getManifest();
          ContainerConfigurationTemplate containerConfig =
              pullContainerConfigJson(
                  imageManifestAndDigest, registryClient, progressDispatcher2.newChildProducer());

          manifestsAndConfigs.add(
              new ManifestAndConfigTemplate(imageManifest, containerConfig, manifestDigest));
          images.add(JsonToImageTranslator.toImage(imageManifest, containerConfig));
        }
      }

      cache.writeMetadata(
          baseImageConfig.getImage(),
          new ImageMetadataTemplate(manifestTemplate /* manifest list */, manifestsAndConfigs));
      return images.build();
    }
  }

  /**
   * Looks through a manifest list for the manifest matching the {@code platform} and returns the
   * digest of the first manifest it finds.
   */
  @VisibleForTesting
  String lookUpPlatformSpecificImageManifest(
      ManifestListTemplate manifestListTemplate, Platform platform)
      throws UnlistedPlatformInManifestListException {
    EventHandlers eventHandlers = buildContext.getEventHandlers();

    List<String> digests =
        manifestListTemplate.getDigestsForPlatform(platform.getArchitecture(), platform.getOs());
    if (digests.isEmpty()) {
      String errorTemplate =
          buildContext.getBaseImageConfiguration().getImage()
              + " is a manifest list, but the list does not contain an image for architecture=%s, "
              + "os=%s. If your intention was to specify a platform for your image, see "
              + "https://github.com/GoogleContainerTools/jib/blob/master/docs/faq.md#how-do-i-specify-a-platform-in-the-manifest-list-or-oci-index-of-a-base-image";
      String error = String.format(errorTemplate, platform.getArchitecture(), platform.getOs());
      eventHandlers.dispatch(LogEvent.error(error));
      throw new UnlistedPlatformInManifestListException(error);
    }
    // TODO: perhaps we should return multiple digests matching the platform.
    return digests.get(0);
  }

  /**
   * Pulls a container configuration JSON specified in the given manifest.
   *
   * @param manifestAndDigest a manifest JSON and its digest
   * @param registryClient to communicate with remote registry
   * @param progressDispatcherFactory the {@link ProgressEventDispatcher.Factory} for emitting
   *     {@link ProgressEvent}s
   * @return pulled {@link ContainerConfigurationTemplate}
   * @throws IOException when an I/O exception occurs during the pulling
   * @throws LayerPropertyNotFoundException if adding image layers fails
   */
  private ContainerConfigurationTemplate pullContainerConfigJson(
      ManifestAndDigest<?> manifestAndDigest,
      RegistryClient registryClient,
      ProgressEventDispatcher.Factory progressDispatcherFactory)
      throws IOException, LayerPropertyNotFoundException, UnknownManifestFormatException {
    BuildableManifestTemplate manifest =
        (BuildableManifestTemplate) manifestAndDigest.getManifest();
    Preconditions.checkArgument(manifest.getSchemaVersion() == 2);

    if (manifest.getContainerConfiguration() == null
        || manifest.getContainerConfiguration().getDigest() == null) {
      throw new UnknownManifestFormatException(
          "Invalid container configuration in Docker V2.2/OCI manifest: \n"
              + JsonTemplateMapper.toUtf8String(manifest));
    }

    try (ThrottledProgressEventDispatcherWrapper progressDispatcherWrapper =
        new ThrottledProgressEventDispatcherWrapper(
            progressDispatcherFactory,
            "pull container configuration " + manifest.getContainerConfiguration().getDigest())) {

      String containerConfigString =
          Blobs.writeToString(
              registryClient.pullBlob(
                  manifest.getContainerConfiguration().getDigest(),
                  progressDispatcherWrapper::setProgressTarget,
                  progressDispatcherWrapper::dispatchProgress));
      return JsonTemplateMapper.readJson(
          containerConfigString, ContainerConfigurationTemplate.class);
    }
  }

  /**
   * Retrieves the cached base images. If a base image reference is not a manifest list, returns a
   * single image (if cached). If a manifest list, returns all the images matching the configured
   * platforms in the manifest list but only when all of the images are cached.
   *
   * @return the cached images, if found
   * @throws IOException when an I/O exception occurs
   * @throws CacheCorruptedException if the cache is corrupted
   * @throws LayerPropertyNotFoundException if adding image layers fails
   * @throws BadContainerConfigurationFormatException if the container configuration is in a bad
   *     format
   * @throws UnlistedPlatformInManifestListException if a cached manifest list has no manifests
   *     matching the configured platform
   */
  @VisibleForTesting
  List<Image> getCachedBaseImages()
      throws IOException, CacheCorruptedException, BadContainerConfigurationFormatException,
          LayerCountMismatchException, UnlistedPlatformInManifestListException,
          PlatformNotFoundInBaseImageException {
    ImageReference baseImage = buildContext.getBaseImageConfiguration().getImage();
    Optional<ImageMetadataTemplate> metadata =
        buildContext.getBaseImageLayersCache().retrieveMetadata(baseImage);
    if (!metadata.isPresent()) {
      return Collections.emptyList();
    }

    ManifestTemplate manifestList = metadata.get().getManifestList();
    List<ManifestAndConfigTemplate> manifestsAndConfigs = metadata.get().getManifestsAndConfigs();

    if (manifestList == null) {
      Verify.verify(manifestsAndConfigs.size() == 1);
      ManifestAndConfigTemplate manifestAndConfig = manifestsAndConfigs.get(0);
      Optional<Image> cachedImage = getBaseImageIfAllLayersCached(manifestAndConfig, true);
      if (!cachedImage.isPresent()) {
        return Collections.emptyList();
      }
      return Collections.singletonList(cachedImage.get());
    }

    // Manifest list cached. Identify matching platforms and check if all of them are cached.
    ImmutableList.Builder<Image> images = ImmutableList.builder();
    for (Platform platform : buildContext.getContainerConfiguration().getPlatforms()) {
      String manifestDigest =
          lookUpPlatformSpecificImageManifest((ManifestListTemplate) manifestList, platform);

      Optional<ManifestAndConfigTemplate> manifestAndConfigFound =
          manifestsAndConfigs.stream()
              .filter(entry -> manifestDigest.equals(entry.getManifestDigest()))
              .findFirst();
      if (!manifestAndConfigFound.isPresent()) {
        return Collections.emptyList();
      }
      Optional<Image> cachedImage =
          getBaseImageIfAllLayersCached(manifestAndConfigFound.get(), false);
      if (!cachedImage.isPresent()) {
        return Collections.emptyList();
      }
      images.add(cachedImage.get());
    }
    return images.build();
  }

  /**
   * Helper method to retrieve a base image from cache given manifest and container config. Does not
   * return image if any layers of base image are missing in cache.
   *
   * @param manifestAndConfig stores an image manifest and a container config
   * @param isSingleManifest true if base image is not a manifest list
   * @return the single cached {@link Image} found, or {@code Optional#empty()} if the base image
   *     not found in cache with all layers present.
   * @throws BadContainerConfigurationFormatException if the container configuration is in a bad
   *     format
   * @throws PlatformNotFoundInBaseImageException if build target platform is not found in the base
   *     image
   * @throws LayerCountMismatchException LayerCountMismatchException if the manifest and
   *     configuration contain conflicting layer information
   */
  private Optional<Image> getBaseImageIfAllLayersCached(
      ManifestAndConfigTemplate manifestAndConfig, boolean isSingleManifest)
      throws BadContainerConfigurationFormatException, PlatformNotFoundInBaseImageException,
          LayerCountMismatchException {
    Cache baseImageLayersCache = buildContext.getBaseImageLayersCache();
    ManifestTemplate manifest = Verify.verifyNotNull(manifestAndConfig.getManifest());

    // Verify all layers described in manifest are present in cache
    if (!baseImageLayersCache.areAllLayersCached(manifest)) {
      return Optional.empty();
    }
    if (manifest instanceof V21ManifestTemplate) {
      return Optional.of(JsonToImageTranslator.toImage((V21ManifestTemplate) manifest));
    }
    ContainerConfigurationTemplate containerConfig =
        Verify.verifyNotNull(manifestAndConfig.getConfig());
    if (isSingleManifest) {
      // If base image is not a manifest list, check and warn misconfigured platforms.
      PlatformChecker.checkManifestPlatform(buildContext, containerConfig);
    }
    return Optional.of(
        JsonToImageTranslator.toImage((BuildableManifestTemplate) manifest, containerConfig));
  }
}
