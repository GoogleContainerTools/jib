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
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;
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
public class SkaffoldInitOutput {

  @JsonInclude(Include.NON_NULL)
  @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
  private static class SkaffoldInitTemplate {

    @Nullable private String image;

    @Nullable private String project;
  }

  private final SkaffoldInitTemplate skaffoldInitTemplate = new SkaffoldInitTemplate();

  public void setImage(@Nullable String image) {
    skaffoldInitTemplate.image = image;
  }

  public void setProject(@Nullable String project) {
    skaffoldInitTemplate.project = project;
  }

  /**
   * Gets the added files in JSON format.
   *
   * @return the files in a JSON string
   * @throws IOException if writing out the JSON fails
   */
  public String getJsonString() throws IOException {
    try (OutputStream outputStream = new ByteArrayOutputStream()) {
      new ObjectMapper().writeValue(outputStream, skaffoldInitTemplate);
      return outputStream.toString();
    }
  }
}
