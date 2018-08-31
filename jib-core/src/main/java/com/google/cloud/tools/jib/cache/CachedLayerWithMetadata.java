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

package com.google.cloud.tools.jib.cache;

import javax.annotation.Nullable;

/** A {@link CachedLayer} with a last modified time. */
public class CachedLayerWithMetadata extends CachedLayer {

  /** Extra layer properties for application layers. */
  @Nullable private final LayerMetadata metadata;

  public CachedLayerWithMetadata(CachedLayer cachedLayer, @Nullable LayerMetadata metadata) {
    super(cachedLayer.getContentFile(), cachedLayer.getBlobDescriptor(), cachedLayer.getDiffId());

    this.metadata = metadata;
  }

  @Nullable
  LayerMetadata getMetadata() {
    return metadata;
  }
}
