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

package com.google.cloud.tools.jib.builder;

import com.google.cloud.tools.jib.configuration.CacheConfiguration;
import com.google.cloud.tools.jib.image.ImageReference;
import com.google.cloud.tools.jib.image.json.BuildableManifestTemplate;
import com.google.cloud.tools.jib.image.json.V22ManifestTemplate;
import com.google.cloud.tools.jib.registry.credentials.RegistryCredentials;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import javax.lang.model.SourceVersion;

/** Immutable configuration options for the builder process. */
public class BuildConfiguration {

  public static class Builder {

    // All the parameters below are set to their default values.
    @Nullable private ImageReference baseImageReference;
    @Nullable private String baseImageCredentialHelperName;
    @Nullable private RegistryCredentials knownBaseRegistryCredentials;
    @Nullable private ImageReference targetImageReference;
    @Nullable private String targetImageCredentialHelperName;
    @Nullable private RegistryCredentials knownTargetRegistryCredentials;
    @Nullable private String mainClass;
    private ImmutableList<String> javaArguments = ImmutableList.of();
    private ImmutableList<String> jvmFlags = ImmutableList.of();
    private ImmutableMap<String, String> environmentMap = ImmutableMap.of();
    private ImmutableList<String> exposedPorts = ImmutableList.of();
    private Class<? extends BuildableManifestTemplate> targetFormat = V22ManifestTemplate.class;
    @Nullable private CacheConfiguration applicationLayersCacheConfiguration;
    @Nullable private CacheConfiguration baseImageLayersCacheConfiguration;
    private boolean allowHttp = false;

    private BuildLogger buildLogger;

    private Builder(BuildLogger buildLogger) {
      this.buildLogger = buildLogger;
    }

    public Builder setBaseImage(@Nullable ImageReference imageReference) {
      baseImageReference = imageReference;
      return this;
    }

    public Builder setTargetImage(@Nullable ImageReference imageReference) {
      targetImageReference = imageReference;
      return this;
    }

    public Builder setBaseImageCredentialHelperName(@Nullable String credentialHelperName) {
      baseImageCredentialHelperName = credentialHelperName;
      return this;
    }

    public Builder setTargetImageCredentialHelperName(@Nullable String credentialHelperName) {
      targetImageCredentialHelperName = credentialHelperName;
      return this;
    }

    public Builder setKnownBaseRegistryCredentials(
        @Nullable RegistryCredentials knownRegistryCrendentials) {
      knownBaseRegistryCredentials = knownRegistryCrendentials;
      return this;
    }

    public Builder setKnownTargetRegistryCredentials(
        @Nullable RegistryCredentials knownRegistryCrendentials) {
      knownTargetRegistryCredentials = knownRegistryCrendentials;
      return this;
    }

    public Builder setMainClass(@Nullable String mainClass) {
      this.mainClass = mainClass;
      return this;
    }

    public Builder setJavaArguments(@Nullable List<String> javaArguments) {
      if (javaArguments != null) {
        Preconditions.checkArgument(!javaArguments.contains(null));
        this.javaArguments = ImmutableList.copyOf(javaArguments);
      }
      return this;
    }

    public Builder setJvmFlags(@Nullable List<String> jvmFlags) {
      if (jvmFlags != null) {
        Preconditions.checkArgument(!jvmFlags.contains(null));
        this.jvmFlags = ImmutableList.copyOf(jvmFlags);
      }
      return this;
    }

    public Builder setEnvironment(@Nullable Map<String, String> environmentMap) {
      if (environmentMap != null) {
        Preconditions.checkArgument(
            !environmentMap.containsKey(null) && !environmentMap.containsValue(null));
        this.environmentMap = ImmutableMap.copyOf(environmentMap);
      }
      return this;
    }

    public Builder setExposedPorts(@Nullable List<String> exposedPorts) {
      if (exposedPorts != null) {
        Preconditions.checkArgument(!exposedPorts.contains(null));
        this.exposedPorts = ImmutableList.copyOf(exposedPorts);
      }
      return this;
    }

    public Builder setTargetFormat(Class<? extends BuildableManifestTemplate> targetFormat) {
      this.targetFormat = targetFormat;
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
     * @param allowHttp if {@code true}, insecure connections will be allowed
     * @return this
     */
    public Builder setAllowHttp(boolean allowHttp) {
      this.allowHttp = allowHttp;
      return this;
    }

    /** @return the corresponding build configuration */
    public BuildConfiguration build() {
      // Validates the parameters.
      List<String> errorMessages = new ArrayList<>();
      if (baseImageReference == null) {
        errorMessages.add("base image is required but not set");
      }
      if (targetImageReference == null) {
        errorMessages.add("target image is required but not set");
      }
      if (mainClass == null) {
        errorMessages.add("main class is required but not set");
      }

      switch (errorMessages.size()) {
        case 0: // No errors
          if (baseImageReference == null || targetImageReference == null || mainClass == null) {
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
              baseImageCredentialHelperName,
              knownBaseRegistryCredentials,
              targetImageReference,
              targetImageCredentialHelperName,
              knownTargetRegistryCredentials,
              mainClass,
              javaArguments,
              jvmFlags,
              environmentMap,
              exposedPorts,
              targetFormat,
              applicationLayersCacheConfiguration,
              baseImageLayersCacheConfiguration,
              allowHttp);

        case 1:
          throw new IllegalStateException(errorMessages.get(0));

        case 2:
          throw new IllegalStateException(errorMessages.get(0) + " and " + errorMessages.get(1));

        default:
          // Appends the descriptions in correct grammar.
          StringBuilder errorMessage = new StringBuilder(errorMessages.get(0));
          for (int errorMessageIndex = 1;
              errorMessageIndex < errorMessages.size();
              errorMessageIndex++) {
            if (errorMessageIndex == errorMessages.size() - 1) {
              errorMessage.append(", and ");
            } else {
              errorMessage.append(", ");
            }
            errorMessage.append(errorMessages.get(errorMessageIndex));
          }
          throw new IllegalStateException(errorMessage.toString());
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
  private final String mainClass;
  private final ImmutableList<String> javaArguments;
  private final ImmutableList<String> jvmFlags;
  private final ImmutableMap<String, String> environmentMap;
  private final ImmutableList<String> exposedPorts;
  private final Class<? extends BuildableManifestTemplate> targetFormat;
  @Nullable private final CacheConfiguration applicationLayersCacheConfiguration;
  @Nullable private final CacheConfiguration baseImageLayersCacheConfiguration;
  private final boolean allowHttp;

  /** Instantiate with {@link Builder#build}. */
  private BuildConfiguration(
      BuildLogger buildLogger,
      ImageReference baseImageReference,
      @Nullable String baseImageCredentialHelperName,
      @Nullable RegistryCredentials knownBaseRegistryCredentials,
      ImageReference targetImageReference,
      @Nullable String targetImageCredentialHelperName,
      @Nullable RegistryCredentials knownTargetRegistryCredentials,
      String mainClass,
      ImmutableList<String> javaArguments,
      ImmutableList<String> jvmFlags,
      ImmutableMap<String, String> environmentMap,
      ImmutableList<String> exposedPorts,
      Class<? extends BuildableManifestTemplate> targetFormat,
      @Nullable CacheConfiguration applicationLayersCacheConfiguration,
      @Nullable CacheConfiguration baseImageLayersCacheConfiguration,
      boolean allowHttp) {
    this.buildLogger = buildLogger;
    this.baseImageReference = baseImageReference;
    this.baseImageCredentialHelperName = baseImageCredentialHelperName;
    this.knownBaseRegistryCredentials = knownBaseRegistryCredentials;
    this.targetImageReference = targetImageReference;
    this.targetImageCredentialHelperName = targetImageCredentialHelperName;
    this.knownTargetRegistryCredentials = knownTargetRegistryCredentials;
    this.mainClass = mainClass;
    this.javaArguments = javaArguments;
    this.jvmFlags = jvmFlags;
    this.environmentMap = environmentMap;
    this.exposedPorts = exposedPorts;
    this.targetFormat = targetFormat;
    this.applicationLayersCacheConfiguration = applicationLayersCacheConfiguration;
    this.baseImageLayersCacheConfiguration = baseImageLayersCacheConfiguration;
    this.allowHttp = allowHttp;
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

  public String getMainClass() {
    return mainClass;
  }

  public ImmutableList<String> getJavaArguments() {
    return javaArguments;
  }

  public ImmutableList<String> getJvmFlags() {
    return jvmFlags;
  }

  public ImmutableMap<String, String> getEnvironment() {
    return environmentMap;
  }

  public ImmutableList<String> getExposedPorts() {
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
   * Gets whether or not to allow communication over HTTP (as opposed to HTTPS).
   *
   * @return {@code true} if insecure connections will be allowed; {@code false} otherwise
   */
  public boolean getAllowHttp() {
    return allowHttp;
  }
}
