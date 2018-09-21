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

package com.google.cloud.tools.jib.ncache;

import com.google.cloud.tools.jib.blob.Blob;
import com.google.cloud.tools.jib.image.DescriptorDigest;
import com.google.common.base.Preconditions;
import java.util.Optional;
import javax.annotation.Nullable;

/** Default implementation of {@link CacheEntry}. */
public class DefaultCacheEntry implements CacheEntry {

  /** Builds a {@link CacheEntry}. */
  public static class Builder {

    @Nullable private DescriptorDigest layerDigest;
    @Nullable private DescriptorDigest layerDiffId;
    private long layerSize = -1;
    @Nullable private Blob layerBlob;
    @Nullable private Blob metadataBlob;

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

    public Builder setMetadataBlob(@Nullable Blob metadataBlob) {
      this.metadataBlob = metadataBlob;
      return this;
    }

    public boolean hasLayerBlob() {
      return layerBlob != null;
    }

    public boolean hasMetadataBlob() {
      return metadataBlob != null;
    }

    public CacheEntry build() {
      return new DefaultCacheEntry(
          Preconditions.checkNotNull(layerDigest, "layerDigest required"),
          Preconditions.checkNotNull(layerDiffId, "layerDiffId required"),
          layerSize,
          Preconditions.checkNotNull(layerBlob, "layerBlob required"),
          metadataBlob);
    }
  }

  /**
   * Creates a new {@link Builder} for a {@link CacheEntry}.
   *
   * @return the new {@link Builder}
   */
  public static Builder builder() {
    return new Builder();
  }

  private final DescriptorDigest layerDigest;
  private final DescriptorDigest layerDiffId;
  private final long layerSize;
  private final Blob layerBlob;
  @Nullable private final Blob metadataBlob;

  private DefaultCacheEntry(
      DescriptorDigest layerDigest,
      DescriptorDigest layerDiffId,
      long layerSize,
      Blob layerBlob,
      @Nullable Blob metadataBlob) {
    this.layerDigest = layerDigest;
    this.layerDiffId = layerDiffId;
    this.layerSize = layerSize;
    this.layerBlob = layerBlob;
    this.metadataBlob = metadataBlob;
  }

  @Override
  public DescriptorDigest getLayerDigest() {
    return layerDigest;
  }

  @Override
  public DescriptorDigest getLayerDiffId() {
    return layerDiffId;
  }

  @Override
  public long getLayerSize() {
    return layerSize;
  }

  @Override
  public Blob getLayerBlob() {
    return layerBlob;
  }

  @Override
  public Optional<Blob> getMetadataBlob() {
    return Optional.ofNullable(metadataBlob);
  }
}
