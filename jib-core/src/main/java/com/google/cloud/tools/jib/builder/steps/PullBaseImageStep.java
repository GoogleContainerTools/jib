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
import com.google.cloud.tools.jib.image.json.ManifestTemplate;
import com.google.cloud.tools.jib.image.json.UnknownManifestFormatException;
import com.google.cloud.tools.jib.image.json.UnlistedPlatformInManifestListException;
import com.google.cloud.tools.jib.image.json.V21ManifestTemplate;
import com.google.cloud.tools.jib.image.json.V22ManifestListTemplate;
import com.google.cloud.tools.jib.json.JsonTemplateMapper;
import com.google.cloud.tools.jib.registry.ManifestAndDigest;
import com.google.cloud.tools.jib.registry.RegistryClient;
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
          RegistryClient noAuthRegistryClient =
              buildContext.newBaseImageRegistryClientFactory().newRegistryClient();
          // TODO: passing noAuthRegistryClient may be problematic. It may return 401 unauthorized
          // if layers have to be downloaded.
          // https://github.com/GoogleContainerTools/jib/issues/2220
          return new ImagesAndRegistryClient(images, noAuthRegistryClient);
        }
      }

      Optional<ImagesAndRegistryClient> mirrorPull =
          tryMirrors(buildContext, progressDispatcher.newChildProducer());
      if (mirrorPull.isPresent()) {
        return mirrorPull.get();
      }

      try {
        // First, try with no credentials. This works with public GCR images (but not Docker Hub).
        // TODO: investigate if we should just pass credentials up front. However, this involves
        // some risk. https://github.com/GoogleContainerTools/jib/pull/2200#discussion_r359069026
        // contains some related discussions.
        RegistryClient noAuthRegistryClient =
            buildContext.newBaseImageRegistryClientFactory().newRegistryClient();
        return new ImagesAndRegistryClient(
            pullBaseImages(noAuthRegistryClient, progressDispatcher.newChildProducer()),
            noAuthRegistryClient);

      } catch (RegistryUnauthorizedException ex) {
        eventHandlers.dispatch(
            LogEvent.lifecycle(
                "The base image requires auth. Trying again for " + imageReference + "..."));

        Credential credential =
            RegistryCredentialRetriever.getBaseImageCredential(buildContext).orElse(null);
        RegistryClient registryClient =
            buildContext
                .newBaseImageRegistryClientFactory()
                .setCredential(credential)
                .newRegistryClient();

        String wwwAuthenticate = ex.getHttpResponseException().getHeaders().getAuthenticate();
        if (wwwAuthenticate != null) {
          eventHandlers.dispatch(
              LogEvent.debug("WWW-Authenticate for " + imageReference + ": " + wwwAuthenticate));
          registryClient.authPullByWwwAuthenticate(wwwAuthenticate);
          return new ImagesAndRegistryClient(
              pullBaseImages(registryClient, progressDispatcher.newChildProducer()),
              registryClient);

        } else {
          // Not getting WWW-Authenticate is unexpected in practice, and we may just blame the
          // server and fail. However, to keep some old behavior, try a few things as a last resort.
          // TODO: consider removing this fallback branch.
          if (credential != null && !credential.isOAuth2RefreshToken()) {
            eventHandlers.dispatch(
                LogEvent.debug("Trying basic auth as fallback for " + imageReference + "..."));
            registryClient.configureBasicAuth();
            try {
              return new ImagesAndRegistryClient(
                  pullBaseImages(registryClient, progressDispatcher.newChildProducer()),
                  registryClient);
            } catch (RegistryUnauthorizedException ignored) {
              // Fall back to try bearer auth.
            }
          }

          eventHandlers.dispatch(
              LogEvent.debug("Trying bearer auth as fallback for " + imageReference + "..."));
          registryClient.doPullBearerAuth();
          return new ImagesAndRegistryClient(
              pullBaseImages(registryClient, progressDispatcher.newChildProducer()),
              registryClient);
        }
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
          // First, try with no credentials. This works with public GCR images.
          RegistryClient registryClient =
              buildContext.newBaseImageRegistryClientFactory(mirror).newRegistryClient();
          try {
            List<Image> images =
                pullBaseImages(registryClient, progressDispatcher2.newChildProducer());
            eventHandlers.dispatch(LogEvent.info("pulled manifest from mirror " + mirror));
            return Optional.of(new ImagesAndRegistryClient(images, registryClient));

          } catch (RegistryUnauthorizedException ex) {
            // in case if a mirror requires bearer auth
            eventHandlers.dispatch(LogEvent.debug("mirror " + mirror + " requires auth"));
            registryClient.doPullBearerAuth();
            List<Image> images =
                pullBaseImages(registryClient, progressDispatcher2.newChildProducer());
            eventHandlers.dispatch(LogEvent.info("pulled manifest from mirror " + mirror));
            return Optional.of(new ImagesAndRegistryClient(images, registryClient));
          }

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

      // TODO: support OciIndexTemplate once AbstractManifestPuller starts to accept it.
      Verify.verify(manifestTemplate instanceof V22ManifestListTemplate);

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
                  (V22ManifestListTemplate) manifestTemplate, platform);
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
  // TODO: support OciIndexTemplate once AbstractManifestPuller starts to accept it.
  @VisibleForTesting
  String lookUpPlatformSpecificImageManifest(
      V22ManifestListTemplate manifestListTemplate, Platform platform)
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
          LayerCountMismatchException, UnlistedPlatformInManifestListException {
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
      ManifestTemplate manifest = manifestsAndConfigs.get(0).getManifest();
      if (manifest instanceof V21ManifestTemplate) {
        return Collections.singletonList(
            JsonToImageTranslator.toImage((V21ManifestTemplate) manifest));
      }

      ContainerConfigurationTemplate containerConfig =
          Verify.verifyNotNull(manifestsAndConfigs.get(0).getConfig());
      PlatformChecker.checkManifestPlatform(buildContext, containerConfig);

      return Collections.singletonList(
          JsonToImageTranslator.toImage(
              (BuildableManifestTemplate) Verify.verifyNotNull(manifest), containerConfig));
    }

    // Manifest list cached. Identify matching platforms and check if all of them are cached.
    ImmutableList.Builder<Image> images = ImmutableList.builder();
    for (Platform platform : buildContext.getContainerConfiguration().getPlatforms()) {
      String manifestDigest =
          lookUpPlatformSpecificImageManifest((V22ManifestListTemplate) manifestList, platform);

      Optional<ManifestAndConfigTemplate> manifestAndConfigFound =
          manifestsAndConfigs.stream()
              .filter(entry -> manifestDigest.equals(entry.getManifestDigest()))
              .findFirst();
      if (!manifestAndConfigFound.isPresent()) {
        return Collections.emptyList();
      }

      ManifestTemplate manifest = Verify.verifyNotNull(manifestAndConfigFound.get().getManifest());
      ContainerConfigurationTemplate containerConfig =
          Verify.verifyNotNull(manifestAndConfigFound.get().getConfig());
      images.add(
          JsonToImageTranslator.toImage((BuildableManifestTemplate) manifest, containerConfig));
    }
    return images.build();
  }
}
