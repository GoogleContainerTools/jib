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
  @Nullable private String architecture;
  @Nullable private String os;
  @Nullable private String osVersion;
  @Nullable private List<String> osFeatures;
  @Nullable private String variant;
  @Nullable private List<String> features;

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
      @JsonProperty("architecture") String architecture,
      @JsonProperty("os") String os,
      @JsonProperty("os.version") String osVersion,
      @JsonProperty("os.features") List<String> osFeatures,
      @JsonProperty("variant") String variant,
      @JsonProperty("features") List<String> features) {
    this.architecture = architecture;
    this.os = os;
    this.osVersion = osVersion;
    this.osFeatures = osFeatures;
    this.variant = variant;
    this.features = features;
  }

  public Optional<String> getArchitecture() {
    return Optional.ofNullable(architecture);
  }

  public Optional<String> getOs() {
    return Optional.ofNullable(os);
  }

  public Optional<String> getOsVersion() {
    return Optional.ofNullable(osVersion);
  }

  public Optional<List<String>> getOsFeatures() {
    return Optional.ofNullable(osFeatures);
  }

  public Optional<String> getVariant() {
    return Optional.ofNullable(variant);
  }

  public Optional<List<String>> getFeatures() {
    return Optional.ofNullable(features);
  }
}
