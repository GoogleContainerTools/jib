/*
 * Copyright 2018 Google LLC. All rights reserved.
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
import jdk.nashorn.internal.ir.annotations.Immutable;

/** Configures how to build a layer in the container image. */
public class LayerConfiguration {

  /** Builds a {@link LayerConfiguration}. */
  public static class Builder {

    private final ImmutableList.Builder<LayerEntry> layerEntries = ImmutableList.builder();

    private Builder() {}

    /**
     * Adds an entry to the layer.
     *
     * @param layerEntry the entry
     * @return this
     */
    public Builder addLayerEntry(LayerEntry layerEntry) {
      this.layerEntries.add(layerEntry);
      return this;
    }

    /**
     * Returns the built {@link LayerConfiguration}.
     *
     * @return the built {@link LayerConfiguration}
     */
    public LayerConfiguration build() {
      return new LayerConfiguration(layerEntries.build());
    }
  }

  public static Builder builder() {
    return new Builder();
  }

  private final ImmutableList<LayerEntry> layerEntries;

  /**
   * Constructs a new layer configuration.
   *
   * @param layerEntries the list of {@link LayerEntry}s
   */
  private LayerConfiguration(ImmutableList<LayerEntry> layerEntries) {
    this.layerEntries = layerEntries;
  }

  public ImmutableList<LayerEntry> getLayerEntries() {
    return layerEntries;
  }
}
