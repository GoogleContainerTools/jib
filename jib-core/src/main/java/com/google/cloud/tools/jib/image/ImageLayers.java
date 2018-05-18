/*
 * Copyright 2017 Google LLC. All rights reserved.
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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.Iterator;
import javax.annotation.Nullable;

/** Holds the layers for an image. Makes sure that each layer is only added once. */
public class ImageLayers<T extends Layer> implements Iterable<T> {

  public static class Builder<T extends Layer> {

    private final ImmutableList.Builder<T> layersBuilder = ImmutableList.builder();
    private final ImmutableSet.Builder<DescriptorDigest> layerDigestsBuilder =
        ImmutableSet.builder();

    /** The last layer added. */
    @Nullable private T lastLayer;

    /**
     * Adds a layer.
     *
     * @param layer the layer to add
     */
    public Builder<T> add(T layer) throws LayerPropertyNotFoundException {
      // Doesn't add the layer if the last layer is the same.
      if (!isSameAsLastLayer(layer)) {
        layerDigestsBuilder.add(layer.getBlobDescriptor().getDigest());
        layersBuilder.add(layer);
        lastLayer = layer;
      }

      return this;
    }

    /** Adds all layers in {@code layers}. */
    public <U extends T> Builder<T> addAll(ImageLayers<U> layers)
        throws LayerPropertyNotFoundException {
      for (U layer : layers) {
        add(layer);
      }
      return this;
    }

    public ImageLayers<T> build() {
      return new ImageLayers<>(layersBuilder.build(), layerDigestsBuilder.build());
    }

    /** @return {@code true} if {@code layer} is the same as the last layer in {@link #layers} */
    private boolean isSameAsLastLayer(T layer) throws LayerPropertyNotFoundException {
      return lastLayer != null
          && layer
              .getBlobDescriptor()
              .getDigest()
              .equals(lastLayer.getBlobDescriptor().getDigest());
    }
  }

  public static <U extends Layer> Builder<U> builder() {
    return new Builder<>();
  }

  /** The layers of the image, in the order in which they are applied. */
  private final ImmutableList<T> layers;

  /** Keeps track of the layers already added. */
  private final ImmutableSet<DescriptorDigest> layerDigests;

  private ImageLayers(ImmutableList<T> layers, ImmutableSet<DescriptorDigest> layerDigests) {
    this.layers = layers;
    this.layerDigests = layerDigests;
  }

  /** Returns a read-only view of the image layers. */
  public ImmutableList<T> getLayers() {
    return layers;
  }

  /** @return the layer count */
  public int size() {
    return layers.size();
  }

  public boolean isEmpty() {
    return layers.isEmpty();
  }

  /** @return the layer at the specified index */
  public T get(int index) {
    return layers.get(index);
  }

  /** @return the layer by digest, or {@code null} if not found */
  @Nullable
  public T get(DescriptorDigest digest) throws LayerPropertyNotFoundException {
    if (!has(digest)) {
      return null;
    }
    for (T layer : layers) {
      if (layer.getBlobDescriptor().getDigest().equals(digest)) {
        return layer;
      }
    }
    throw new IllegalStateException("Layer digest exists but layer not found");
  }

  /** @return true if the layer with the specified digest exists; false otherwise */
  public boolean has(DescriptorDigest digest) {
    return layerDigests.contains(digest);
  }

  @Override
  public Iterator<T> iterator() {
    return getLayers().iterator();
  }
}
