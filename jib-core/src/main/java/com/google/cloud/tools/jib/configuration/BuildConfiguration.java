/*
 * Copyright 2018 Google LLC. All rights reserved.
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
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

/** Immutable configuration options for the builder process. */
public class BuildConfiguration {

  public static class Builder {

    // All the parameters below are set to their default values.
    @Nullable private ImageConfiguration baseImageConfiguration;
    @Nullable private ImageConfiguration targetImageConfiguration;
    @Nullable private ContainerConfiguration containerConfiguration;
    @Nullable private CacheConfiguration applicationLayersCacheConfiguration;
    @Nullable private CacheConfiguration baseImageLayersCacheConfiguration;
    private boolean allowInsecureRegistries = false;
    private ImmutableList<LayerConfiguration> layerConfigurations = ImmutableList.of();
    private Class<? extends BuildableManifestTemplate> targetFormat = V22ManifestTemplate.class;
    private String createdBy = "jib";

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
     * Sets the command that created the image layers (for the "Created By" field in the container's
     * history).
     *
     * @param createdBy the field value
     * @return this
     */
    public Builder setCreatedBy(String createdBy) {
      this.createdBy = createdBy;
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
              containerConfiguration,
              applicationLayersCacheConfiguration,
              baseImageLayersCacheConfiguration,
              targetFormat,
              allowInsecureRegistries,
              layerConfigurations,
              createdBy);

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

  public static Builder builder(JibLogger buildLogger) {
    return new Builder(buildLogger);
  }

  private final JibLogger buildLogger;
  private final ImageConfiguration baseImageConfiguration;
  private final ImageConfiguration targetImageConfiguration;
  @Nullable private final ContainerConfiguration containerConfiguration;
  @Nullable private final CacheConfiguration applicationLayersCacheConfiguration;
  @Nullable private final CacheConfiguration baseImageLayersCacheConfiguration;
  private Class<? extends BuildableManifestTemplate> targetFormat;
  private final boolean allowInsecureRegistries;
  private final ImmutableList<LayerConfiguration> layerConfigurations;
  private final String createdBy;

  /** Instantiate with {@link Builder#build}. */
  private BuildConfiguration(
      JibLogger buildLogger,
      ImageConfiguration baseImageConfiguration,
      ImageConfiguration targetImageConfiguration,
      @Nullable ContainerConfiguration containerConfiguration,
      @Nullable CacheConfiguration applicationLayersCacheConfiguration,
      @Nullable CacheConfiguration baseImageLayersCacheConfiguration,
      Class<? extends BuildableManifestTemplate> targetFormat,
      boolean allowInsecureRegistries,
      ImmutableList<LayerConfiguration> layerConfigurations,
      String createdBy) {
    this.buildLogger = buildLogger;
    this.baseImageConfiguration = baseImageConfiguration;
    this.targetImageConfiguration = targetImageConfiguration;
    this.containerConfiguration = containerConfiguration;
    this.applicationLayersCacheConfiguration = applicationLayersCacheConfiguration;
    this.baseImageLayersCacheConfiguration = baseImageLayersCacheConfiguration;
    this.targetFormat = targetFormat;
    this.allowInsecureRegistries = allowInsecureRegistries;
    this.layerConfigurations = layerConfigurations;
    this.createdBy = createdBy;
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

  @Nullable
  public ContainerConfiguration getContainerConfiguration() {
    return containerConfiguration;
  }

  public Class<? extends BuildableManifestTemplate> getTargetFormat() {
    return targetFormat;
  }

  public String getCreatedBy() {
    return createdBy;
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
}
