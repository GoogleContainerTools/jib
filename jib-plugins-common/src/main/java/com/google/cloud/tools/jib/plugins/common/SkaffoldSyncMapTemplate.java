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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.tools.jib.api.buildplan.FileEntry;
import com.google.cloud.tools.jib.json.JsonTemplate;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds a JSON string containing files and directories that <a
 * href="https://github.com/GoogleContainerTools/skaffold">Skaffold</a> can use for synchronizing
 * files against a remote container.
 *
 * <p>Example:
 *
 * <pre>{@code
 * {
 *   "generated": [
 *      {
 *        src: "fileX-local",
 *        dest: "fileX-remote"
 *      },
 *      {
 *        src: "dirX-local",
 *        dest: "dirX-remote"
 *      }
 *   ],
 *   "direct": [
 *      {
 *        src: "fileY-local",
 *        dest: "fileY-remote"
 *      },
 *      {
 *        src: "dirY-local",
 *        dest: "dirY-remote"
 *      },
 *   ]
 * }
 * }</pre>
 */
public class SkaffoldSyncMapTemplate implements JsonTemplate {

  /**
   * A single entry in the skaffold sync map, may be eventually extended to support permissions and
   * ownership.
   */
  public static class FileTemplate implements JsonTemplate {
    private final String src;
    private final String dest;

    @JsonCreator
    public FileTemplate(
        @JsonProperty(value = "src", required = true) String src,
        @JsonProperty(value = "dest", required = true) String dest) {
      this.src = src;
      this.dest = dest;
    }

    @VisibleForTesting
    public String getSrc() {
      return src;
    }

    @VisibleForTesting
    public String getDest() {
      return dest;
    }
  }

  private final List<FileTemplate> generated = new ArrayList<>();
  private final List<FileTemplate> direct = new ArrayList<>();

  @VisibleForTesting
  public static SkaffoldSyncMapTemplate from(String jsonString) throws IOException {
    return new ObjectMapper().readValue(jsonString, SkaffoldSyncMapTemplate.class);
  }

  /**
   * Add a layer entry as a "generated" sync entry. Generated sync entries require rebuilds before
   * files can be sync'd to a running container.
   *
   * @param layerEntry the layer entry to add to the generated configuration
   */
  public void addGenerated(FileEntry layerEntry) {
    generated.add(
        new FileTemplate(
            layerEntry.getSourceFile().toAbsolutePath().toString(),
            layerEntry.getExtractionPath().toString()));
  }

  /**
   * Add a layer entry as a "direct" sync entry. Direct entries can be sync'd to a running container
   * without rebuilding any files.
   *
   * @param layerEntry the layer entry to add to the direct configuration
   */
  public void addDirect(FileEntry layerEntry) {
    direct.add(
        new FileTemplate(
            layerEntry.getSourceFile().toAbsolutePath().toString(),
            layerEntry.getExtractionPath().toString()));
  }

  /**
   * Return JSON representation of the SyncMap.
   *
   * @return the json string representation of this SyncMap
   * @throws IOException if json serialization fails
   */
  public String getJsonString() throws IOException {
    try (OutputStream outputStream = new ByteArrayOutputStream()) {
      new ObjectMapper().writeValue(outputStream, this);
      return outputStream.toString();
    }
  }

  @VisibleForTesting
  public List<FileTemplate> getGenerated() {
    return ImmutableList.copyOf(generated);
  }

  @VisibleForTesting
  public List<FileTemplate> getDirect() {
    return ImmutableList.copyOf(direct);
  }
}
