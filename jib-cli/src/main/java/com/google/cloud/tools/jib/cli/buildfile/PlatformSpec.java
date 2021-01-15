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
// TODO: reintroduce platform details when ready
// TODO: revert https://github.com/GoogleContainerTools/jib/pull/2763
public class PlatformSpec {
  private final String architecture;
  private final String os;

  /**
   * Constructor for use by jackson to populate this object.
   *
   * @param architecture the target cpu architecture
   * @param os the target operating system
   */
  @JsonCreator
  public PlatformSpec(
      @JsonProperty(value = "architecture", required = true) String architecture,
      @JsonProperty(value = "os", required = true) String os) {
    Validator.checkNotNullAndNotEmpty(architecture, "architecture");
    Validator.checkNotNullAndNotEmpty(os, "os");
    this.architecture = architecture;
    this.os = os;
  }

  public String getArchitecture() {
    return architecture;
  }

  public String getOs() {
    return os;
  }
}
