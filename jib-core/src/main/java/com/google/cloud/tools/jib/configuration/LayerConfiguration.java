/*
 * Copyright 2018 Google LLC.
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

package com.google.cloud.tools.jib.configuration;

import com.google.cloud.tools.jib.image.LayerEntry;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Configures how to build a layer in the container image. */
public class LayerConfiguration {

  /** Builds a {@link LayerConfiguration}. */
  public static class Builder {

    private final ImmutableList.Builder<LayerEntry> layerEntries = ImmutableList.builder();
    private String label = "";

    private Builder() {}

    /**
     * Sets a label for this layer.
     *
     * @param label the label
     * @return this
     */
    public Builder setLabel(String label) {
      this.label = label;
      return this;
    }

    /**
     * Adds an entry to the layer. Only adds the single source file to the exact path in the
     * container file system.
     *
     * <p>For example, {@code addEntry(Paths.get("myfile"), Paths.get("/path/in/container"))} would
     * add {@code myfile} to the container to be accessed at {@code /path/in/container}.
     *
     * @param sourceFile the source file to add to the layer
     * @param pathInContainer the path to add the source files to in the container file system
     *     (relative to root {@code /})
     * @return this
     */
    public Builder addEntry(Path sourceFile, Path pathInContainer) {
      layerEntries.add(new LayerEntry(sourceFile, pathInContainer));
      return this;
    }

    /**
     * Adds an entry to the layer. If the source file is a directory, the directory and its contents
     * will be added recursively.
     *
     * <p>For example, {@code addEntryRecursive(Paths.get("mydirectory"),
     * Paths.get("/path/in/container"))} would add {@code mydirectory} to the container to be
     * accessed at {@code /path/in/container} and the contents of {@code mydirectory} to be accessed
     * at {@code /path/in/container/**}.
     *
     * @param sourceFile the source file to add to the layer recursively
     * @param pathInContainer the path to add the source files to in the container file system
     *     (relative to root {@code /})
     * @return this
     * @throws IOException if an exception occurred when recursively listing the directory
     */
    // @Untested
    public Builder addEntryRecursive(Path sourceFile, Path pathInContainer) throws IOException {
      if (!Files.isDirectory(sourceFile)) {
        return addEntry(sourceFile, pathInContainer);
      }
      addEntry(sourceFile, pathInContainer);
      try (Stream<Path> files = Files.list(sourceFile)) {
        for (Path file : files.collect(Collectors.toList())) {
          addEntryRecursive(file, pathInContainer.resolve(file.getFileName()));
        }
      }
      return this;
    }

    /**
     * Returns the built {@link LayerConfiguration}.
     *
     * @return the built {@link LayerConfiguration}
     */
    public LayerConfiguration build() {
      return new LayerConfiguration(label, layerEntries.build());
    }
  }

  public static Builder builder() {
    return new Builder();
  }

  private final ImmutableList<LayerEntry> layerEntries;
  private final String label;

  /**
   * Constructs a new layer configuration.
   *
   * @param label an optional label for the layer
   * @param layerEntries the list of {@link LayerEntry}s
   */
  private LayerConfiguration(String label, ImmutableList<LayerEntry> layerEntries) {
    this.label = label;
    this.layerEntries = layerEntries;
  }

  public String getLabel() {
    return label;
  }

  public ImmutableList<LayerEntry> getLayerEntries() {
    return layerEntries;
  }
}
