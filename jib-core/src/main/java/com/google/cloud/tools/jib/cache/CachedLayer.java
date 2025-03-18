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
import com.google.cloud.tools.jib.api.buildplan.CompressionAlgorithm;
import com.google.cloud.tools.jib.blob.Blob;
import com.google.cloud.tools.jib.blob.BlobDescriptor;
import com.google.cloud.tools.jib.image.Layer;
import com.google.common.base.Preconditions;
import javax.annotation.Nullable;

/** A reference to an image layer that is in the Cache. */
public class CachedLayer implements Layer {

  /** Builds a {@link CachedLayer}. */
  public static class Builder {

    @Nullable private DescriptorDigest layerDigest;
    @Nullable private DescriptorDigest layerDiffId;
    private long layerSize = -1;
    @Nullable private Blob layerBlob;
    @Nullable private CompressionAlgorithm compressionAlgorithm;

    private Builder() {}

    public Builder setLayerDigest(DescriptorDigest layerDigest) {
      this.layerDigest = layerDigest;
      return this;
    }

    public Builder setLayerDiffId(DescriptorDigest layerDiffId) {
      this.layerDiffId = layerDiffId;
      return this;
    }

    public Builder setLayerSize(long layerSize) {
      this.layerSize = layerSize;
      return this;
    }

    public Builder setLayerBlob(Blob layerBlob) {
      this.layerBlob = layerBlob;
      return this;
    }

    public Builder setCompressionAlgorithm(CompressionAlgorithm compressionAlgorithm) {
      this.compressionAlgorithm = compressionAlgorithm;
      return this;
    }

    boolean hasLayerBlob() {
      return layerBlob != null;
    }

    /**
     * Creates a CachedLayer instance.
     *
     * @return a new cached layer
     */
    public CachedLayer build() {
      return new CachedLayer(
          Preconditions.checkNotNull(layerDigest, "layerDigest required"),
          Preconditions.checkNotNull(layerDiffId, "layerDiffId required"),
          layerSize,
          Preconditions.checkNotNull(layerBlob, "layerBlob required"),
          Preconditions.checkNotNull(compressionAlgorithm, "compressionAlgorithm required"));
    }
  }

  /**
   * Creates a new {@link Builder} for a {@link CachedLayer}.
   *
   * @return the new {@link Builder}
   */
  public static Builder builder() {
    return new Builder();
  }

  private final DescriptorDigest layerDiffId;
  private final BlobDescriptor blobDescriptor;
  private final Blob layerBlob;
  private final CompressionAlgorithm compressionAlgorithm;

  private CachedLayer(
      DescriptorDigest layerDigest,
      DescriptorDigest layerDiffId,
      long layerSize,
      Blob layerBlob,
      CompressionAlgorithm compressionAlgorithm) {
    this.layerDiffId = layerDiffId;
    this.layerBlob = layerBlob;
    this.blobDescriptor = new BlobDescriptor(layerSize, layerDigest);
    this.compressionAlgorithm = compressionAlgorithm;
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
  public CompressionAlgorithm getCompressionAlgorithm() {
    return compressionAlgorithm;
  }

  @Override
  public BlobDescriptor getBlobDescriptor() {
    return blobDescriptor;
  }
}
