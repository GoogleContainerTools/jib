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
     * Adds an entry to the layer.
     *
     * <p>The source files are specified as a list instead of a set to define the order in which
     * they are added.
     *
     * @param sourceFiles the source files to build from. Source files that are directories will
     *     have all subfiles in the directory added (but not the directory itself)
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
