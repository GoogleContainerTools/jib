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
import com.google.cloud.tools.jib.api.buildplan.Platform;
import com.google.cloud.tools.jib.blob.Blob;
import com.google.cloud.tools.jib.blob.BlobDescriptor;
import com.google.cloud.tools.jib.image.Image;
import com.google.cloud.tools.jib.image.Layer;

import javax.annotation.Nullable;

/**
 * Layer prepared from {@link BuildAndCacheApplicationLayerStep} and {@link
 * ObtainBaseImageLayerStep} to hold information about either a base image layer or an application
 * layer.
 */
class PreparedLayer implements Layer {

  enum StateInTarget {
    UNKNOWN,
    EXISTING,
    MISSING
  }

  static class Builder {

    private Layer layer;
    private String name = "unnamed layer";
    private StateInTarget stateInTarget = StateInTarget.UNKNOWN;
    @Nullable private Platform platform;

    Builder(Layer layer) {
      this.layer = layer;
    }

    Builder setName(String name) {
      this.name = name;
      return this;
    }

    /** Sets whether the layer exists in a target destination. */
    Builder setStateInTarget(StateInTarget stateInTarget) {
      this.stateInTarget = stateInTarget;
      return this;
    }

    /** Sets the platform this layer is associated with. */
    Builder setPlatform(@Nullable Platform platform) {
      this.platform = platform;
      return this;
    }

    PreparedLayer build() {
      return new PreparedLayer(layer, name, stateInTarget, platform);
    }
  }

  private final Layer layer;
  private final String name;
  private final StateInTarget stateInTarget;
  @Nullable private final Platform platform;

  private PreparedLayer(Layer layer, String name, StateInTarget stateInTarget, @Nullable Platform platform) {
    this.layer = layer;
    this.name = name;
    this.stateInTarget = stateInTarget;
    this.platform = platform;
  }

  String getName() {
    return name;
  }

  StateInTarget getStateInTarget() {
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

  @Nullable
  public Platform getPlatform() {
    return platform;
  }

  public boolean appliesTo(Image baseImage) {
    return platform == null ||
        (baseImage.getArchitecture().equals(platform.getArchitecture()) && baseImage.getOs().equals(platform.getOs()));
  }
}
