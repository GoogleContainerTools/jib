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

import com.google.cloud.tools.jib.registry.credentials.RegistryCredentials;
import com.google.common.annotations.VisibleForTesting;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Immutable configuration options for the builder process. */
public class BuildConfiguration {

  /** Enumerates the fields in the configuration. */
  @VisibleForTesting
  enum Fields {
    /** The server URL of the registry to pull the base image from. */
    BASE_IMAGE_REGISTRY(true),
    /** The image name/repository of the base image (also known as the registry namespace). */
    BASE_IMAGE_REPOSITORY(true),
    /** The base image tag. */
    BASE_IMAGE_TAG(true),

    /** The server URL of the registry to push the built image to. */
    TARGET_REGISTRY(true),
    /** The image name/repository of the built image (also known as the registry namespace). */
    TARGET_REPOSITORY(true),
    /** The image tag of the built image (the part after the colon). */
    TARGET_TAG(true),

    /** The credential helper names used by {@link RegistryCredentials}. */
    CREDENTIAL_HELPER_NAMES(false),
    /** Known registry credentials to fallback on. */
    KNOWN_REGISTRY_CREDENTIALS(false),

    /** The main class to use when running the application. */
    MAIN_CLASS(true),

    /** Additional JVM flags to use when running the application. */
    JVM_FLAGS(false),
    /** Environment variables to set when running the application. */
    ENVIRONMENT(false);

    private final boolean required;

    Fields(boolean required) {
      this.required = required;
    }

    @VisibleForTesting
    boolean isRequired() {
      return required;
    }
  }

  public static class Builder {

    /** Textual descriptions of the configuration fields. */
    @VisibleForTesting
    static final Map<Fields, String> FIELD_DESCRIPTIONS =
        new EnumMap<Fields, String>(Fields.class) {
          {
            put(Fields.BASE_IMAGE_REGISTRY, "base image registry server URL");
            put(Fields.BASE_IMAGE_REPOSITORY, "base image name");
            put(Fields.BASE_IMAGE_TAG, "base image tag");
            put(Fields.TARGET_REGISTRY, "target registry server URL");
            put(Fields.TARGET_REPOSITORY, "target image name");
            put(Fields.TARGET_TAG, "target image tag");
            put(Fields.CREDENTIAL_HELPER_NAMES, "credential helper names");
            put(Fields.MAIN_CLASS, "main class");
            put(Fields.JVM_FLAGS, "JVM flags");
            put(Fields.ENVIRONMENT, "environment variables");
          }
        };

    private static final String MISSING_FIELD_MESSAGE_SUFFIX =
        " required but not set in build configuration";

    private BuildLogger buildLogger;
    private Map<Fields, Object> values = new EnumMap<>(Fields.class);

    private Builder() {
      // Sets default empty values.
      values.put(Fields.CREDENTIAL_HELPER_NAMES, Collections.emptyList());
      values.put(Fields.KNOWN_REGISTRY_CREDENTIALS, RegistryCredentials.none());
      values.put(Fields.JVM_FLAGS, Collections.emptyList());
      values.put(Fields.ENVIRONMENT, Collections.emptyMap());
    }

    public Builder setBuildLogger(BuildLogger buildLogger) {
      this.buildLogger = buildLogger;
      return this;
    }

    public Builder setBaseImageRegistry(String baseImageServerUrl) {
      values.put(Fields.BASE_IMAGE_REGISTRY, baseImageServerUrl);
      return this;
    }

    public Builder setBaseImageRepository(String baseImageName) {
      values.put(Fields.BASE_IMAGE_REPOSITORY, baseImageName);
      return this;
    }

    public Builder setBaseImageTag(String baseImageTag) {
      values.put(Fields.BASE_IMAGE_TAG, baseImageTag);
      return this;
    }

    public Builder setTargetRegistry(String targetServerUrl) {
      values.put(Fields.TARGET_REGISTRY, targetServerUrl);
      return this;
    }

    public Builder setTargetRepository(String targetImageName) {
      values.put(Fields.TARGET_REPOSITORY, targetImageName);
      return this;
    }

    public Builder setTargetTag(String targetTag) {
      values.put(Fields.TARGET_TAG, targetTag);
      return this;
    }

    public Builder setCredentialHelperNames(List<String> credentialHelperNames) {
      if (credentialHelperNames != null) {
        values.put(Fields.CREDENTIAL_HELPER_NAMES, credentialHelperNames);
      }
      return this;
    }

    public Builder setKnownRegistryCredentials(RegistryCredentials knownRegistryCredentials) {
      if (knownRegistryCredentials != null) {
        values.put(Fields.KNOWN_REGISTRY_CREDENTIALS, knownRegistryCredentials);
      }
      return this;
    }

    public Builder setMainClass(String mainClass) {
      values.put(Fields.MAIN_CLASS, mainClass);
      return this;
    }

    public Builder setJvmFlags(List<String> jvmFlags) {
      if (jvmFlags != null) {
        values.put(Fields.JVM_FLAGS, jvmFlags);
      }
      return this;
    }

    public Builder setEnvironment(Map<String, String> environment) {
      if (environment != null) {
        values.put(Fields.ENVIRONMENT, environment);
      }
      return this;
    }

    /**
     * @return the corresponding build configuration
     * @throws IllegalStateException if required fields were not set
     */
    public BuildConfiguration build() {
      List<String> descriptions = new ArrayList<>();
      for (Fields field : Fields.values()) {
        if (field.isRequired() && (!values.containsKey(field) || values.get(field) == null)) {
          descriptions.add(FIELD_DESCRIPTIONS.get(field));
        }
      }
      // TODO: Find and replace with utility method for generating lists in English grammar.
      switch (descriptions.size()) {
        case 0:
          values = Collections.unmodifiableMap(values);
          return new BuildConfiguration(buildLogger, values);

        case 1:
          throw new IllegalStateException(descriptions.get(0) + MISSING_FIELD_MESSAGE_SUFFIX);

        case 2:
          throw new IllegalStateException(
              descriptions.get(0) + " and " + descriptions.get(1) + MISSING_FIELD_MESSAGE_SUFFIX);

        default:
          // Appends the descriptions in correct grammar.
          StringBuilder stringBuilder = new StringBuilder();
          for (int descriptionsIndex = 0;
              descriptionsIndex < descriptions.size();
              descriptionsIndex++) {
            if (descriptionsIndex == descriptions.size() - 1) {
              stringBuilder.append(", and ");
            } else {
              stringBuilder.append(", ");
            }
            stringBuilder.append(descriptions.get(descriptionsIndex));
          }
          throw new IllegalStateException(
              stringBuilder.append(MISSING_FIELD_MESSAGE_SUFFIX).toString());
      }
    }
  }

  private final BuildLogger buildLogger;
  private final Map<Fields, Object> values;

  public static Builder builder() {
    return new Builder();
  }

  private BuildConfiguration(BuildLogger buildLogger, Map<Fields, Object> values) {
    this.buildLogger = buildLogger;
    this.values = values;
  }

  public BuildLogger getBuildLogger() {
    return buildLogger;
  }

  public String getBaseImageRegistry() {
    return getFieldValue(Fields.BASE_IMAGE_REGISTRY);
  }

  public String getBaseImageRepository() {
    return getFieldValue(Fields.BASE_IMAGE_REPOSITORY);
  }

  public String getBaseImageTag() {
    return getFieldValue(Fields.BASE_IMAGE_TAG);
  }

  public String getTargetRegistry() {
    return getFieldValue(Fields.TARGET_REGISTRY);
  }

  public String getTargetRepository() {
    return getFieldValue(Fields.TARGET_REPOSITORY);
  }

  public String getTargetTag() {
    return getFieldValue(Fields.TARGET_TAG);
  }

  public RegistryCredentials getKnownRegistryCredentials() {
    return getFieldValue(Fields.KNOWN_REGISTRY_CREDENTIALS);
  }

  public List<String> getCredentialHelperNames() {
    return getFieldValue(Fields.CREDENTIAL_HELPER_NAMES);
  }

  public String getMainClass() {
    return getFieldValue(Fields.MAIN_CLASS);
  }

  public List<String> getJvmFlags() {
    return Collections.unmodifiableList(getFieldValue(Fields.JVM_FLAGS));
  }

  public Map<String, String> getEnvironment() {
    return Collections.unmodifiableMap(getFieldValue(Fields.ENVIRONMENT));
  }

  @SuppressWarnings("unchecked")
  private <T> T getFieldValue(Fields field) {
    return (T) values.get(field);
  }
}
