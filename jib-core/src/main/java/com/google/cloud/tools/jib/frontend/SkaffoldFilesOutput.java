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

package com.google.cloud.tools.jib.frontend;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.cloud.tools.jib.blob.Blob;
import com.google.cloud.tools.jib.json.JsonTemplate;
import com.google.cloud.tools.jib.json.JsonTemplateMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class SkaffoldFilesOutput {

  @JsonIgnoreProperties(ignoreUnknown = true)
  private static class SkaffoldFilesTemplate implements JsonTemplate {

    private final List<String> buildFiles = new ArrayList<>();

    private final List<String> inputs = new ArrayList<>();

    private final List<String> ignore = new ArrayList<>();
  }

  private final SkaffoldFilesTemplate skaffoldFilesTemplate = new SkaffoldFilesTemplate();

  public void addBuildFile(Path buildFile) {
    skaffoldFilesTemplate.buildFiles.add(buildFile.toString());
  }

  public void addInput(Path inputFile) {
    skaffoldFilesTemplate.inputs.add(inputFile.toString());
  }

  public void addIgnore(Path ignoreFile) {
    skaffoldFilesTemplate.ignore.add(ignoreFile.toString());
  }

  public String getJsonString() throws IOException {
    Blob blob = JsonTemplateMapper.toBlob(skaffoldFilesTemplate);
    try (OutputStream outputStream = new ByteArrayOutputStream()) {
      blob.writeTo(outputStream);
      return outputStream.toString();
    }
  }
}
