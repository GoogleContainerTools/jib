package com.google.cloud.tools.jib.builder.steps;

import com.google.cloud.tools.jib.cache.CachedLayer;
import javax.annotation.Nullable;

/** Simple structure to hold the result pair of {#link CachedLayer} and its name. */
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
