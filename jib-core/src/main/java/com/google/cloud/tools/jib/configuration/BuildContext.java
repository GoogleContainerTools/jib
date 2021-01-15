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

package com.google.cloud.tools.jib.configuration;

import com.google.cloud.tools.jib.api.CacheDirectoryCreationException;
import com.google.cloud.tools.jib.api.LogEvent;
import com.google.cloud.tools.jib.api.buildplan.FileEntriesLayer;
import com.google.cloud.tools.jib.api.buildplan.ImageFormat;
import com.google.cloud.tools.jib.cache.Cache;
import com.google.cloud.tools.jib.event.EventHandlers;
import com.google.cloud.tools.jib.global.JibSystemProperties;
import com.google.cloud.tools.jib.http.FailoverHttpClient;
import com.google.cloud.tools.jib.image.json.BuildableManifestTemplate;
import com.google.cloud.tools.jib.image.json.OciManifestTemplate;
import com.google.cloud.tools.jib.image.json.V22ManifestTemplate;
import com.google.cloud.tools.jib.registry.RegistryClient;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.base.Verify;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ListMultimap;
import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.annotation.Nullable;

/**
 * Build context for the builder process. Includes static build configuration options as well as
 * various services for execution (such as event dispatching, thread execution service, and HTTP
 * client). Informational instances (particularly configuration options such as {@link
 * ContainerConfiguration}, {@link ImageConfiguration}, and {@link FileEntriesLayer}) held in are
 * immutable.
 */
public class BuildContext implements Closeable {

  /** The default target format of the container manifest. */
  private static final Class<? extends BuildableManifestTemplate> DEFAULT_TARGET_FORMAT =
      V22ManifestTemplate.class;

  /** The default tool identifier. */
  private static final String DEFAULT_TOOL_NAME = "jib";

  /** Builds an immutable {@link BuildContext}. Instantiate with {@link #builder}. */
  public static class Builder {

    // All the parameters below are set to their default values.
    @Nullable private ImageConfiguration baseImageConfiguration;
    @Nullable private ImageConfiguration targetImageConfiguration;
    private ImmutableSet<String> additionalTargetImageTags = ImmutableSet.of();
    @Nullable private ContainerConfiguration containerConfiguration;
    @Nullable private Path applicationLayersCacheDirectory;
    @Nullable private Path baseImageLayersCacheDirectory;
    private boolean allowInsecureRegistries = false;
    private boolean offline = false;
    private ImmutableList<FileEntriesLayer> layerConfigurations = ImmutableList.of();
    private Class<? extends BuildableManifestTemplate> targetFormat = DEFAULT_TARGET_FORMAT;
    private String toolName = DEFAULT_TOOL_NAME;
    @Nullable private String toolVersion;
    private EventHandlers eventHandlers = EventHandlers.NONE;
    @Nullable private ExecutorService executorService;
    private boolean alwaysCacheBaseImage = false;
    private ImmutableListMultimap<String, String> registryMirrors = ImmutableListMultimap.of();

    private Builder() {}

    /**
     * Sets the base image configuration.
     *
     * @param imageConfiguration the {@link ImageConfiguration} describing the base image
     * @return this
     */
    public Builder setBaseImageConfiguration(ImageConfiguration imageConfiguration) {
      baseImageConfiguration = imageConfiguration;
      return this;
    }

    /**
     * Sets the target image configuration.
     *
     * @param imageConfiguration the {@link ImageConfiguration} describing the target image
     * @return this
     */
    public Builder setTargetImageConfiguration(ImageConfiguration imageConfiguration) {
      targetImageConfiguration = imageConfiguration;
      return this;
    }

    /**
     * Sets the tags to tag the target image with (in addition to the tag in the target image
     * configuration image reference set via {@link #setTargetImageConfiguration}).
     *
     * @param tags a set of tags
     * @return this
     */
    public Builder setAdditionalTargetImageTags(Set<String> tags) {
      additionalTargetImageTags = ImmutableSet.copyOf(tags);
      return this;
    }

    /**
     * Sets configuration parameters for the container.
     *
     * @param containerConfiguration the {@link ContainerConfiguration}
     * @return this
     */
    public Builder setContainerConfiguration(ContainerConfiguration containerConfiguration) {
      this.containerConfiguration = containerConfiguration;
      return this;
    }

    /**
     * Sets the location of the cache for storing application layers.
     *
     * @param applicationLayersCacheDirectory the application layers cache directory
     * @return this
     */
    public Builder setApplicationLayersCacheDirectory(Path applicationLayersCacheDirectory) {
      this.applicationLayersCacheDirectory = applicationLayersCacheDirectory;
      return this;
    }

    /**
     * Sets the location of the cache for storing base image layers.
     *
     * @param baseImageLayersCacheDirectory the base image layers cache directory
     * @return this
     */
    public Builder setBaseImageLayersCacheDirectory(Path baseImageLayersCacheDirectory) {
      this.baseImageLayersCacheDirectory = baseImageLayersCacheDirectory;
      return this;
    }

    /**
     * Sets the target format of the container image.
     *
     * @param targetFormat the target format
     * @return this
     */
    public Builder setTargetFormat(ImageFormat targetFormat) {
      this.targetFormat =
          targetFormat == ImageFormat.Docker
              ? V22ManifestTemplate.class
              : OciManifestTemplate.class;
      return this;
    }

    /**
     * Sets whether or not to allow communication over HTTP (as opposed to HTTPS).
     *
     * @param allowInsecureRegistries if {@code true}, insecure connections will be allowed
     * @return this
     */
    public Builder setAllowInsecureRegistries(boolean allowInsecureRegistries) {
      this.allowInsecureRegistries = allowInsecureRegistries;
      return this;
    }

    /**
     * Sets whether or not to perform the build in offline mode.
     *
     * @param offline if {@code true}, the build will run in offline mode
     * @return this
     */
    public Builder setOffline(boolean offline) {
      this.offline = offline;
      return this;
    }

    /**
     * Controls the optimization which skips downloading base image layers that exist in a target
     * registry. If the user does not set this property then read as false.
     *
     * @param alwaysCacheBaseImage if {@code true}, base image layers are always pulled and cached.
     *     If {@code false}, base image layers will not be pulled/cached if they already exist on
     *     the target registry.
     * @return this
     */
    public Builder setAlwaysCacheBaseImage(boolean alwaysCacheBaseImage) {
      this.alwaysCacheBaseImage = alwaysCacheBaseImage;
      return this;
    }

    /**
     * Sets the layers to build.
     *
     * @param layerConfigurations the configurations for the layers
     * @return this
     */
    public Builder setLayerConfigurations(List<FileEntriesLayer> layerConfigurations) {
      this.layerConfigurations = ImmutableList.copyOf(layerConfigurations);
      return this;
    }

    /**
     * Sets the name of the tool that is executing the build.
     *
     * @param toolName the tool name
     * @return this
     */
    public Builder setToolName(String toolName) {
      this.toolName = toolName;
      return this;
    }

    /**
     * Sets the version of the tool that is executing the build.
     *
     * @param toolVersion the tool version
     * @return this
     */
    public Builder setToolVersion(@Nullable String toolVersion) {
      this.toolVersion = toolVersion;
      return this;
    }

    /**
     * Sets the {@link EventHandlers} to dispatch events with.
     *
     * @param eventHandlers the {@link EventHandlers}
     * @return this
     */
    public Builder setEventHandlers(EventHandlers eventHandlers) {
      this.eventHandlers = eventHandlers;
      return this;
    }

    /**
     * Sets the {@link ExecutorService} Jib executes on. By default, Jib uses {@link
     * Executors#newCachedThreadPool}.
     *
     * @param executorService the {@link ExecutorService}
     * @return this
     */
    public Builder setExecutorService(@Nullable ExecutorService executorService) {
      this.executorService = executorService;
      return this;
    }

    /**
     * Sets the registry mirrors.
     *
     * @param registryMirrors registry mirrors
     * @return this
     */
    public Builder setRegistryMirrors(ListMultimap<String, String> registryMirrors) {
      this.registryMirrors = ImmutableListMultimap.copyOf(registryMirrors);
      return this;
    }

    /**
     * Builds a new {@link BuildContext} using the parameters passed into the builder.
     *
     * @return the corresponding build context
     * @throws CacheDirectoryCreationException if I/O exception occurs when creating cache directory
     */
    public BuildContext build() throws CacheDirectoryCreationException {
      // Validates the parameters.
      List<String> missingFields = new ArrayList<>();
      if (baseImageConfiguration == null) {
        missingFields.add("base image configuration");
      }
      if (targetImageConfiguration == null) {
        missingFields.add("target image configuration");
      }
      if (containerConfiguration == null) {
        missingFields.add("container configuration");
      }
      if (baseImageLayersCacheDirectory == null) {
        missingFields.add("base image layers cache directory");
      }
      if (applicationLayersCacheDirectory == null) {
        missingFields.add("application layers cache directory");
      }

      switch (missingFields.size()) {
        case 0: // No errors
          Preconditions.checkNotNull(baseImageConfiguration);
          if (!baseImageConfiguration.getImage().getDigest().isPresent()
              && !baseImageConfiguration.getImage().isScratch()) {
            eventHandlers.dispatch(
                LogEvent.warn(
                    "Base image '"
                        + baseImageConfiguration.getImage()
                        + "' does not use a specific image digest - build may not be reproducible"));
          }

          return new BuildContext(
              baseImageConfiguration,
              Verify.verifyNotNull(targetImageConfiguration),
              additionalTargetImageTags,
              Verify.verifyNotNull(containerConfiguration),
              Cache.withDirectory(Preconditions.checkNotNull(baseImageLayersCacheDirectory)),
              Cache.withDirectory(Preconditions.checkNotNull(applicationLayersCacheDirectory)),
              targetFormat,
              offline,
              layerConfigurations,
              toolName,
              toolVersion,
              eventHandlers,
              // TODO: try setting global User-Agent: here
              new FailoverHttpClient(
                  allowInsecureRegistries,
                  JibSystemProperties.sendCredentialsOverHttp(),
                  eventHandlers::dispatch),
              executorService == null ? Executors.newCachedThreadPool() : executorService,
              executorService == null, // shutDownExecutorService
              alwaysCacheBaseImage,
              registryMirrors);

        case 1:
          throw new IllegalStateException(missingFields.get(0) + " is required but not set");

        case 2:
          throw new IllegalStateException(
              missingFields.get(0) + " and " + missingFields.get(1) + " are required but not set");

        default:
          missingFields.add("and " + missingFields.remove(missingFields.size() - 1));
          StringJoiner errorMessage = new StringJoiner(", ", "", " are required but not set");
          for (String missingField : missingFields) {
            errorMessage.add(missingField);
          }
          throw new IllegalStateException(errorMessage.toString());
      }
    }

    @Nullable
    @VisibleForTesting
    Path getBaseImageLayersCacheDirectory() {
      return baseImageLayersCacheDirectory;
    }

    @Nullable
    @VisibleForTesting
    Path getApplicationLayersCacheDirectory() {
      return applicationLayersCacheDirectory;
    }
  }

  /**
   * Creates a new {@link Builder} to build a {@link BuildContext}.
   *
   * @return a new {@link Builder}
   */
  public static Builder builder() {
    return new Builder();
  }

  private final ImageConfiguration baseImageConfiguration;
  private final ImageConfiguration targetImageConfiguration;
  private final ImmutableSet<String> additionalTargetImageTags;
  private final ContainerConfiguration containerConfiguration;
  private final Cache baseImageLayersCache;
  private final Cache applicationLayersCache;
  private Class<? extends BuildableManifestTemplate> targetFormat;
  private final boolean offline;
  private final ImmutableList<FileEntriesLayer> layerConfigurations;
  private final String toolName;
  @Nullable private final String toolVersion;
  private final EventHandlers eventHandlers;
  private final FailoverHttpClient httpClient;
  private final ExecutorService executorService;
  private final boolean shutDownExecutorService;
  private final boolean alwaysCacheBaseImage;
  private final ImmutableListMultimap<String, String> registryMirrors;

  /** Instantiate with {@link #builder}. */
  private BuildContext(
      ImageConfiguration baseImageConfiguration,
      ImageConfiguration targetImageConfiguration,
      ImmutableSet<String> additionalTargetImageTags,
      ContainerConfiguration containerConfiguration,
      Cache baseImageLayersCache,
      Cache applicationLayersCache,
      Class<? extends BuildableManifestTemplate> targetFormat,
      boolean offline,
      ImmutableList<FileEntriesLayer> layerConfigurations,
      String toolName,
      @Nullable String toolVersion,
      EventHandlers eventHandlers,
      FailoverHttpClient httpClient,
      ExecutorService executorService,
      boolean shutDownExecutorService,
      boolean alwaysCacheBaseImage,
      ImmutableListMultimap<String, String> registryMirrors) {
    this.baseImageConfiguration = baseImageConfiguration;
    this.targetImageConfiguration = targetImageConfiguration;
    this.additionalTargetImageTags = additionalTargetImageTags;
    this.containerConfiguration = containerConfiguration;
    this.baseImageLayersCache = baseImageLayersCache;
    this.applicationLayersCache = applicationLayersCache;
    this.targetFormat = targetFormat;
    this.offline = offline;
    this.layerConfigurations = layerConfigurations;
    this.toolName = toolName;
    this.toolVersion = toolVersion;
    this.eventHandlers = eventHandlers;
    this.httpClient = httpClient;
    this.executorService = executorService;
    this.shutDownExecutorService = shutDownExecutorService;
    this.alwaysCacheBaseImage = alwaysCacheBaseImage;
    this.registryMirrors = registryMirrors;
  }

  public ImageConfiguration getBaseImageConfiguration() {
    return baseImageConfiguration;
  }

  public ImageConfiguration getTargetImageConfiguration() {
    return targetImageConfiguration;
  }

  /**
   * Returns all image tags configured for this build.
   *
   * @return the set of image tags configured for this build
   */
  public ImmutableSet<String> getAllTargetImageTags() {
    ImmutableSet.Builder<String> allTargetImageTags =
        ImmutableSet.builderWithExpectedSize(1 + additionalTargetImageTags.size());
    allTargetImageTags.add(targetImageConfiguration.getImageQualifier());
    allTargetImageTags.addAll(additionalTargetImageTags);
    return allTargetImageTags.build();
  }

  public ContainerConfiguration getContainerConfiguration() {
    return containerConfiguration;
  }

  public Class<? extends BuildableManifestTemplate> getTargetFormat() {
    return targetFormat;
  }

  public String getToolName() {
    return toolName;
  }

  @Nullable
  public String getToolVersion() {
    return toolVersion;
  }

  public EventHandlers getEventHandlers() {
    return eventHandlers;
  }

  public ExecutorService getExecutorService() {
    return executorService;
  }

  /**
   * Gets the {@link Cache} for base image layers.
   *
   * @return the {@link Cache} for base image layers
   */
  public Cache getBaseImageLayersCache() {
    return baseImageLayersCache;
  }

  /**
   * Gets the {@link Cache} for application layers.
   *
   * @return the {@link Cache} for application layers
   */
  public Cache getApplicationLayersCache() {
    return applicationLayersCache;
  }

  /**
   * Gets whether or not to run the build in offline mode.
   *
   * @return {@code true} if the build will run in offline mode; {@code false} otherwise
   */
  public boolean isOffline() {
    return offline;
  }

  /**
   * Gets whether or not to force caching the base images.
   *
   * @return {@code true} if the user wants to force the build to always pull the image layers.
   */
  public boolean getAlwaysCacheBaseImage() {
    return alwaysCacheBaseImage;
  }

  /**
   * Gets the configurations for building the layers.
   *
   * @return the list of layer configurations
   */
  public ImmutableList<FileEntriesLayer> getLayerConfigurations() {
    return layerConfigurations;
  }

  /**
   * Gets the registry mirrors.
   *
   * @return the registry mirrors
   */
  public ImmutableListMultimap<String, String> getRegistryMirrors() {
    return registryMirrors;
  }

  /**
   * Creates a new {@link RegistryClient.Factory} for the base image with fields from the build
   * configuration.
   *
   * @return a new {@link RegistryClient.Factory}
   */
  public RegistryClient.Factory newBaseImageRegistryClientFactory() {
    return newRegistryClientFactory(baseImageConfiguration);
  }

  /**
   * Creates a new {@link RegistryClient.Factory} for the target image with fields from the build
   * configuration.
   *
   * @return a new {@link RegistryClient.Factory}
   */
  public RegistryClient.Factory newTargetImageRegistryClientFactory() {
    // if base and target are on the same registry, try enabling cross-repository mounts
    if (baseImageConfiguration
        .getImageRegistry()
        .equals(targetImageConfiguration.getImageRegistry())) {
      return RegistryClient.factory(
              getEventHandlers(),
              targetImageConfiguration.getImageRegistry(),
              targetImageConfiguration.getImageRepository(),
              baseImageConfiguration.getImageRepository(),
              httpClient)
          .setUserAgent(makeUserAgent());
    }
    return newRegistryClientFactory(targetImageConfiguration);
  }

  private RegistryClient.Factory newRegistryClientFactory(ImageConfiguration imageConfiguration) {
    return RegistryClient.factory(
            getEventHandlers(),
            imageConfiguration.getImageRegistry(),
            imageConfiguration.getImageRepository(),
            httpClient)
        .setUserAgent(makeUserAgent());
  }

  @Override
  public void close() throws IOException {
    if (shutDownExecutorService) {
      executorService.shutdown();
    }
    httpClient.shutDown();
  }

  /**
   * The {@code User-Agent} is in the form of {@code jib <toolVersion> <toolName>}. For example:
   * {@code jib 0.9.0 jib-maven-plugin}.
   *
   * @return the {@code User-Agent} header to send. The {@code User-Agent} can be disabled by
   *     setting the system property variable {@code _JIB_DISABLE_USER_AGENT} to any non-empty
   *     string.
   */
  @VisibleForTesting
  String makeUserAgent() {
    if (!JibSystemProperties.isUserAgentEnabled()) {
      return "";
    }

    StringBuilder userAgentBuilder = new StringBuilder("jib");
    userAgentBuilder.append(" ").append(toolVersion);
    userAgentBuilder.append(" ").append(toolName);
    if (!Strings.isNullOrEmpty(System.getProperty(JibSystemProperties.UPSTREAM_CLIENT))) {
      userAgentBuilder.append(" ").append(System.getProperty(JibSystemProperties.UPSTREAM_CLIENT));
    }
    return userAgentBuilder.toString();
  }
}
