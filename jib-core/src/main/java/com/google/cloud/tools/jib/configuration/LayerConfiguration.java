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

/** Configures how to build a layer in the container image. Instantiate with {@link #builder}. */
public class LayerConfiguration {

  /** Builds a {@link LayerConfiguration}. */
  public static class Builder {

    private final ImmutableList.Builder<LayerEntry> layerEntries = ImmutableList.builder();
    private String name = "";

    private Builder() {}

    /**
     * Sets a name for this layer. This name does not affect the contents of the layer.
     *
     * @param name the name
     * @return this
     */
    public Builder setName(String name) {
      this.name = name;
      return this;
    }

    /**
     * Adds an entry to the layer. Only adds the single source file to the exact path in the
     * container file system.
     *
     * <p>For example, {@code addEntry(Paths.get("myfile"), Paths.get("/path/in/container"))} adds a
     * file {@code myfile} to the container file system at {@code /path/in/container}.
     *
     * <p>For example, {@code addEntry(Paths.get("mydirectory"), Paths.get("/path/in/container"))}
     * adds a directory {@code mydirectory/} to the container file system at {@code
     * /path/in/container/}. This does <b>not</b> add the contents of {@code mydirectory}.
     *
     * @param sourceFile the source file to add to the layer
     * @param pathInContainer the path in the container file system corresponding to the {@code
     *     sourceFile} (relative to root {@code /})
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
     * <p>For example, {@code addEntryRecursive(Paths.get("mydirectory",
     * Paths.get("/path/in/container"))} adds {@code mydirectory} to the container file system at
     * {@code /path/in/container} such that {@code mydirectory/subfile} is found at {@code
     * /path/in/container/subfile}.
     *
     * @param sourceFile the source file to add to the layer recursively
     * @param pathInContainer the path in the container file system corresponding to the {@code
     *     sourceFile} (relative to root {@code /})
     * @return this
     * @throws IOException if an exception occurred when recursively listing the directory
     */
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
      return new LayerConfiguration(name, layerEntries.build());
    }
  }

  /**
   * Gets a new {@link Builder} for {@link LayerConfiguration}.
   *
   * @return a new {@link Builder}
   */
  public static Builder builder() {
    return new Builder();
  }

  private final ImmutableList<LayerEntry> layerEntries;
  private final String name;

  /**
   * Use {@link #builder} to instantiate.
   *
   * @param name an optional name for the layer
   * @param layerEntries the list of {@link LayerEntry}s
   */
  private LayerConfiguration(String name, ImmutableList<LayerEntry> layerEntries) {
    this.name = name;
    this.layerEntries = layerEntries;
  }

  /**
   * Gets the name.
   *
   * @return the name
   */
  public String getName() {
    return name;
  }

  /**
   * Gets the list of layer entries.
   *
   * @return the list of layer entries
   */
  public ImmutableList<LayerEntry> getLayerEntries() {
    return layerEntries;
  }
}
