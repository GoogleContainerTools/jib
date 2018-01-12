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

package com.google.cloud.tools.crepecake.builder;

import com.google.cloud.tools.crepecake.registry.DockerCredentialRetriever;
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
    BASE_IMAGE_SERVER_URL(true),
    /** The image name/repository of the base image (also known as the registry namespace). */
    BASE_IMAGE_NAME(true),
    /** The base image tag. */
    BASE_IMAGE_TAG(true),

    /** The server URL of the registry to push the built image to. */
    TARGET_SERVER_URL(true),
    /** The image name/repository of the built image (also known as the registry namespace). */
    TARGET_IMAGE_NAME(true),
    /** The image tag of the built image (the part after the colon). */
    TARGET_TAG(true),

    /** The credential helper name used by {@link DockerCredentialRetriever}. */
    CREDENTIAL_HELPER_NAME(false),

    /** The main class to use when running the application. */
    MAIN_CLASS(true);

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
            put(Fields.BASE_IMAGE_SERVER_URL, "base image registry server URL");
            put(Fields.BASE_IMAGE_NAME, "base image name");
            put(Fields.BASE_IMAGE_TAG, "base image tag");
            put(Fields.TARGET_SERVER_URL, "target registry server URL");
            put(Fields.TARGET_IMAGE_NAME, "target image name");
            put(Fields.TARGET_TAG, "target image tag");
            put(Fields.CREDENTIAL_HELPER_NAME, "credential helper name");
            put(Fields.MAIN_CLASS, "main class");
          }
        };

    private static final String MISSING_FIELD_MESSAGE_SUFFIX =
        " required but not set in build configuration";

    private Map<Fields, String> values = new EnumMap<>(Fields.class);

    private Builder() {}

    public Builder setBaseImageServerUrl(String baseImageServerUrl) {
      values.put(Fields.BASE_IMAGE_SERVER_URL, baseImageServerUrl);
      return this;
    }

    public Builder setBaseImageName(String baseImageName) {
      values.put(Fields.BASE_IMAGE_NAME, baseImageName);
      return this;
    }

    public Builder setBaseImageTag(String baseImageTag) {
      values.put(Fields.BASE_IMAGE_TAG, baseImageTag);
      return this;
    }

    public Builder setTargetServerUrl(String targetServerUrl) {
      values.put(Fields.TARGET_SERVER_URL, targetServerUrl);
      return this;
    }

    public Builder setTargetImageName(String targetImageName) {
      values.put(Fields.TARGET_IMAGE_NAME, targetImageName);
      return this;
    }

    public Builder setTargetTag(String targetTag) {
      values.put(Fields.TARGET_TAG, targetTag);
      return this;
    }

    public Builder setCredentialHelperName(String credentialHelperName) {
      values.put(Fields.CREDENTIAL_HELPER_NAME, credentialHelperName);
      return this;
    }

    public Builder setMainClass(String mainClass) {
      values.put(Fields.MAIN_CLASS, mainClass);
      return this;
    }

    /**
     * @return the corresponding build configuration
     * @throws IllegalStateException if required fields were not set
     */
    public BuildConfiguration build() {
      List<String> descriptions = new ArrayList<>();
      for (Fields field : Fields.values()) {
        if (field.isRequired() && !values.containsKey(field)) {
          descriptions.add(FIELD_DESCRIPTIONS.get(field));
        }
      }
      // TODO: Find and replace with utility method for generating lists in English grammar.
      switch (descriptions.size()) {
        case 0:
          values = Collections.unmodifiableMap(values);
          return new BuildConfiguration(values);

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

  private final Map<Fields, String> values;

  public static Builder builder() {
    return new Builder();
  }

  private BuildConfiguration(Map<Fields, String> values) {
    this.values = values;
  }

  public String getBaseImageServerUrl() {
    return values.get(Fields.BASE_IMAGE_SERVER_URL);
  }

  public String getBaseImageName() {
    return values.get(Fields.BASE_IMAGE_NAME);
  }

  public String getBaseImageTag() {
    return values.get(Fields.BASE_IMAGE_TAG);
  }

  public String getTargetServerUrl() {
    return values.get(Fields.TARGET_SERVER_URL);
  }

  public String getTargetImageName() {
    return values.get(Fields.TARGET_IMAGE_NAME);
  }

  public String getTargetTag() {
    return values.get(Fields.TARGET_TAG);
  }

  public String getCredentialHelperName() {
    return values.get(Fields.CREDENTIAL_HELPER_NAME);
  }

  public String getMainClass() {
    return values.get(Fields.MAIN_CLASS);
  }
}
