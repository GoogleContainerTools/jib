/*
 * Copyright 2018 Google Inc.
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

import com.google.cloud.tools.jib.image.ImageReference;
import com.google.cloud.tools.jib.image.json.BuildableManifestTemplate;
import com.google.cloud.tools.jib.image.json.V22ManifestTemplate;
import com.google.cloud.tools.jib.registry.credentials.RegistryCredentials;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/** Immutable configuration options for the builder process. */
public class BuildConfiguration {

  public static class Builder {

    // All the parameters below are set to their default values.
    private ImageReference baseImageReference;
    private ImageReference targetImageReference;
    private List<String> credentialHelperNames = new ArrayList<>();
    private RegistryCredentials knownRegistryCredentials = RegistryCredentials.none();
    private boolean enableReproducibleBuilds = true;
    private String mainClass;
    private List<String> jvmFlags = new ArrayList<>();
    private Map<String, String> environmentMap = new HashMap<>();
    private Class<? extends BuildableManifestTemplate> targetFormat = V22ManifestTemplate.class;

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

    public Builder setCredentialHelperNames(@Nullable List<String> credentialHelperNames) {
      if (credentialHelperNames != null) {
        this.credentialHelperNames = credentialHelperNames;
      }
      return this;
    }

    public Builder setKnownRegistryCredentials(
        @Nullable RegistryCredentials knownRegistryCredentials) {
      if (knownRegistryCredentials != null) {
        this.knownRegistryCredentials = knownRegistryCredentials;
      }
      return this;
    }

    public Builder setEnableReproducibleBuilds(boolean isEnabled) {
      enableReproducibleBuilds = isEnabled;
      return this;
    }

    public Builder setMainClass(@Nullable String mainClass) {
      this.mainClass = mainClass;
      return this;
    }

    public Builder setJvmFlags(@Nullable List<String> jvmFlags) {
      if (jvmFlags != null) {
        this.jvmFlags = jvmFlags;
      }
      return this;
    }

    public Builder setEnvironment(@Nullable Map<String, String> environmentMap) {
      if (environmentMap != null) {
        this.environmentMap = environmentMap;
      }
      return this;
    }

    public Builder setTargetFormat(Class<? extends BuildableManifestTemplate> targetFormat) {
      this.targetFormat = targetFormat;
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
          return new BuildConfiguration(
              buildLogger,
              baseImageReference,
              targetImageReference,
              credentialHelperNames,
              knownRegistryCredentials,
              enableReproducibleBuilds,
              mainClass,
              jvmFlags,
              environmentMap,
              targetFormat);

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

  private final BuildLogger buildLogger;
  private ImageReference baseImageReference;
  private ImageReference targetImageReference;
  private List<String> credentialHelperNames;
  private RegistryCredentials knownRegistryCredentials;
  private boolean enableReproducibleBuilds;
  private String mainClass;
  private List<String> jvmFlags;
  private Map<String, String> environmentMap;
  private Class<? extends BuildableManifestTemplate> targetFormat;

  public static Builder builder(BuildLogger buildLogger) {
    return new Builder(buildLogger);
  }

  /** Instantiate with {@link Builder#build}. */
  private BuildConfiguration(
      BuildLogger buildLogger,
      ImageReference baseImageReference,
      ImageReference targetImageReference,
      List<String> credentialHelperNames,
      RegistryCredentials knownRegistryCredentials,
      boolean enableReproducibleBuilds,
      String mainClass,
      List<String> jvmFlags,
      Map<String, String> environmentMap,
      Class<? extends BuildableManifestTemplate> targetFormat) {
    this.buildLogger = buildLogger;
    this.baseImageReference = baseImageReference;
    this.targetImageReference = targetImageReference;
    this.credentialHelperNames = Collections.unmodifiableList(credentialHelperNames);
    this.knownRegistryCredentials = knownRegistryCredentials;
    this.enableReproducibleBuilds = enableReproducibleBuilds;
    this.mainClass = mainClass;
    this.jvmFlags = Collections.unmodifiableList(jvmFlags);
    this.environmentMap = Collections.unmodifiableMap(environmentMap);
    this.targetFormat = targetFormat;
  }

  public BuildLogger getBuildLogger() {
    return buildLogger;
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

  public String getTargetRegistry() {
    return targetImageReference.getRegistry();
  }

  public String getTargetRepository() {
    return targetImageReference.getRepository();
  }

  public String getTargetTag() {
    return targetImageReference.getTag();
  }

  public RegistryCredentials getKnownRegistryCredentials() {
    return knownRegistryCredentials;
  }

  public List<String> getCredentialHelperNames() {
    return credentialHelperNames;
  }

  public boolean getEnableReproducibleBuilds() {
    return enableReproducibleBuilds;
  }

  public String getMainClass() {
    return mainClass;
  }

  public List<String> getJvmFlags() {
    return jvmFlags;
  }

  public Map<String, String> getEnvironment() {
    return environmentMap;
  }

  public Class<? extends BuildableManifestTemplate> getTargetFormat() {
    return targetFormat;
  }
}
