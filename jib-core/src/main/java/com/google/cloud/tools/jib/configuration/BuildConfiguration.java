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

import com.google.cloud.tools.jib.JibLogger;
import com.google.cloud.tools.jib.image.json.BuildableManifestTemplate;
import com.google.cloud.tools.jib.image.json.V22ManifestTemplate;
import com.google.cloud.tools.jib.registry.RegistryClient;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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
    @Nullable private CacheConfiguration applicationLayersCacheConfiguration;
    @Nullable private CacheConfiguration baseImageLayersCacheConfiguration;
    private boolean allowInsecureRegistries = false;
    private ImmutableList<LayerConfiguration> layerConfigurations = ImmutableList.of();
    private Class<? extends BuildableManifestTemplate> targetFormat = DEFAULT_TARGET_FORMAT;
    private String toolName = DEFAULT_TOOL_NAME;

    private JibLogger buildLogger;

    private Builder(JibLogger buildLogger) {
      this.buildLogger = buildLogger;
    }

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
     * @param applicationLayersCacheConfiguration the application layers {@link CacheConfiguration}
     * @return this
     */
    public Builder setApplicationLayersCacheConfiguration(
        @Nullable CacheConfiguration applicationLayersCacheConfiguration) {
      this.applicationLayersCacheConfiguration = applicationLayersCacheConfiguration;
      return this;
    }

    /**
     * Sets the location of the cache for storing base image layers.
     *
     * @param baseImageLayersCacheConfiguration the base image layers {@link CacheConfiguration}
     * @return this
     */
    public Builder setBaseImageLayersCacheConfiguration(
        @Nullable CacheConfiguration baseImageLayersCacheConfiguration) {
      this.baseImageLayersCacheConfiguration = baseImageLayersCacheConfiguration;
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
     * Builds a new {@link BuildConfiguration} using the parameters passed into the builder.
     *
     * @return the corresponding build configuration
     */
    public BuildConfiguration build() {
      // Validates the parameters.
      List<String> errorMessages = new ArrayList<>();
      if (baseImageConfiguration == null) {
        errorMessages.add("base image configuration is required but not set");
      }
      if (targetImageConfiguration == null) {
        errorMessages.add("target image configuration is required but not set");
      }

      switch (errorMessages.size()) {
        case 0: // No errors
          if (baseImageConfiguration == null || targetImageConfiguration == null) {
            throw new IllegalStateException("Required fields should not be null");
          }
          if (baseImageConfiguration.getImage().usesDefaultTag()) {
            buildLogger.warn(
                "Base image '"
                    + baseImageConfiguration.getImage()
                    + "' does not use a specific image digest - build may not be reproducible");
          }

          return new BuildConfiguration(
              buildLogger,
              baseImageConfiguration,
              targetImageConfiguration,
              additionalTargetImageTags,
              containerConfiguration,
              applicationLayersCacheConfiguration,
              baseImageLayersCacheConfiguration,
              targetFormat,
              allowInsecureRegistries,
              layerConfigurations,
              toolName);

        case 1:
          throw new IllegalStateException(errorMessages.get(0));

        case 2:
          throw new IllegalStateException(errorMessages.get(0) + " and " + errorMessages.get(1));

        default:
          // Should never reach here.
          throw new IllegalStateException();
      }
    }
  }

  /**
   * Creates a new {@link Builder} to build a {@link BuildConfiguration}.
   *
   * @param jibLogger the logger to log messages during build
   * @return a new {@link Builder}
   */
  public static Builder builder(JibLogger jibLogger) {
    return new Builder(jibLogger);
  }

  private final JibLogger buildLogger;
  private final ImageConfiguration baseImageConfiguration;
  private final ImageConfiguration targetImageConfiguration;
  private final ImmutableSet<String> additionalTargetImageTags;
  @Nullable private final ContainerConfiguration containerConfiguration;
  @Nullable private final CacheConfiguration applicationLayersCacheConfiguration;
  @Nullable private final CacheConfiguration baseImageLayersCacheConfiguration;
  private Class<? extends BuildableManifestTemplate> targetFormat;
  private final boolean allowInsecureRegistries;
  private final ImmutableList<LayerConfiguration> layerConfigurations;
  private final String toolName;

  /** Instantiate with {@link #builder}. */
  private BuildConfiguration(
      JibLogger buildLogger,
      ImageConfiguration baseImageConfiguration,
      ImageConfiguration targetImageConfiguration,
      ImmutableSet<String> additionalTargetImageTags,
      @Nullable ContainerConfiguration containerConfiguration,
      @Nullable CacheConfiguration applicationLayersCacheConfiguration,
      @Nullable CacheConfiguration baseImageLayersCacheConfiguration,
      Class<? extends BuildableManifestTemplate> targetFormat,
      boolean allowInsecureRegistries,
      ImmutableList<LayerConfiguration> layerConfigurations,
      String toolName) {
    this.buildLogger = buildLogger;
    this.baseImageConfiguration = baseImageConfiguration;
    this.targetImageConfiguration = targetImageConfiguration;
    this.additionalTargetImageTags = additionalTargetImageTags;
    this.containerConfiguration = containerConfiguration;
    this.applicationLayersCacheConfiguration = applicationLayersCacheConfiguration;
    this.baseImageLayersCacheConfiguration = baseImageLayersCacheConfiguration;
    this.targetFormat = targetFormat;
    this.allowInsecureRegistries = allowInsecureRegistries;
    this.layerConfigurations = layerConfigurations;
    this.toolName = toolName;
  }

  public JibLogger getBuildLogger() {
    return buildLogger;
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

  /**
   * Gets the location of the cache for storing application layers.
   *
   * @return the application layers {@link CacheConfiguration}, or {@code null} if not set
   */
  @Nullable
  public CacheConfiguration getApplicationLayersCacheConfiguration() {
    return applicationLayersCacheConfiguration;
  }

  /**
   * Gets the location of the cache for storing base image layers.
   *
   * @return the base image layers {@link CacheConfiguration}, or {@code null} if not set
   */
  @Nullable
  public CacheConfiguration getBaseImageLayersCacheConfiguration() {
    return baseImageLayersCacheConfiguration;
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
            getBuildLogger(),
            imageConfiguration.getImageRegistry(),
            imageConfiguration.getImageRepository())
        .setAllowInsecureRegistries(getAllowInsecureRegistries())
        .setUserAgentSuffix(getToolName());
  }
}
