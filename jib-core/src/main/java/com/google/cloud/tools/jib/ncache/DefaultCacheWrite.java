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
import java.util.Optional;
import javax.annotation.Nullable;

/** A default implementation of {@link CacheWrite}. */
public class DefaultCacheWrite implements CacheWrite {

  /**
   * Constructs a {@link CacheWrite} with only the layer {@link Blob}.
   *
   * @param layerBlob the layer {@link Blob}
   * @return the new {@link CacheWrite}
   */
  public static CacheWrite layerOnly(Blob layerBlob) {
    return new DefaultCacheWrite(layerBlob, null, null);
  }

  /**
   * Constructs a {@link CacheWrite} with a layer {@link Blob}, an additional selector digest, and a
   * metadata {@link Blob}.
   *
   * @param layerBlob the layer {@link Blob}
   * @param selector the selector digest
   * @param metadataBlob the metadata {@link Blob}
   * @return the new {@link CacheWrite}
   */
  public static CacheWrite withSelectorAndMetadata(
      Blob layerBlob, DescriptorDigest selector, Blob metadataBlob) {
    return new DefaultCacheWrite(layerBlob, selector, metadataBlob);
  }

  private final Blob layerBlob;
  @Nullable private final DescriptorDigest selector;
  @Nullable private final Blob metadataBlob;

  private DefaultCacheWrite(
      Blob layerBlob, @Nullable DescriptorDigest selector, @Nullable Blob metadataBlob) {
    this.layerBlob = layerBlob;
    this.selector = selector;
    this.metadataBlob = metadataBlob;
  }

  @Override
  public Blob getLayerBlob() {
    return layerBlob;
  }

  @Override
  public Optional<DescriptorDigest> getSelector() {
    return Optional.ofNullable(selector);
  }

  @Override
  public Optional<Blob> getMetadataBlob() {
    return Optional.ofNullable(metadataBlob);
  }
}
