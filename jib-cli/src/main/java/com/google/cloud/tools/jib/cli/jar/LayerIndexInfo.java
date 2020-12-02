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

package com.google.cloud.tools.jib.cli.jar;

import com.google.cloud.tools.jib.api.buildplan.FileEntriesLayer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class LayerIndexInfo {
  public static class Builder {
    private String layerName = "";
    private List<Path> files = new ArrayList<>();

    private Builder() {}

    public Builder setLayerName(String layerName) {
      this.layerName = layerName;
      return this;
    }

    public Builder setFilePaths(List<Path> files) {
      this.files = files;
      return this;
    }

    public Builder addFilePath(Path file) {
      this.files.add(file);
      return this;
    }

    public LayerIndexInfo build() {
      return new LayerIndexInfo(layerName, files);
    }
  }

  /**
   * Gets a new {@link FileEntriesLayer.Builder} for {@link FileEntriesLayer}.
   *
   * @return a new {@link FileEntriesLayer.Builder}
   */
  public static Builder builder() {
    return new Builder();
  }

  private final String layerName;
  private final List<Path> files;

  /**
   * Use {@link #builder} to instantiate.
   *
   * @param layerName an optional name for the layer
   * @param files the list of {@link Path}s
   */
  private LayerIndexInfo(String layerName, List<Path> files) {
    this.layerName = layerName;
    this.files = files;
  }

  public String getLayerName() {
    return layerName;
  }

  public List<Path> getFilePaths() {
    return files;
  }

  /**
   * Creates a builder configured with the current values.
   *
   * @return {@link FileEntriesLayer.Builder} configured with the current values
   */
  public Builder toBuilder() {
    return builder().setLayerName(layerName).setFilePaths(files);
  }
}
