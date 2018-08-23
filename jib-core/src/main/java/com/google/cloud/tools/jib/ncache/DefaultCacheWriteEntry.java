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

package com.google.cloud.tools.jib.ncache;

import com.google.cloud.tools.jib.blob.Blob;
import com.google.cloud.tools.jib.image.DescriptorDigest;
import java.util.Optional;
import javax.annotation.Nullable;

public class DefaultCacheWriteEntry implements CacheWriteEntry {

  public static DefaultCacheWriteEntry layerOnly(Blob layerBlob) {
    return new DefaultCacheWriteEntry(layerBlob, null, null);
  }

  public static DefaultCacheWriteEntry withSelectorAndMetadata(
      Blob layerBlob, DescriptorDigest selector, Blob metadataBlob) {
    return new DefaultCacheWriteEntry(layerBlob, selector, metadataBlob);
  }

  private final Blob layerBlob;
  @Nullable private final DescriptorDigest selector;
  @Nullable private final Blob metadataBlob;

  private DefaultCacheWriteEntry(Blob layerBlob, @Nullable DescriptorDigest selector, @Nullable Blob metadataBlob) {
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
