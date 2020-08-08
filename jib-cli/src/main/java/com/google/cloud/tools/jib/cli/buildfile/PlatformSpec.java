/*
 * Copyright 2020 Google LLC.
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

package com.google.cloud.tools.jib.cli.buildfile;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * A yaml block for specifying platforms.
 *
 * <p>Example use of this yaml snippet.
 *
 * <pre>{@code
 * architecture: amd64
 * os: linux
 * os.version: 1.0.0
 * os.features:
 *   - headless
 * variant: amd64v10
 * features:
 *   - sse4
 *   - aes
 * }</pre>
 */
public class PlatformSpec {
  private final String architecture;
  private final String os;
  @Nullable private final String osVersion;
  private final List<String> osFeatures;
  @Nullable private final String variant;
  private final List<String> features;

  /**
   * Constructor for use by jackson to populate this object.
   *
   * @param architecture the target cpu architecture
   * @param os the target operating system
   * @param variant the cpu variant
   * @param features a list of cpu features
   * @param osVersion the operating system version
   * @param osFeatures a list of operating system features
   */
  @JsonCreator
  public PlatformSpec(
      @JsonProperty(value = "architecture", required = true) String architecture,
      @JsonProperty(value = "os", required = true) String os,
      @JsonProperty("os.version") String osVersion,
      @JsonProperty("os.features") List<String> osFeatures,
      @JsonProperty("variant") String variant,
      @JsonProperty("features") List<String> features) {
    Validator.checkNotNullAndNotEmpty(architecture, "architecture");
    Validator.checkNotNullAndNotEmpty(os, "os");
    Validator.checkNullOrNotEmpty(osVersion, "os.version");
    Validator.checkNullOrNonNullNonEmptyEntries(osFeatures, "os.features");
    Validator.checkNullOrNotEmpty(variant, "variant");
    Validator.checkNullOrNonNullNonEmptyEntries(features, "features");
    this.architecture = architecture;
    this.os = os;
    this.osVersion = osVersion;
    this.osFeatures = (osFeatures == null) ? ImmutableList.of() : osFeatures;
    this.variant = variant;
    this.features = (features == null) ? ImmutableList.of() : features;
  }

  public String getArchitecture() {
    return architecture;
  }

  public String getOs() {
    return os;
  }

  public Optional<String> getOsVersion() {
    return Optional.ofNullable(osVersion);
  }

  public List<String> getOsFeatures() {
    return osFeatures;
  }

  public Optional<String> getVariant() {
    return Optional.ofNullable(variant);
  }

  public List<String> getFeatures() {
    return features;
  }
}
