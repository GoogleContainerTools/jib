/*
 * Copyright 2019 Google LLC.
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

package com.google.cloud.tools.jib.plugins.common;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import javax.annotation.Nullable;

/**
 * Builds a JSON string containing the configured target image and sub-project name, to be consumed
 * by <a href="https://github.com/GoogleContainerTools/skaffold">Skaffold</a>.
 *
 * <p>Example:
 *
 * <pre>{@code
 * {
 *   "image":"gcr.io/project/test",
 *   "project":"project-name"
 * }
 * }</pre>
 */
@JsonInclude(Include.NON_NULL)
@JsonAutoDetect(
    fieldVisibility = Visibility.ANY,
    setterVisibility = Visibility.NONE,
    getterVisibility = Visibility.NONE)
public class SkaffoldInitOutput {

  @Nullable private String image;

  @Nullable private String project;

  public SkaffoldInitOutput() {}

  /**
   * Testing visible OutputGenerator, you should NOT use this.
   *
   * @param json the json string to convert
   * @throws IOException if error occurs during json deserialization
   */
  @VisibleForTesting
  public SkaffoldInitOutput(String json) throws IOException {
    SkaffoldInitOutput skaffoldInitOutput =
        new ObjectMapper().readValue(json, SkaffoldInitOutput.class);
    this.image = skaffoldInitOutput.image;
    this.project = skaffoldInitOutput.project;
  }

  public void setImage(@Nullable String image) {
    this.image = image;
  }

  public void setProject(@Nullable String project) {
    this.project = project;
  }

  /**
   * Gets the added files in JSON format.
   *
   * @return the files in a JSON string
   * @throws IOException if writing out the JSON fails
   */
  public String getJsonString() throws IOException {
    try (OutputStream outputStream = new ByteArrayOutputStream()) {
      new ObjectMapper().writeValue(outputStream, this);
      return outputStream.toString();
    }
  }

  @VisibleForTesting
  @Nullable
  public String getImage() {
    return image;
  }

  @VisibleForTesting
  @Nullable
  public String getProject() {
    return project;
  }
}
