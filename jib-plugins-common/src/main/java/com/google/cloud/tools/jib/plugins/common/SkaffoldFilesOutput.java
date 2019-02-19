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
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds a JSON string containing files and directories that <a
 * href="https://github.com/GoogleContainerTools/skaffold">Skaffold</a> should watch for changes
 * (and consequently trigger rebuilds).
 *
 * <p>{@code buildFiles} consist of build definitions. Changes in these files indicate that the
 * project structure may have changed, so Skaffold will refresh the file watch list when this
 * happens.
 *
 * <p>{@code inputs} consist of source/resource directories. Skaffold will trigger a rebuild when
 * changes are detected in these files.
 *
 * <p>{@code ignore} consists of files that the Skaffold file watcher should not watch.
 *
 * <p>Example:
 *
 * <pre>{@code
 * {
 *   "buildFiles": [
 *     "buildFile1",
 *     "buildFile2"
 *   ],
 *   "inputs": [
 *     "src/main/java/",
 *     "src/main/resources/"
 *   ],
 *   "ignore": [
 *     "pathToIgnore"
 *   ]
 * }
 * }</pre>
 */
public class SkaffoldFilesOutput {

  @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
  private static class SkaffoldFilesTemplate {

    private final List<String> buildFiles = new ArrayList<>();

    private final List<String> inputs = new ArrayList<>();

    private final List<String> ignore = new ArrayList<>();
  }

  private final SkaffoldFilesTemplate skaffoldFilesTemplate = new SkaffoldFilesTemplate();

  /**
   * Adds a build file/directory.
   *
   * @param buildFile the path to the file
   */
  public void addBuildFile(Path buildFile) {
    skaffoldFilesTemplate.buildFiles.add(buildFile.toString());
  }

  /**
   * Adds an input file/directory.
   *
   * @param inputFile the path to the directory
   */
  public void addInput(Path inputFile) {
    skaffoldFilesTemplate.inputs.add(inputFile.toString());
  }

  /**
   * Adds an ignored file/directory.
   *
   * @param ignoreFile the path to the file
   */
  public void addIgnore(Path ignoreFile) {
    skaffoldFilesTemplate.ignore.add(ignoreFile.toString());
  }

  /**
   * Gets the added files in JSON format.
   *
   * @return the files in a JSON string
   * @throws IOException if writing out the JSON fails
   */
  public String getJsonString() throws IOException {
    try (OutputStream outputStream = new ByteArrayOutputStream()) {
      new ObjectMapper().writeValue(outputStream, skaffoldFilesTemplate);
      return outputStream.toString();
    }
  }
}
