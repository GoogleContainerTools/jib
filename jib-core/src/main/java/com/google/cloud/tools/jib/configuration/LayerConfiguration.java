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
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.List;

/** Configures how to build a layer in the container image. */
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
     * Adds an entry to the layer.
     *
     * <p>The source files are specified as a list instead of a set to define the order in which
     * they are added.
     *
     * <p>Source files that are directories will be recursively copied. For example, if the source
     * files are:
     *
     * <ul>
     *   <li>{@code fileA}
     *   <li>{@code fileB}
     *   <li>{@code directory/}
     * </ul>
     *
     * and the destination to copy to is {@code /path/in/container}, then the new layer will have
     * the following entries for the container file system:
     *
     * <ul>
     *   <li>{@code /path/in/container/fileA}
     *   <li>{@code /path/in/container/fileB}
     *   <li>{@code /path/in/container/directory/}
     *   <li>{@code /path/in/container/directory/...} (all contents of {@code directory/})
     * </ul>
     *
     * @param sourceFiles the source files to build from. Source files that are directories will
     *     have the directory added recursively
     * @param destinationOnImage Unix-style path to add the source files to in the container image
     *     filesystem
     * @return this
     */
    public Builder addEntry(List<Path> sourceFiles, String destinationOnImage) {
      Preconditions.checkArgument(!sourceFiles.contains(null));
      this.layerEntries.add(new LayerEntry(ImmutableList.copyOf(sourceFiles), destinationOnImage));
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
   * Constructs a new layer configuration.
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
