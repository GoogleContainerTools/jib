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
import com.google.cloud.tools.jib.api.DescriptorDigest;
import com.google.cloud.tools.jib.api.ImageReference;
import com.google.cloud.tools.jib.api.InsecureRegistryException;
import com.google.cloud.tools.jib.api.LogEvent;
import com.google.cloud.tools.jib.api.RegistryException;
import com.google.cloud.tools.jib.api.RegistryUnauthorizedException;
import com.google.cloud.tools.jib.async.AsyncStep;
import com.google.cloud.tools.jib.async.NonBlockingSteps;
import com.google.cloud.tools.jib.blob.Blobs;
import com.google.cloud.tools.jib.builder.ProgressEventDispatcher;
import com.google.cloud.tools.jib.builder.TimerEventDispatcher;
import com.google.cloud.tools.jib.builder.steps.PullBaseImageStep.BaseImageWithAuthorization;
import com.google.cloud.tools.jib.cache.CacheCorruptedException;
import com.google.cloud.tools.jib.configuration.BuildConfiguration;
import com.google.cloud.tools.jib.configuration.ImageConfiguration;
import com.google.cloud.tools.jib.event.EventHandlers;
import com.google.cloud.tools.jib.event.events.ProgressEvent;
import com.google.cloud.tools.jib.http.Authorization;
import com.google.cloud.tools.jib.image.Image;
import com.google.cloud.tools.jib.image.LayerCountMismatchException;
import com.google.cloud.tools.jib.image.LayerPropertyNotFoundException;
import com.google.cloud.tools.jib.image.json.BadContainerConfigurationFormatException;
import com.google.cloud.tools.jib.image.json.BuildableManifestTemplate;
import com.google.cloud.tools.jib.image.json.ContainerConfigurationTemplate;
import com.google.cloud.tools.jib.image.json.JsonToImageTranslator;
import com.google.cloud.tools.jib.image.json.ManifestAndConfig;
import com.google.cloud.tools.jib.image.json.ManifestTemplate;
import com.google.cloud.tools.jib.image.json.UnknownManifestFormatException;
import com.google.cloud.tools.jib.image.json.V21ManifestTemplate;
import com.google.cloud.tools.jib.json.JsonTemplateMapper;
import com.google.cloud.tools.jib.registry.RegistryAuthenticator;
import com.google.cloud.tools.jib.registry.RegistryClient;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nullable;

/** Pulls the base image manifest. */
class PullBaseImageStep
    implements AsyncStep<BaseImageWithAuthorization>, Callable<BaseImageWithAuthorization> {

  private static final String DESCRIPTION = "Pulling base image manifest";

  /** Structure for the result returned by this step. */
  static class BaseImageWithAuthorization {

    private final Image baseImage;
    private final @Nullable Authorization baseImageAuthorization;

    @VisibleForTesting
    BaseImageWithAuthorization(Image baseImage, @Nullable Authorization baseImageAuthorization) {
      this.baseImage = baseImage;
      this.baseImageAuthorization = baseImageAuthorization;
    }

    Image getBaseImage() {
      return baseImage;
    }

    @Nullable
    Authorization getBaseImageAuthorization() {
      return baseImageAuthorization;
    }
  }

  private final BuildConfiguration buildConfiguration;
  private final ProgressEventDispatcher.Factory progressEventDispatcherFactory;

  private final ListenableFuture<BaseImageWithAuthorization> listenableFuture;

  PullBaseImageStep(
      ListeningExecutorService listeningExecutorService,
      BuildConfiguration buildConfiguration,
      ProgressEventDispatcher.Factory progressEventDispatcherFactory) {
    this.buildConfiguration = buildConfiguration;
    this.progressEventDispatcherFactory = progressEventDispatcherFactory;

    listenableFuture = listeningExecutorService.submit(this);
  }

  @Override
  public ListenableFuture<BaseImageWithAuthorization> getFuture() {
    return listenableFuture;
  }

  @Override
  public BaseImageWithAuthorization call()
      throws IOException, RegistryException, LayerPropertyNotFoundException,
          LayerCountMismatchException, ExecutionException, BadContainerConfigurationFormatException,
          CacheCorruptedException {
    EventHandlers eventHandlers = buildConfiguration.getEventHandlers();
    // Skip this step if this is a scratch image
    ImageConfiguration baseImageConfiguration = buildConfiguration.getBaseImageConfiguration();
    if (baseImageConfiguration.getImage().isScratch()) {
      eventHandlers.dispatch(LogEvent.progress("Getting scratch base image..."));
      return new BaseImageWithAuthorization(
          Image.builder(buildConfiguration.getTargetFormat()).build(), null);
    }

    eventHandlers.dispatch(
        LogEvent.progress(
            "Getting base image "
                + buildConfiguration.getBaseImageConfiguration().getImage()
                + "..."));

    if (buildConfiguration.isOffline()) {
      return new BaseImageWithAuthorization(pullBaseImageOffline(), null);
    }

    try (ProgressEventDispatcher progressEventDispatcher =
            progressEventDispatcherFactory.create("pulling base image manifest", 2);
        TimerEventDispatcher ignored =
            new TimerEventDispatcher(buildConfiguration.getEventHandlers(), DESCRIPTION)) {
      // First, try with no credentials.
      try {
        return new BaseImageWithAuthorization(pullBaseImage(null, progressEventDispatcher), null);

      } catch (RegistryUnauthorizedException ex) {
        eventHandlers.dispatch(
            LogEvent.lifecycle(
                "The base image requires auth. Trying again for "
                    + buildConfiguration.getBaseImageConfiguration().getImage()
                    + "..."));

        // If failed, then, retrieve base registry credentials and try with retrieved credentials.
        // TODO: Refactor the logic in RetrieveRegistryCredentialsStep out to
        // registry.credentials.RegistryCredentialsRetriever to avoid this direct executor hack.
        ListeningExecutorService directExecutorService = MoreExecutors.newDirectExecutorService();
        RetrieveRegistryCredentialsStep retrieveBaseRegistryCredentialsStep =
            RetrieveRegistryCredentialsStep.forBaseImage(
                directExecutorService,
                buildConfiguration,
                progressEventDispatcher.newChildProducer());

        Credential registryCredential = NonBlockingSteps.get(retrieveBaseRegistryCredentialsStep);
        Authorization registryAuthorization =
            registryCredential == null || registryCredential.isOAuth2RefreshToken()
                ? null
                : Authorization.fromBasicCredentials(
                    registryCredential.getUsername(), registryCredential.getPassword());

        try {
          return new BaseImageWithAuthorization(
              pullBaseImage(registryAuthorization, progressEventDispatcher), registryAuthorization);

        } catch (RegistryUnauthorizedException registryUnauthorizedException) {
          // The registry requires us to authenticate using the Docker Token Authentication.
          // See https://docs.docker.com/registry/spec/auth/token
          try {
            RegistryAuthenticator registryAuthenticator =
                buildConfiguration
                    .newBaseImageRegistryClientFactory()
                    .newRegistryClient()
                    .getRegistryAuthenticator();
            if (registryAuthenticator != null) {
              Authorization pullAuthorization =
                  registryAuthenticator.authenticatePull(registryCredential);

              return new BaseImageWithAuthorization(
                  pullBaseImage(pullAuthorization, progressEventDispatcher), pullAuthorization);
            }

          } catch (InsecureRegistryException insecureRegistryException) {
            // Cannot skip certificate validation or use HTTP; fall through.
          }
          eventHandlers.dispatch(
              LogEvent.error(
                  "Failed to retrieve authentication challenge for registry that required token authentication"));
          throw registryUnauthorizedException;
        }
      }
    }
  }

  /**
   * Pulls the base image.
   *
   * @param registryAuthorization authentication credentials to possibly use
   * @param progressEventDispatcher the {@link ProgressEventDispatcher} for emitting {@link
   *     ProgressEvent}s
   * @return the pulled image
   * @throws IOException when an I/O exception occurs during the pulling
   * @throws RegistryException if communicating with the registry caused a known error
   * @throws LayerCountMismatchException if the manifest and configuration contain conflicting layer
   *     information
   * @throws LayerPropertyNotFoundException if adding image layers fails
   * @throws BadContainerConfigurationFormatException if the container configuration is in a bad
   *     format
   */
  private Image pullBaseImage(
      @Nullable Authorization registryAuthorization,
      ProgressEventDispatcher progressEventDispatcher)
      throws IOException, RegistryException, LayerPropertyNotFoundException,
          LayerCountMismatchException, BadContainerConfigurationFormatException {
    RegistryClient registryClient =
        buildConfiguration
            .newBaseImageRegistryClientFactory()
            .setAuthorization(registryAuthorization)
            .newRegistryClient();

    ManifestTemplate manifestTemplate =
        registryClient.pullManifest(buildConfiguration.getBaseImageConfiguration().getImageTag());

    // TODO: Make schema version be enum.
    switch (manifestTemplate.getSchemaVersion()) {
      case 1:
        V21ManifestTemplate v21ManifestTemplate = (V21ManifestTemplate) manifestTemplate;
        buildConfiguration
            .getBaseImageLayersCache()
            .writeMetadata(
                buildConfiguration.getBaseImageConfiguration().getImage(), v21ManifestTemplate);
        return JsonToImageTranslator.toImage(v21ManifestTemplate);

      case 2:
        BuildableManifestTemplate buildableManifestTemplate =
            (BuildableManifestTemplate) manifestTemplate;
        if (buildableManifestTemplate.getContainerConfiguration() == null
            || buildableManifestTemplate.getContainerConfiguration().getDigest() == null) {
          throw new UnknownManifestFormatException(
              "Invalid container configuration in Docker V2.2/OCI manifest: \n"
                  + JsonTemplateMapper.toUtf8String(buildableManifestTemplate));
        }

        DescriptorDigest containerConfigurationDigest =
            buildableManifestTemplate.getContainerConfiguration().getDigest();

        try (ThrottledProgressEventDispatcherWrapper progressEventDispatcherWrapper =
            new ThrottledProgressEventDispatcherWrapper(
                progressEventDispatcher.newChildProducer(),
                "pull container configuration " + containerConfigurationDigest)) {
          String containerConfigurationString =
              Blobs.writeToString(
                  registryClient.pullBlob(
                      containerConfigurationDigest,
                      progressEventDispatcherWrapper::setProgressTarget,
                      progressEventDispatcherWrapper::dispatchProgress));

          ContainerConfigurationTemplate containerConfigurationTemplate =
              JsonTemplateMapper.readJson(
                  containerConfigurationString, ContainerConfigurationTemplate.class);
          buildConfiguration
              .getBaseImageLayersCache()
              .writeMetadata(
                  buildConfiguration.getBaseImageConfiguration().getImage(),
                  buildableManifestTemplate,
                  containerConfigurationTemplate);
          return JsonToImageTranslator.toImage(
              buildableManifestTemplate, containerConfigurationTemplate);
        }
    }

    throw new IllegalStateException("Unknown manifest schema version");
  }

  /**
   * Retrieves the cached base image.
   *
   * @return the cached image
   * @throws IOException when an I/O exception occurs
   * @throws CacheCorruptedException if the cache is corrupted
   * @throws LayerPropertyNotFoundException if adding image layers fails
   * @throws BadContainerConfigurationFormatException if the container configuration is in a bad
   *     format
   */
  private Image pullBaseImageOffline()
      throws IOException, CacheCorruptedException, BadContainerConfigurationFormatException,
          LayerCountMismatchException {
    ImageReference baseImage = buildConfiguration.getBaseImageConfiguration().getImage();
    Optional<ManifestAndConfig> metadata =
        buildConfiguration.getBaseImageLayersCache().retrieveMetadata(baseImage);
    if (!metadata.isPresent()) {
      throw new IOException(
          "Cannot run Jib in offline mode; " + baseImage + " not found in local Jib cache");
    }

    ManifestTemplate manifestTemplate = metadata.get().getManifest();
    if (manifestTemplate instanceof V21ManifestTemplate) {
      return JsonToImageTranslator.toImage((V21ManifestTemplate) manifestTemplate);
    }

    ContainerConfigurationTemplate configurationTemplate =
        metadata.get().getConfig().orElseThrow(IllegalStateException::new);
    return JsonToImageTranslator.toImage(
        (BuildableManifestTemplate) manifestTemplate, configurationTemplate);
  }
}
