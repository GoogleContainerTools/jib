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
import java.util.Collections;
import java.util.List;
import java.util.Optional;
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
  private final ProgressEventDispatcher.Factory progressEventDispatcherFactory;

  PullBaseImageStep(
      BuildContext buildContext, ProgressEventDispatcher.Factory progressEventDispatcherFactory) {
    this.buildContext = buildContext;
    this.progressEventDispatcherFactory = progressEventDispatcherFactory;
  }

  @Override
  public ImagesAndRegistryClient call()
      throws IOException, RegistryException, LayerPropertyNotFoundException,
          LayerCountMismatchException, BadContainerConfigurationFormatException,
          CacheCorruptedException, CredentialRetrievalException {
    EventHandlers eventHandlers = buildContext.getEventHandlers();
    // Skip this step if this is a scratch image
    ImageReference imageReference = buildContext.getBaseImageConfiguration().getImage();
    if (imageReference.isScratch()) {
      eventHandlers.dispatch(LogEvent.progress("Getting scratch base image..."));
      return new ImagesAndRegistryClient(
          Collections.singletonList(Image.builder(buildContext.getTargetFormat()).build()), null);
    }

    eventHandlers.dispatch(
        LogEvent.progress("Getting manifest for base image " + imageReference + "..."));

    if (buildContext.isOffline()) {
      Optional<Image> image = getCachedBaseImage();
      if (image.isPresent()) {
        if (!checkImagePlatform(image.get())) {
          throw new IllegalStateException(
              "The cached base image manifest does not match the configured platform due to the "
                  + "current implementation of limited platform support. As a workaround, re-run Jib "
                  + "online once to re-cache the right image manifest.");
        }
        return new ImagesAndRegistryClient(Collections.singletonList(image.get()), null);
      }
      throw new IOException(
          "Cannot run Jib in offline mode; " + imageReference + " not found in local Jib cache");

    } else if (imageReference.getDigest().isPresent()) {
      Optional<Image> image = getCachedBaseImage();
      if (image.isPresent() && checkImagePlatform(image.get())) {
        RegistryClient noAuthRegistryClient =
            buildContext.newBaseImageRegistryClientFactory().newRegistryClient();
        // TODO: passing noAuthRegistryClient may be problematic. It may return 401 unauthorized if
        // layers have to be downloaded. https://github.com/GoogleContainerTools/jib/issues/2220
        return new ImagesAndRegistryClient(
            Collections.singletonList(image.get()), noAuthRegistryClient);
      }
    }

    try (ProgressEventDispatcher progressEventDispatcher =
            progressEventDispatcherFactory.create("pulling base image manifest", 2);
        TimerEventDispatcher ignored1 = new TimerEventDispatcher(eventHandlers, DESCRIPTION)) {

      // First, try with no credentials.
      RegistryClient noAuthRegistryClient =
          buildContext.newBaseImageRegistryClientFactory().newRegistryClient();
      try {
        return new ImagesAndRegistryClient(
            pullBaseImages(noAuthRegistryClient, progressEventDispatcher), noAuthRegistryClient);

      } catch (RegistryUnauthorizedException ex) {
        eventHandlers.dispatch(
            LogEvent.lifecycle(
                "The base image requires auth. Trying again for " + imageReference + "..."));

        Credential registryCredential =
            RegistryCredentialRetriever.getBaseImageCredential(buildContext).orElse(null);

        RegistryClient registryClient =
            buildContext
                .newBaseImageRegistryClientFactory()
                .setCredential(registryCredential)
                .newRegistryClient();

        try {
          // TODO: refactor the code (https://github.com/GoogleContainerTools/jib/pull/2202)
          if (registryCredential == null || registryCredential.isOAuth2RefreshToken()) {
            throw ex;
          }

          eventHandlers.dispatch(LogEvent.debug("Trying basic auth for " + imageReference + "..."));
          registryClient.configureBasicAuth();
          return new ImagesAndRegistryClient(
              pullBaseImages(registryClient, progressEventDispatcher), registryClient);

        } catch (RegistryUnauthorizedException registryUnauthorizedException) {
          // The registry requires us to authenticate using the Docker Token Authentication.
          // See https://docs.docker.com/registry/spec/auth/token
          eventHandlers.dispatch(
              LogEvent.debug("Trying bearer auth for " + imageReference + "..."));
          if (registryClient.doPullBearerAuth()) {
            return new ImagesAndRegistryClient(
                pullBaseImages(registryClient, progressEventDispatcher), registryClient);
          }
          eventHandlers.dispatch(
              LogEvent.error(
                  "The registry asked for basic authentication, but the registry had refused basic "
                      + "authentication previously"));
          throw registryUnauthorizedException;
        }
      }
    }
  }

  // TODO: remove when properly caching manifests with multiple platforms and manifest lists.
  private boolean checkImagePlatform(Image image) {
    Preconditions.checkState(buildContext.getContainerConfiguration().getPlatforms().size() == 1);
    Platform platform = buildContext.getContainerConfiguration().getPlatforms().iterator().next();
    boolean ok =
        image.getArchitecture().equals(platform.getArchitecture())
            && image.getOs().equals(platform.getOs());
    if (!ok) {
      String message = "platform of the cached manifest does not match the requested one";
      buildContext.getEventHandlers().dispatch(LogEvent.debug(message));
    }
    return ok;
  }

  /**
   * Pulls the base images specified in the platforms list.
   *
   * @param registryClient to communicate with remote registry
   * @param progressEventDispatcher the {@link ProgressEventDispatcher} for emitting {@link
   *     ProgressEvent}s
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
      RegistryClient registryClient, ProgressEventDispatcher progressEventDispatcher)
      throws IOException, RegistryException, LayerPropertyNotFoundException,
          LayerCountMismatchException, BadContainerConfigurationFormatException {
    Cache cache = buildContext.getBaseImageLayersCache();
    ImageConfiguration baseImageConfig = buildContext.getBaseImageConfiguration();

    ManifestAndDigest<?> manifestAndDigest =
        registryClient.pullManifest(baseImageConfig.getImageQualifier());
    ManifestTemplate manifestTemplate = manifestAndDigest.getManifest();

    if (manifestTemplate instanceof V21ManifestTemplate) {
      V21ManifestTemplate v21Manifest = (V21ManifestTemplate) manifestTemplate;
      cache.writeMetadata(baseImageConfig.getImage(), v21Manifest);
      return Collections.singletonList(JsonToImageTranslator.toImage(v21Manifest));
    }

    ManifestTemplate manifestList = null;
    List<BuildableManifestTemplate> manifests = new ArrayList<>();
    List<ContainerConfigurationTemplate> containerConfigs = new ArrayList<>();
    // If a manifest list, search for the manifests matching the given platforms.
    if (manifestTemplate instanceof V22ManifestListTemplate) {
      manifestList = manifestTemplate;
      for (Platform platform : buildContext.getContainerConfiguration().getPlatforms()) {
        manifestAndDigest =
            obtainPlatformSpecificImageManifest(
                registryClient, (V22ManifestListTemplate) manifestList, platform);

        manifests.add((BuildableManifestTemplate) manifestAndDigest.getManifest());
        containerConfigs.add(
            pullContainerConfigJson(manifestAndDigest, registryClient, progressEventDispatcher));
      }

    } else {
      // V22ManifestTemplate or OciManifestTemplate
      // TODO: support OciIndexTemplate once AbstractManifestPuller starts to accept it.
      manifests = Collections.singletonList((BuildableManifestTemplate) manifestTemplate);
      containerConfigs =
          Collections.singletonList(
              pullContainerConfigJson(manifestAndDigest, registryClient, progressEventDispatcher));
    }

    cache.writeMetadata(baseImageConfig.getImage(), manifestList, manifests, containerConfigs);

    ImmutableList.Builder<Image> images = ImmutableList.builder();
    for (int i = 0; i < manifests.size(); i++) {
      images.add(JsonToImageTranslator.toImage(manifests.get(i), containerConfigs.get(i)));
    }
    return images.build();
  }

  /**
   * Looks through a manifest list for the manifest matching the {@code platform} and downloads and
   * returns the first manifest it finds.
   */
  // TODO: support OciIndexTemplate once AbstractManifestPuller starts to accept it.
  @VisibleForTesting
  ManifestAndDigest<?> obtainPlatformSpecificImageManifest(
      RegistryClient registryClient,
      V22ManifestListTemplate manifestListTemplate,
      Platform platform)
      throws IOException, RegistryException {
    EventHandlers eventHandlers = buildContext.getEventHandlers();
    String message = "Searching for architecture=%s, os=%s in the base image manifest list";
    eventHandlers.dispatch(
        LogEvent.lifecycle(String.format(message, platform.getArchitecture(), platform.getOs())));

    List<String> digests =
        manifestListTemplate.getDigestsForPlatform(platform.getArchitecture(), platform.getOs());
    if (digests.size() == 0) {
      String errorMessage =
          buildContext.getBaseImageConfiguration().getImage()
              + " is a manifest list, but the list does not contain an image for architecture=%s, "
              + "os=%s. If your intention was to specify a platform for your image, see "
              + "https://github.com/GoogleContainerTools/jib/blob/master/docs/faq.md#how-do-i-specify-a-platform-in-the-manifest-list-or-oci-index-of-a-base-image";
      eventHandlers.dispatch(
          LogEvent.error(
              String.format(errorMessage, platform.getArchitecture(), platform.getOs())));
      throw new RegistryException(errorMessage);
    }
    // TODO: pull multiple manifests (+ container configs) in parallel. (It will be simpler to pull
    // a manifest+container config pair in sequence. That is, calling pullContainerConfigJson here.)
    return registryClient.pullManifest(digests.get(0));
  }

  /**
   * Converts a JSON manifest to an {@link Image}. aaaa
   *
   * @param manifestAndDigest a manifest list and digest of a {@link Image}
   * @param registryClient to communicate with remote registry
   * @param progressEventDispatcher the {@link ProgressEventDispatcher} for emitting {@link
   *     ProgressEvent}s
   * @return {@link Image}
   * @throws IOException when an I/O exception occurs during the pulling
   * @throws LayerCountMismatchException if the manifest and configuration contain conflicting layer
   *     information
   * @throws LayerPropertyNotFoundException if adding image layers fails
   * @throws BadContainerConfigurationFormatException if the container configuration is in a bad
   *     format
   */
  private ContainerConfigurationTemplate pullContainerConfigJson(
      ManifestAndDigest<?> manifestAndDigest,
      RegistryClient registryClient,
      ProgressEventDispatcher progressEventDispatcher)
      throws IOException, LayerPropertyNotFoundException, UnknownManifestFormatException {
    BuildableManifestTemplate manifest =
        (BuildableManifestTemplate) manifestAndDigest.getManifest();
    Preconditions.checkArgument(
        manifest.getSchemaVersion() == 2,
        "Unknown manifest schema version: " + manifest.getSchemaVersion());

    EventHandlers eventHandlers = buildContext.getEventHandlers();
    eventHandlers.dispatch(
        LogEvent.lifecycle("Using base image with digest: " + manifestAndDigest.getDigest()));
    if (manifest.getContainerConfiguration() == null
        || manifest.getContainerConfiguration().getDigest() == null) {
      throw new UnknownManifestFormatException(
          "Invalid container configuration in Docker V2.2/OCI manifest: \n"
              + JsonTemplateMapper.toUtf8String(manifest));
    }

    try (ThrottledProgressEventDispatcherWrapper progressEventDispatcherWrapper =
        new ThrottledProgressEventDispatcherWrapper(
            progressEventDispatcher.newChildProducer(),
            "pull container configuration " + manifest.getContainerConfiguration().getDigest())) {
      String containerConfigString =
          Blobs.writeToString(
              registryClient.pullBlob(
                  manifest.getContainerConfiguration().getDigest(),
                  progressEventDispatcherWrapper::setProgressTarget,
                  progressEventDispatcherWrapper::dispatchProgress));
      return JsonTemplateMapper.readJson(
          containerConfigString, ContainerConfigurationTemplate.class);
    }
  }

  /**
   * Retrieves the cached base image.
   *
   * @return the cached image, if found
   * @throws IOException when an I/O exception occurs
   * @throws CacheCorruptedException if the cache is corrupted
   * @throws LayerPropertyNotFoundException if adding image layers fails
   * @throws BadContainerConfigurationFormatException if the container configuration is in a bad
   *     format
   */
  private Optional<Image> getCachedBaseImage()
      throws IOException, CacheCorruptedException, BadContainerConfigurationFormatException,
          LayerCountMismatchException {
    ImageReference baseImage = buildContext.getBaseImageConfiguration().getImage();
    Optional<ImageMetadataTemplate> metadata =
        buildContext.getBaseImageLayersCache().retrieveMetadata(baseImage);
    if (!metadata.isPresent()) {
      return Optional.empty();
    }

    List<ManifestAndConfigTemplate> manifestsAndConfigs = metadata.get().getManifestsAndConfigs();
    Verify.verify(manifestsAndConfigs.size() == 1);
    ManifestTemplate manifest = Verify.verifyNotNull(manifestsAndConfigs.get(0).getManifest());
    if (manifest instanceof V21ManifestTemplate) {
      return Optional.of(JsonToImageTranslator.toImage((V21ManifestTemplate) manifest));
    }

    return Optional.of(
        JsonToImageTranslator.toImage(
            (BuildableManifestTemplate) manifest,
            Verify.verifyNotNull(manifestsAndConfigs.get(0).getConfig())));
  }
}
