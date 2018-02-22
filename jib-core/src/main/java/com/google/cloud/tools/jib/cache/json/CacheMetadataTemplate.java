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

package com.google.cloud.tools.jib.cache.json;

import com.google.cloud.tools.jib.json.JsonTemplate;
import java.util.ArrayList;
import java.util.List;

/**
 * JSON template for storing metadata about the cache.
 *
 * <p>Example:
 *
 * <pre>{@code
 * {
 *   "layers": [
 *     {
 *       // This is a base image layer.
 *       "reference": {
 *         "size": 631,
 *         "digest": "sha256:5f70bf18a086007016e948b04aed3b82103a36bea41755b6cddfaf10ace3c6ef",
 *         "diffId": "sha256:b56ae66c29370df48e7377c8f9baa744a3958058a766793f821dadcb144a4647"
 *       }
 *     },
 *     ...
 *     {
 *       // This is an application layer (it has properties).
 *       "reference": {
 *         "size": 223,
 *         "digest": "sha256:8c662931926fa990b41da3c9f42663a537ccd498130030f9149173a0493832ad",
 *         "diffId": "sha256:a3f3e99c29370df48e7377c8f9baa744a3958058a766793f821dadcb144a8372"
 *       }
 *       "properties": {
 *         "sourceFiles": ["build/classes"],
 *         "lastModifiedTime": 255073580723571
 *       }
 *     },
 *     ...
 *   ]
 * }
 * }</pre>
 */
public class CacheMetadataTemplate implements JsonTemplate {

  private final List<CacheMetadataLayerObjectTemplate> layers = new ArrayList<>();

  public List<CacheMetadataLayerObjectTemplate> getLayers() {
    return layers;
  }

  public CacheMetadataTemplate addLayer(CacheMetadataLayerObjectTemplate layer) {
    layers.add(layer);
    return this;
  }
}
