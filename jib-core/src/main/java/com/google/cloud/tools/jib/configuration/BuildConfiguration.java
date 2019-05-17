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

import com.google.cloud.tools.jib.cache.Cache;
import com.google.cloud.tools.jib.event.EventHandlers;
import com.google.cloud.tools.jib.event.events.LogEvent;
import com.google.cloud.tools.jib.image.json.BuildableManifestTemplate;
import com.google.cloud.tools.jib.image.json.V22ManifestTemplate;
import com.google.cloud.tools.jib.registry.RegistryClient;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.annotation.Nullable;

/** Immutable configuration options for the builder process. */
public class BuildConfiguration {

  /** The default target format of the container manifest. */
  private static final Class<? extends BuildableManifestTemplate> DEFAULT_TARGET_FORMAT =
      V22ManifestTemplate.class;

  /** The default tool identifier. */
  private static final String DEFAULT_TOOL_NAME = "jib";

  /** Builds an immutable {@link BuildConfiguration}. Instantiate with {@link #builder}. */
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
    private ImmutableList<LayerConfiguration> layerConfigurations = ImmutableList.of();
    private Class<? extends BuildableManifestTemplate> targetFormat = DEFAULT_TARGET_FORMAT;
    private String toolName = DEFAULT_TOOL_NAME;
    private EventHandlers eventHandlers = EventHandlers.none();
    @Nullable private ExecutorService executorService;

    private Builder() {}

    /**
     * Sets the base image configuration.
     *
     * @param imageConfiguration the {@link ImageConfiguration} describing the base image
     * @return this
     */
    public Builder setBaseImageConfiguration(ImageConfiguration imageConfiguration) {
      this.baseImageConfiguration = imageConfiguration;
      return this;
    }

    /**
     * Sets the target image configuration.
     *
     * @param imageConfiguration the {@link ImageConfiguration} describing the target image
     * @return this
     */
    public Builder setTargetImageConfiguration(ImageConfiguration imageConfiguration) {
      this.targetImageConfiguration = imageConfiguration;
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
    public Builder setTargetFormat(Class<? extends BuildableManifestTemplate> targetFormat) {
      this.targetFormat = targetFormat;
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
     * Sets the layers to build.
     *
     * @param layerConfigurations the configurations for the layers
     * @return this
     */
    public Builder setLayerConfigurations(List<LayerConfiguration> layerConfigurations) {
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
    public Builder setExecutorService(ExecutorService executorService) {
      this.executorService = executorService;
      return this;
    }

    /**
     * Builds a new {@link BuildConfiguration} using the parameters passed into the builder.
     *
     * @return the corresponding build configuration
     * @throws IOException if an I/O exception occurs
     */
    public BuildConfiguration build() throws IOException {
      // Validates the parameters.
      List<String> missingFields = new ArrayList<>();
      if (baseImageConfiguration == null) {
        missingFields.add("base image configuration");
      }
      if (targetImageConfiguration == null) {
        missingFields.add("target image configuration");
      }
      if (baseImageLayersCacheDirectory == null) {
        missingFields.add("base image layers cache directory");
      }
      if (applicationLayersCacheDirectory == null) {
        missingFields.add("application layers cache directory");
      }
      if (executorService == null) {
        missingFields.add("executor service");
      }

      switch (missingFields.size()) {
        case 0: // No errors
          if (Preconditions.checkNotNull(baseImageConfiguration).getImage().usesDefaultTag()) {
            eventHandlers.dispatch(
                LogEvent.warn(
                    "Base image '"
                        + baseImageConfiguration.getImage()
                        + "' does not use a specific image digest - build may not be reproducible"));
          }

          return new BuildConfiguration(
              baseImageConfiguration,
              Preconditions.checkNotNull(targetImageConfiguration),
              additionalTargetImageTags,
              containerConfiguration,
              Cache.withDirectory(Preconditions.checkNotNull(baseImageLayersCacheDirectory)),
              Cache.withDirectory(Preconditions.checkNotNull(applicationLayersCacheDirectory)),
              targetFormat,
              allowInsecureRegistries,
              offline,
              layerConfigurations,
              toolName,
              eventHandlers,
              Preconditions.checkNotNull(executorService));

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
   * Creates a new {@link Builder} to build a {@link BuildConfiguration}.
   *
   * @return a new {@link Builder}
   */
  public static Builder builder() {
    return new Builder();
  }

  private final ImageConfiguration baseImageConfiguration;
  private final ImageConfiguration targetImageConfiguration;
  private final ImmutableSet<String> additionalTargetImageTags;
  @Nullable private final ContainerConfiguration containerConfiguration;
  private final Cache baseImageLayersCache;
  private final Cache applicationLayersCache;
  private Class<? extends BuildableManifestTemplate> targetFormat;
  private final boolean allowInsecureRegistries;
  private final boolean offline;
  private final ImmutableList<LayerConfiguration> layerConfigurations;
  private final String toolName;
  private final EventHandlers eventHandlers;
  private final ExecutorService executorService;

  /** Instantiate with {@link #builder}. */
  private BuildConfiguration(
      ImageConfiguration baseImageConfiguration,
      ImageConfiguration targetImageConfiguration,
      ImmutableSet<String> additionalTargetImageTags,
      @Nullable ContainerConfiguration containerConfiguration,
      Cache baseImageLayersCache,
      Cache applicationLayersCache,
      Class<? extends BuildableManifestTemplate> targetFormat,
      boolean allowInsecureRegistries,
      boolean offline,
      ImmutableList<LayerConfiguration> layerConfigurations,
      String toolName,
      EventHandlers eventHandlers,
      ExecutorService executorService) {
    this.baseImageConfiguration = baseImageConfiguration;
    this.targetImageConfiguration = targetImageConfiguration;
    this.additionalTargetImageTags = additionalTargetImageTags;
    this.containerConfiguration = containerConfiguration;
    this.baseImageLayersCache = baseImageLayersCache;
    this.applicationLayersCache = applicationLayersCache;
    this.targetFormat = targetFormat;
    this.allowInsecureRegistries = allowInsecureRegistries;
    this.offline = offline;
    this.layerConfigurations = layerConfigurations;
    this.toolName = toolName;
    this.eventHandlers = eventHandlers;
    this.executorService = executorService;
  }

  public ImageConfiguration getBaseImageConfiguration() {
    return baseImageConfiguration;
  }

  public ImageConfiguration getTargetImageConfiguration() {
    return targetImageConfiguration;
  }

  public ImmutableSet<String> getAllTargetImageTags() {
    ImmutableSet.Builder<String> allTargetImageTags =
        ImmutableSet.builderWithExpectedSize(1 + additionalTargetImageTags.size());
    allTargetImageTags.add(targetImageConfiguration.getImageTag());
    allTargetImageTags.addAll(additionalTargetImageTags);
    return allTargetImageTags.build();
  }

  @Nullable
  public ContainerConfiguration getContainerConfiguration() {
    return containerConfiguration;
  }

  public Class<? extends BuildableManifestTemplate> getTargetFormat() {
    return targetFormat;
  }

  public String getToolName() {
    return toolName;
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
   * Gets whether or not to allow insecure registries (ignoring certificate validation failure or
   * communicating over HTTP if all else fail).
   *
   * @return {@code true} if insecure connections will be allowed; {@code false} otherwise
   */
  public boolean getAllowInsecureRegistries() {
    return allowInsecureRegistries;
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
   * Gets the configurations for building the layers.
   *
   * @return the list of layer configurations
   */
  public ImmutableList<LayerConfiguration> getLayerConfigurations() {
    return layerConfigurations;
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
    return newRegistryClientFactory(targetImageConfiguration);
  }

  private RegistryClient.Factory newRegistryClientFactory(ImageConfiguration imageConfiguration) {
    return RegistryClient.factory(
            getEventHandlers(),
            imageConfiguration.getImageRegistry(),
            imageConfiguration.getImageRepository())
        .setAllowInsecureRegistries(getAllowInsecureRegistries())
        .setUserAgentSuffix(getToolName());
  }
}
