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

package com.google.cloud.tools.jib.cache;

import com.google.cloud.tools.jib.api.DescriptorDigest;
import com.google.cloud.tools.jib.blob.Blob;
import com.google.cloud.tools.jib.blob.BlobDescriptor;
import com.google.cloud.tools.jib.image.Layer;
import com.google.common.base.Preconditions;
import javax.annotation.Nullable;

/** Default implementation of {@link CachedLayer}. */
public class CachedLayer implements Layer {

  /** Builds a {@link CachedLayer}. */
  static class Builder {

    @Nullable private DescriptorDigest layerDigest;
    @Nullable private DescriptorDigest layerDiffId;
    private long layerSize = -1;
    @Nullable private Blob layerBlob;

    private Builder() {}

    Builder setLayerDigest(DescriptorDigest layerDigest) {
      this.layerDigest = layerDigest;
      return this;
    }

    Builder setLayerDiffId(DescriptorDigest layerDiffId) {
      this.layerDiffId = layerDiffId;
      return this;
    }

    Builder setLayerSize(long layerSize) {
      this.layerSize = layerSize;
      return this;
    }

    Builder setLayerBlob(Blob layerBlob) {
      this.layerBlob = layerBlob;
      return this;
    }

    boolean hasLayerBlob() {
      return layerBlob != null;
    }

    CachedLayer build() {
      return new CachedLayer(
          Preconditions.checkNotNull(layerDigest, "layerDigest required"),
          Preconditions.checkNotNull(layerDiffId, "layerDiffId required"),
          layerSize,
          Preconditions.checkNotNull(layerBlob, "layerBlob required"));
    }
  }

  /**
   * Creates a new {@link Builder} for a {@link CachedLayer}.
   *
   * @return the new {@link Builder}
   */
  static Builder builder() {
    return new Builder();
  }

  private final DescriptorDigest layerDiffId;
  private final BlobDescriptor blobDescriptor;
  private final Blob layerBlob;

  private CachedLayer(
      DescriptorDigest layerDigest, DescriptorDigest layerDiffId, long layerSize, Blob layerBlob) {
    this.layerDiffId = layerDiffId;
    this.layerBlob = layerBlob;
    this.blobDescriptor = new BlobDescriptor(layerSize, layerDigest);
  }

  public DescriptorDigest getDigest() {
    return blobDescriptor.getDigest();
  }

  public long getSize() {
    return blobDescriptor.getSize();
  }

  @Override
  public DescriptorDigest getDiffId() {
    return layerDiffId;
  }

  @Override
  public Blob getBlob() {
    return layerBlob;
  }

  @Override
  public BlobDescriptor getBlobDescriptor() {
    return blobDescriptor;
  }
}
