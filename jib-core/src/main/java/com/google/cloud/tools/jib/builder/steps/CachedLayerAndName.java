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

import com.google.cloud.tools.jib.cache.CachedLayer;
import javax.annotation.Nullable;

/** Simple structure to hold a pair of {#link CachedLayer} and its string name. */
class CachedLayerAndName {

  private CachedLayer cachedLayer;
  @Nullable private String name;

  CachedLayerAndName(CachedLayer cachedLayer, @Nullable String name) {
    this.cachedLayer = cachedLayer;
    this.name = name;
  }

  CachedLayer getCachedLayer() {
    return cachedLayer;
  }

  @Nullable
  String getName() {
    return name;
  }
}
