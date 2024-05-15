/*
 * Copyright 2024 Google LLC.
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

package com.google.cloud.tools.jib.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.cloud.tools.jib.json.JsonTemplate;

/** Contains the os and architecture from {@code docker info}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DockerInfoDetails implements JsonTemplate {

  @JsonProperty("OSType")
  private String osType = "";

  @JsonProperty("Architecture")
  private String architecture = "";

  public String getOsType() {
    return osType;
  }

  public String getArchitecture() {
    return architecture;
  }
}
