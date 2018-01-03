/*
 * Copyright 2017 Google Inc.
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

package com.google.cloud.tools.crepecake.image;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Holds the layers for an image. Makes sure that each layer is only added once. */
public class ImageLayers<T extends Layer> {

  /** The layers of the image, in the order in which they are applied. */
  private final List<T> layers = new ArrayList<>();

  /** Keeps track of the layers already added. */
  private final Set<DescriptorDigest> layerDigests = new HashSet<>();

  /** Returns a read-only view of the image layers. */
  public List<T> getLayers() {
    return Collections.unmodifiableList(layers);
  }

  /**
   * Adds a layer.
   *
   * @param layer the layer to add
   * @throws DuplicateLayerException if the layer has already been added
   */
  public void add(T layer) throws DuplicateLayerException, LayerPropertyNotFoundException {
    if (layerDigests.contains(layer.getBlobDescriptor().getDigest())) {
      throw new DuplicateLayerException("Cannot add the same layer more than once");
    }

    layerDigests.add(layer.getBlobDescriptor().getDigest());
    layers.add(layer);
  }
}
