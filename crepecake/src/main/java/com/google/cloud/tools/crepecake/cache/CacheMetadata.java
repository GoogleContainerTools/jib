/*
 * Copyright 2017 Google Inc.
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

package com.google.cloud.tools.crepecake.cache;

import com.google.cloud.tools.crepecake.image.DuplicateLayerException;
import com.google.cloud.tools.crepecake.image.ImageLayers;
import com.google.cloud.tools.crepecake.image.LayerPropertyNotFoundException;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * The cache stores all the BLOBs as separate files and the cache metadata contains information
 * about each BLOB.
 */
public class CacheMetadata {

  private final ImageLayers<TimestampedCachedLayer> baseImageLayers = new ImageLayers<>();

  private Map<CachedLayerType, TimestampedCachedLayer> applicationLayers = new HashMap<>();

  public ImageLayers<TimestampedCachedLayer> getBaseImageLayers() {
    return baseImageLayers;
  }

  public void addBaseImageLayer(TimestampedCachedLayer layer)
      throws LayerPropertyNotFoundException, DuplicateLayerException {
    baseImageLayers.add(layer);
  }

  @Nullable
  public TimestampedCachedLayer getApplicationLayer(CachedLayerType layerType) {
    return applicationLayers.get(layerType);
  }

  public void setApplicationLayer(CachedLayerType layerType, TimestampedCachedLayer layer) {
    applicationLayers.put(layerType, layer);
  }
}
