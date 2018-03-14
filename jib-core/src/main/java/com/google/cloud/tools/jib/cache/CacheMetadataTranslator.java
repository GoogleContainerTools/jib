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

package com.google.cloud.tools.jib.cache;

import com.google.cloud.tools.jib.blob.BlobDescriptor;
import com.google.cloud.tools.jib.cache.json.CacheMetadataLayerObjectTemplate;
import com.google.cloud.tools.jib.cache.json.CacheMetadataLayerPropertiesObjectTemplate;
import com.google.cloud.tools.jib.cache.json.CacheMetadataTemplate;
import com.google.cloud.tools.jib.image.LayerPropertyNotFoundException;
import java.nio.file.Path;

/** Translates {@link CacheMetadata} to and from {@link CacheMetadataTemplate}. */
public class CacheMetadataTranslator {

  /** Translates {@link CacheMetadataTemplate} to {@link CacheMetadata}. */
  static CacheMetadata fromTemplate(CacheMetadataTemplate template, Path cacheDirectory)
      throws CacheMetadataCorruptedException {
    try {
      CacheMetadata cacheMetadata = new CacheMetadata();

      // Converts each layer object in the template to a cache metadata layer.
      for (CacheMetadataLayerObjectTemplate layerObjectTemplate : template.getLayers()) {
        if (layerObjectTemplate.getDigest() == null || layerObjectTemplate.getDiffId() == null) {
          throw new IllegalStateException(
              "Cannot translate cache metadata layer without a digest or diffId");
        }

        Path layerContentFile =
            CacheFiles.getLayerFile(cacheDirectory, layerObjectTemplate.getDigest());

        // Gets the properties for a layer. Properties only exist for application layers.
        CacheMetadataLayerPropertiesObjectTemplate propertiesObjectTemplate =
            layerObjectTemplate.getProperties();

        // Constructs the cache metadata layer from a cached layer and layer metadata.
        LayerMetadata layerMetadata = null;
        if (propertiesObjectTemplate != null) {
          layerMetadata =
              new LayerMetadata(
                  propertiesObjectTemplate.getSourceFiles(),
                  propertiesObjectTemplate.getLastModifiedTime());
        }

        CachedLayer cachedLayer =
            new CachedLayer(
                layerContentFile,
                new BlobDescriptor(layerObjectTemplate.getSize(), layerObjectTemplate.getDigest()),
                layerObjectTemplate.getDiffId());

        CachedLayerWithMetadata cachedLayerWithMetadata =
            new CachedLayerWithMetadata(cachedLayer, layerMetadata);
        cacheMetadata.addLayer(cachedLayerWithMetadata);
      }

      return cacheMetadata;

    } catch (LayerPropertyNotFoundException ex) {
      throw new CacheMetadataCorruptedException(ex);
    }
  }

  /** Translates {@link CacheMetadata} to {@link CacheMetadataTemplate}. */
  static CacheMetadataTemplate toTemplate(CacheMetadata cacheMetadata) {
    CacheMetadataTemplate template = new CacheMetadataTemplate();

    for (CachedLayerWithMetadata cachedLayerWithMetadata : cacheMetadata.getLayers()) {
      CacheMetadataLayerObjectTemplate layerObjectTemplate =
          new CacheMetadataLayerObjectTemplate()
              .setSize(cachedLayerWithMetadata.getBlobDescriptor().getSize())
              .setDigest(cachedLayerWithMetadata.getBlobDescriptor().getDigest())
              .setDiffId(cachedLayerWithMetadata.getDiffId());

      if (cachedLayerWithMetadata.getMetadata() != null) {
        layerObjectTemplate.setProperties(
            new CacheMetadataLayerPropertiesObjectTemplate()
                .setSourceFiles(cachedLayerWithMetadata.getMetadata().getSourceFiles())
                .setLastModifiedTime(cachedLayerWithMetadata.getMetadata().getLastModifiedTime()));
      }

      template.addLayer(layerObjectTemplate);
    }

    return template;
  }
}
