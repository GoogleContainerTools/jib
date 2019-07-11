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

package com.google.cloud.tools.jib.builder.steps;

import com.google.cloud.tools.jib.api.DescriptorDigest;
import com.google.cloud.tools.jib.blob.Blob;
import com.google.cloud.tools.jib.blob.BlobDescriptor;
import com.google.cloud.tools.jib.image.Layer;
import java.util.Optional;

/**
 * Layer prepared from {@link BuildAndCacheApplicationLayerStep} and {@link
 * PullAndCacheBaseImageLayerStep} to hold information about either a base image layer or an
 * application layer.
 */
class PreparedLayer implements Layer {

  static class Builder {

    private Layer layer;
    private String name = "unnamed layer";
    private Optional<Boolean> stateInTarget = Optional.empty();

    Builder(Layer layer) {
      this.layer = layer;
    }

    Builder setName(String name) {
      this.name = name;
      return this;
    }

    /** Sets whether the layer exists in a target destination. Empty (absence) means unknown. */
    Builder setStateInTarget(Optional<Boolean> stateInTarget) {
      this.stateInTarget = stateInTarget;
      return this;
    }

    PreparedLayer build() {
      return new PreparedLayer(layer, name, stateInTarget);
    }
  }

  private final Layer layer;
  private final String name;
  private final Optional<Boolean> stateInTarget;

  private PreparedLayer(Layer layer, String name, Optional<Boolean> stateInTarget) {
    this.layer = layer;
    this.name = name;
    this.stateInTarget = stateInTarget;
  }

  String getName() {
    return name;
  }

  Optional<Boolean> existsInTarget() {
    return stateInTarget;
  }

  @Override
  public Blob getBlob() {
    return layer.getBlob();
  }

  @Override
  public BlobDescriptor getBlobDescriptor() {
    return layer.getBlobDescriptor();
  }

  @Override
  public DescriptorDigest getDiffId() {
    return layer.getDiffId();
  }
}
