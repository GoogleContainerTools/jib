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

/** Immutable configuration options for the builder process. */
public class BuildConfiguration {

  public static class Builder {

    private String baseImageServerUrl;
    private String baseImageName;

    private String targetServerUrl;
    private String targetImageName;
    private String targetTag;

    private String credentialHelperName;

    private Builder() {}

    /** Sets the server URL of the registry to pull the base image from. */
    public Builder setBaseImageServerUrl(String baseImageServerUrl) {
      this.baseImageServerUrl = baseImageServerUrl;
      return this;
    }

    /** Sets the image name/repository the base image (also known as the registry namespace). */
    public Builder setBaseImageName(String baseImageName) {
      this.baseImageName = baseImageName;
      return this;
    }

    /** Sets the server URL of the registry to push the built image to. */
    public Builder setTargetServerUrl(String targetServerUrl) {
      this.targetServerUrl = targetServerUrl;
      return this;
    }

    /** Sets the image name/repository the built image (also known as the registry namespace). */
    public Builder setTargetImageName(String targetImageName) {
      this.targetImageName = targetImageName;
      return this;
    }

    /** Sets the image tag of the built image (the part after the colon). */
    public Builder setTargetTag(String targetTag) {
      this.targetTag = targetTag;
      return this;
    }

    /** Sets the credential helper name used by {@link DockerCredentialRetriever}. */
    public Builder setCredentialHelperName(String credentialHelperName) {
      this.credentialHelperName = credentialHelperName;
      return this;
    }

    public BuildConfiguration build() {
      return new BuildConfiguration(
          baseImageServerUrl,
          baseImageName,
          targetServerUrl,
          targetImageName,
          targetTag,
          credentialHelperName);
    }
  }

  private final String baseImageServerUrl;
  private final String baseImageName;

  private final String targetServerUrl;
  private final String targetImageName;
  private final String targetTag;

  private final String credentialHelperName;

  public static Builder builder() {
    return new Builder();
  }

  private BuildConfiguration(
      String baseImageServerUrl,
      String baseImageName,
      String targetServerUrl,
      String targetImageName,
      String targetTag,
      String credentialHelperName) {
    this.baseImageServerUrl = baseImageServerUrl;
    this.baseImageName = baseImageName;
    this.targetServerUrl = targetServerUrl;
    this.targetImageName = targetImageName;
    this.targetTag = targetTag;
    this.credentialHelperName = credentialHelperName;
  }

  public String getBaseImageServerUrl() {
    return baseImageServerUrl;
  }

  public String getBaseImageName() {
    return baseImageName;
  }

  public String getTargetServerUrl() {
    return targetServerUrl;
  }

  public String getTargetImageName() {
    return targetImageName;
  }

  public String getTargetTag() {
    return targetTag;
  }

  public String getCredentialHelperName() {
    return credentialHelperName;
  }
}
