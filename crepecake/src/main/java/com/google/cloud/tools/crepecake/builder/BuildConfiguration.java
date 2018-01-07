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
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/** Immutable configuration options for the builder process. */
public class BuildConfiguration {

  /** Enumerates the fields in the configuration. */
  @VisibleForTesting
  enum Fields {
    /** The server URL of the registry to pull the base image from. */
    BASE_IMAGE_SERVER_URL,
    /** The image name/repository the base image (also known as the registry namespace). */
    BASE_IMAGE_NAME,

    /** The server URL of the registry to push the built image to. */
    TARGET_SERVER_URL,
    /** The image name/repository of the built image (also known as the registry namespace). */
    TARGET_IMAGE_NAME,
    /** The image tag of the built image (the part after the colon). */
    TARGET_TAG,

    /** The credential helper name used by {@link DockerCredentialRetriever}. */
    CREDENTIAL_HELPER_NAME,
  }

  public static class Builder {

    /** Textual descriptions of the configuration fields. */
    @VisibleForTesting
    static final Map<Fields, String> FIELD_DESCRIPTIONS =
        new EnumMap<Fields, String>(Fields.class) {
          {
            put(Fields.BASE_IMAGE_SERVER_URL, "base image registry server URL");
            put(Fields.BASE_IMAGE_NAME, "base image name");
            put(Fields.TARGET_SERVER_URL, "target registry server URL");
            put(Fields.TARGET_IMAGE_NAME, "target image name");
            put(Fields.TARGET_TAG, "target image tag");
            put(Fields.CREDENTIAL_HELPER_NAME, "credential helper name");
          }
        };

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

    public BuildConfiguration build() throws BuildConfigurationMissingValueException {
      BuildConfigurationMissingValueException.Builder
          buildConfigurationMissingValueExceptionBuilder =
              BuildConfigurationMissingValueException.builder();
      for (Fields field : Fields.values()) {
        if (!values.containsKey(field)) {
          buildConfigurationMissingValueExceptionBuilder.addDescription(
              FIELD_DESCRIPTIONS.get(field));
        }
      }
      BuildConfigurationMissingValueException ex =
          buildConfigurationMissingValueExceptionBuilder.build();
      if (ex != null) {
        throw ex;
      }

      values = Collections.unmodifiableMap(values);
      return new BuildConfiguration(values);
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
}
