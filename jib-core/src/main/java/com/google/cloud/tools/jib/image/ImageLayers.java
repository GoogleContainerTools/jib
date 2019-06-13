/*
 * Copyright 2017 Google LLC.
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

package com.google.cloud.tools.jib.image;

import com.google.cloud.tools.jib.api.DescriptorDigest;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;

/** Holds the layers for an image. */
public class ImageLayers implements Iterable<Layer> {

  public static class Builder {

    private final List<Layer> layers = new ArrayList<>();
    private final ImmutableSet.Builder<DescriptorDigest> layerDigestsBuilder =
        ImmutableSet.builder();
    private boolean removeDuplicates = false;

    /**
     * Adds a layer. Removes any prior occurrences of the same layer.
     *
     * <p>Note that only subclasses of {@link Layer} that implement {@code equals/hashCode} will be
     * guaranteed to not be duplicated.
     *
     * @param layer the layer to add
     * @return this
     * @throws LayerPropertyNotFoundException if adding the layer fails
     */
    public Builder add(Layer layer) throws LayerPropertyNotFoundException {
      layerDigestsBuilder.add(layer.getBlobDescriptor().getDigest());
      layers.add(layer);
      return this;
    }

    /**
     * Adds all layers in {@code layers}.
     *
     * @param layers the layers to add
     * @return this
     * @throws LayerPropertyNotFoundException if adding a layer fails
     */
    public Builder addAll(ImageLayers layers) throws LayerPropertyNotFoundException {
      for (Layer layer : layers) {
        add(layer);
      }
      return this;
    }

    /**
     * Remove any duplicate layers, keeping the last occurrence of the layer.
     *
     * @return this
     */
    public Builder removeDuplicates() {
      removeDuplicates = true;
      return this;
    }

    public ImageLayers build() {
      if (!removeDuplicates) {
        return new ImageLayers(ImmutableList.copyOf(layers), layerDigestsBuilder.build());
      }

      // LinkedHashSet maintains the order but keeps the first occurrence. Keep last occurrence by
      // adding elements in reverse, and then reversing the result
      Set<Layer> dedupedButReversed = new LinkedHashSet<>(Lists.reverse(this.layers));
      ImmutableList<Layer> deduped = ImmutableList.copyOf(dedupedButReversed).reverse();
      return new ImageLayers(deduped, layerDigestsBuilder.build());
    }
  }

  public static Builder builder() {
    return new Builder();
  }

  /** The layers of the image, in the order in which they are applied. */
  private final ImmutableList<Layer> layers;

  /** Keeps track of the layers already added. */
  private final ImmutableSet<DescriptorDigest> layerDigests;

  private ImageLayers(ImmutableList<Layer> layers, ImmutableSet<DescriptorDigest> layerDigests) {
    this.layers = layers;
    this.layerDigests = layerDigests;
  }

  /** @return a read-only view of the image layers. */
  public ImmutableList<Layer> getLayers() {
    return layers;
  }

  /** @return the layer count */
  public int size() {
    return layers.size();
  }

  public boolean isEmpty() {
    return layers.isEmpty();
  }

  /**
   * @param index the index of the layer to get
   * @return the layer at the specified index
   */
  public Layer get(int index) {
    return layers.get(index);
  }

  /**
   * @param digest the digest used to retrieve the layer
   * @return the layer found, or {@code null} if not found
   * @throws LayerPropertyNotFoundException if getting the layer's blob descriptor fails
   */
  @Nullable
  public Layer get(DescriptorDigest digest) throws LayerPropertyNotFoundException {
    if (!has(digest)) {
      return null;
    }
    for (Layer layer : layers) {
      if (layer.getBlobDescriptor().getDigest().equals(digest)) {
        return layer;
      }
    }
    throw new IllegalStateException("Layer digest exists but layer not found");
  }

  /**
   * @param digest the digest to check for
   * @return true if the layer with the specified digest exists; false otherwise
   */
  public boolean has(DescriptorDigest digest) {
    return layerDigests.contains(digest);
  }

  @Override
  public Iterator<Layer> iterator() {
    return getLayers().iterator();
  }
}
