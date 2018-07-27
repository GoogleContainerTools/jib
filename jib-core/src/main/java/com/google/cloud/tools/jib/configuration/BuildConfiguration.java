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

import com.google.cloud.tools.jib.builder.BuildLogger;
import com.google.cloud.tools.jib.image.ImageReference;
import com.google.cloud.tools.jib.image.json.BuildableManifestTemplate;
import com.google.cloud.tools.jib.registry.credentials.RegistryCredentials;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import javax.lang.model.SourceVersion;

/** Immutable configuration options for the builder process. */
public class BuildConfiguration {

  public static class Builder {

    // All the parameters below are set to their default values.
    private ImageConfiguration baseImageConfiguration = new ImageConfiguration();
    private ImageConfiguration targetImageConfiguration = new ImageConfiguration();
    private ContainerConfiguration containerConfiguration = new ContainerConfiguration();

    @Nullable private CacheConfiguration applicationLayersCacheConfiguration;
    @Nullable private CacheConfiguration baseImageLayersCacheConfiguration;
    private boolean allowInsecureRegistries = false;
    private ImmutableList<LayerConfiguration> layerConfigurations = ImmutableList.of();

    private BuildLogger buildLogger;

    private Builder(BuildLogger buildLogger) {
      this.buildLogger = buildLogger;
    }

    public Builder setBaseImageConfiguration(ImageConfiguration imageReference) {
      this.baseImageConfiguration = imageReference;
      return this;
    }

    public Builder setTargetImageConfiguration(ImageConfiguration imageReference) {
      this.targetImageConfiguration = imageReference;
      return this;
    }

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

    /** @return the corresponding build configuration */
    public BuildConfiguration build() {
      // Validates the parameters.
      List<String> errorMessages = new ArrayList<>();
      ImageReference baseImageReference = baseImageConfiguration.getImage();
      ImageReference targetImageReference = targetImageConfiguration.getImage();
      if (baseImageReference == null) {
        errorMessages.add("base image is required but not set");
      }
      if (targetImageReference == null) {
        errorMessages.add("target image is required but not set");
      }

      switch (errorMessages.size()) {
        case 0: // No errors
          if (baseImageReference == null || targetImageReference == null) {
            throw new IllegalStateException("Required fields should not be null");
          }
          if (baseImageReference.usesDefaultTag()) {
            buildLogger.warn(
                "Base image '"
                    + baseImageReference
                    + "' does not use a specific image digest - build may not be reproducible");
          }
          return new BuildConfiguration(
              buildLogger,
              baseImageReference,
              baseImageConfiguration.getCredentialHelper(),
              baseImageConfiguration.getKnownRegistryCredentials(),
              targetImageReference,
              targetImageConfiguration.getCredentialHelper(),
              targetImageConfiguration.getKnownRegistryCredentials(),
              containerConfiguration.getCreationTime(),
              containerConfiguration.getEntrypoint(),
              containerConfiguration.getProgramArguments(),
              containerConfiguration.getEnvironmentMap(),
              containerConfiguration.getExposedPorts(),
              containerConfiguration.getTargetFormat(),
              applicationLayersCacheConfiguration,
              baseImageLayersCacheConfiguration,
              allowInsecureRegistries,
              layerConfigurations);

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
   * @param className the class name to check
   * @return {@code true} if {@code className} is a valid Java class name; {@code false} otherwise
   */
  public static boolean isValidJavaClass(String className) {
    for (String part : Splitter.on('.').split(className)) {
      if (!SourceVersion.isIdentifier(part)) {
        return false;
      }
    }
    return true;
  }

  public static Builder builder(BuildLogger buildLogger) {
    return new Builder(buildLogger);
  }

  private final BuildLogger buildLogger;
  private final ImageReference baseImageReference;
  @Nullable private final String baseImageCredentialHelperName;
  @Nullable private final RegistryCredentials knownBaseRegistryCredentials;
  private final ImageReference targetImageReference;
  @Nullable private final String targetImageCredentialHelperName;
  @Nullable private final RegistryCredentials knownTargetRegistryCredentials;
  private final Instant creationTime;
  @Nullable private final ImmutableList<String> programArguments;
  @Nullable private final ImmutableMap<String, String> environmentMap;
  @Nullable private final ImmutableList<Port> exposedPorts;
  private final Class<? extends BuildableManifestTemplate> targetFormat;
  @Nullable private final CacheConfiguration applicationLayersCacheConfiguration;
  @Nullable private final CacheConfiguration baseImageLayersCacheConfiguration;
  private final boolean allowInsecureRegistries;
  private final ImmutableList<LayerConfiguration> layerConfigurations;
  @Nullable private final ImmutableList<String> entrypoint;

  /** Instantiate with {@link Builder#build}. */
  private BuildConfiguration(
      BuildLogger buildLogger,
      ImageReference baseImageReference,
      @Nullable String baseImageCredentialHelperName,
      @Nullable RegistryCredentials knownBaseRegistryCredentials,
      ImageReference targetImageReference,
      @Nullable String targetImageCredentialHelperName,
      @Nullable RegistryCredentials knownTargetRegistryCredentials,
      Instant creationTime,
      @Nullable ImmutableList<String> entrypoint,
      @Nullable ImmutableList<String> programArguments,
      @Nullable ImmutableMap<String, String> environmentMap,
      @Nullable ImmutableList<Port> exposedPorts,
      Class<? extends BuildableManifestTemplate> targetFormat,
      @Nullable CacheConfiguration applicationLayersCacheConfiguration,
      @Nullable CacheConfiguration baseImageLayersCacheConfiguration,
      boolean allowInsecureRegistries,
      ImmutableList<LayerConfiguration> layerConfigurations) {
    this.buildLogger = buildLogger;
    this.baseImageReference = baseImageReference;
    this.baseImageCredentialHelperName = baseImageCredentialHelperName;
    this.knownBaseRegistryCredentials = knownBaseRegistryCredentials;
    this.targetImageReference = targetImageReference;
    this.targetImageCredentialHelperName = targetImageCredentialHelperName;
    this.knownTargetRegistryCredentials = knownTargetRegistryCredentials;
    this.creationTime = creationTime;
    this.programArguments = programArguments;
    this.environmentMap = environmentMap;
    this.exposedPorts = exposedPorts;
    this.targetFormat = targetFormat;
    this.applicationLayersCacheConfiguration = applicationLayersCacheConfiguration;
    this.baseImageLayersCacheConfiguration = baseImageLayersCacheConfiguration;
    this.allowInsecureRegistries = allowInsecureRegistries;
    this.layerConfigurations = layerConfigurations;
    this.entrypoint = entrypoint;
  }

  public BuildLogger getBuildLogger() {
    return buildLogger;
  }

  public ImageReference getBaseImageReference() {
    return baseImageReference;
  }

  public String getBaseImageRegistry() {
    return baseImageReference.getRegistry();
  }

  public String getBaseImageRepository() {
    return baseImageReference.getRepository();
  }

  public String getBaseImageTag() {
    return baseImageReference.getTag();
  }

  @Nullable
  public String getBaseImageCredentialHelperName() {
    return baseImageCredentialHelperName;
  }

  @Nullable
  public RegistryCredentials getKnownBaseRegistryCredentials() {
    return knownBaseRegistryCredentials;
  }

  public ImageReference getTargetImageReference() {
    return targetImageReference;
  }

  public String getTargetImageRegistry() {
    return targetImageReference.getRegistry();
  }

  public String getTargetImageRepository() {
    return targetImageReference.getRepository();
  }

  public String getTargetImageTag() {
    return targetImageReference.getTag();
  }

  @Nullable
  public String getTargetImageCredentialHelperName() {
    return targetImageCredentialHelperName;
  }

  @Nullable
  public RegistryCredentials getKnownTargetRegistryCredentials() {
    return knownTargetRegistryCredentials;
  }

  public Instant getCreationTime() {
    return creationTime;
  }

  /**
   * Gets the container entrypoint.
   *
   * @return the list of entrypoint tokens, or {@code null} if not set
   */
  @Nullable
  public ImmutableList<String> getEntrypoint() {
    return entrypoint;
  }

  /**
   * Gets the arguments to pass to the entrypoint.
   *
   * @return the list of arguments, or {@code null} if not set
   */
  @Nullable
  public ImmutableList<String> getProgramArguments() {
    return programArguments;
  }

  /**
   * Gets the map from environment variable names to values for the container.
   *
   * @return the map of environment variables, or {@code null} if not set
   */
  @Nullable
  public ImmutableMap<String, String> getEnvironment() {
    return environmentMap;
  }

  /**
   * Gets the ports to expose on the container.
   *
   * @return the list of exposed ports, or {@code null} if not set
   */
  @Nullable
  public ImmutableList<Port> getExposedPorts() {
    return exposedPorts;
  }

  public Class<? extends BuildableManifestTemplate> getTargetFormat() {
    return targetFormat;
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
