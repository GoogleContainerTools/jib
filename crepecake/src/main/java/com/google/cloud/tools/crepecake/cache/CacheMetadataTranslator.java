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

import com.google.cloud.tools.crepecake.blob.BlobDescriptor;
import com.google.cloud.tools.crepecake.cache.json.CacheMetadataLayerObjectTemplate;
import com.google.cloud.tools.crepecake.cache.json.CacheMetadataLayerPropertiesObjectTemplate;
import com.google.cloud.tools.crepecake.cache.json.CacheMetadataTemplate;
import com.google.cloud.tools.crepecake.image.DuplicateLayerException;
import com.google.cloud.tools.crepecake.image.LayerPropertyNotFoundException;
import java.io.File;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

/** Translates {@link CacheMetadata} to and from {@link CacheMetadataTemplate}. */
public class CacheMetadataTranslator {

  /** Translates {@link CacheMetadataTemplate} to {@link CacheMetadata}. */
  static CacheMetadata fromTemplate(CacheMetadataTemplate template, Path cacheDirectory)
      throws CacheMetadataCorruptedException {
    try {
      CacheMetadata cacheMetadata = new CacheMetadata();

      // Converts each layer object in the template to a cache metadata layer.
      for (CacheMetadataLayerObjectTemplate layerObjectTemplate : template.getLayers()) {
        File layerContentFile =
            CacheFiles.getLayerFile(cacheDirectory, layerObjectTemplate.getDigest());

        // Gets the properties for a layer. Properties only exist for application layers.
        CacheMetadataLayerPropertiesObjectTemplate propertiesObjectTemplate =
            layerObjectTemplate.getProperties();
        List<String> sourceFiles = Collections.emptyList();
        long lastModifiedTime = -1;
        if (propertiesObjectTemplate != null) {
          sourceFiles = propertiesObjectTemplate.getSourceFiles();
          lastModifiedTime = propertiesObjectTemplate.getLastModifiedTime();
        }

        // Constructs the cache metadata layer from a cached layer and layer metadata.
        LayerMetadata layerMetadata =
            new LayerMetadata(layerObjectTemplate.getType(), sourceFiles, lastModifiedTime);

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

    } catch (DuplicateLayerException | LayerPropertyNotFoundException ex) {
      throw new CacheMetadataCorruptedException(ex);
    }
  }

  /** Translates {@link CacheMetadata} to {@link CacheMetadataTemplate}. */
  static CacheMetadataTemplate toTemplate(CacheMetadata cacheMetadata) {
    CacheMetadataTemplate template = new CacheMetadataTemplate();

    for (CachedLayerWithMetadata cachedLayerWithMetadata : cacheMetadata.getLayers().getLayers()) {
      LayerMetadata layerMetadata = cachedLayerWithMetadata.getMetadata();

      CacheMetadataLayerObjectTemplate layerObjectTemplate =
          new CacheMetadataLayerObjectTemplate()
              .setType(layerMetadata.getType())
              .setSize(cachedLayerWithMetadata.getBlobDescriptor().getSize())
              .setDigest(cachedLayerWithMetadata.getBlobDescriptor().getDigest())
              .setDiffId(cachedLayerWithMetadata.getDiffId());

      switch (layerMetadata.getType()) {
        case DEPENDENCIES:
        case RESOURCES:
        case CLASSES:
          CacheMetadataLayerPropertiesObjectTemplate propertiesTemplate =
              new CacheMetadataLayerPropertiesObjectTemplate()
                  .setSourceFiles(layerMetadata.getSourceFiles())
                  .setLastModifiedTime(layerMetadata.getLastModifiedTime());
          layerObjectTemplate.setProperties(propertiesTemplate);
          break;
      }

      template.addLayer(layerObjectTemplate);
    }

    return template;
  }
}
