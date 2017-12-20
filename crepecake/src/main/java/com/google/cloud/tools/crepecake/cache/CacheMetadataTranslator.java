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
import com.google.cloud.tools.crepecake.cache.json.CacheMetadataTemplate;
import com.google.cloud.tools.crepecake.image.DuplicateLayerException;
import com.google.cloud.tools.crepecake.image.LayerPropertyNotFoundException;
import java.io.File;
import java.nio.file.Path;

/** Translates {@link CacheMetadata} to and from {@link CacheMetadataTemplate}. */
public class CacheMetadataTranslator {

  /** Translates {@link CacheMetadataTemplate} to {@link CacheMetadata}. */
  static CacheMetadata fromTemplate(CacheMetadataTemplate template, Path cacheDirectory)
      throws CacheMetadataCorruptedException {
    try {
      CacheMetadata cacheMetadata = new CacheMetadata();

      for (CacheMetadataLayerObjectTemplate layerObjectTemplate : template.getLayers()) {
        File layerContentFile =
            CacheFiles.getLayerFile(cacheDirectory, layerObjectTemplate.getDigest());

        LayerMetadata layerMetadata;
        switch (layerObjectTemplate.getType()) {
          case BASE:
            layerMetadata =
                new LayerMetadata(layerObjectTemplate.getType(), layerObjectTemplate.getExistsOn());
            break;

          case DEPENDENCIES:
          case RESOURCES:
          case CLASSES:
            layerMetadata =
                new LayerMetadata(
                    layerObjectTemplate.getType(),
                    layerObjectTemplate.getExistsOn(),
                    layerObjectTemplate.getSourceDirectories(),
                    layerObjectTemplate.getLastModifiedTime());
            break;

          default:
            throw new IllegalStateException("The switch should be exhaustive");
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

    } catch (DuplicateLayerException | LayerPropertyNotFoundException ex) {
      throw new CacheMetadataCorruptedException(ex);
    }
  }

  /** Translates {@link CacheMetadata} to {@link CacheMetadataTemplate}. */
  static CacheMetadataTemplate toTemplate(CacheMetadata cacheMetadata) {
    CacheMetadataTemplate template = new CacheMetadataTemplate();

    for (CachedLayerWithMetadata cachedLayerWithMetadata : cacheMetadata.getLayers().asList()) {
      CacheMetadataLayerObjectTemplate layerObjectTemplate = new CacheMetadataLayerObjectTemplate();

      LayerMetadata layerMetadata = cachedLayerWithMetadata.getMetadata();
      layerObjectTemplate.setType(layerMetadata.getType());
      layerObjectTemplate.setSize(cachedLayerWithMetadata.getBlobDescriptor().getSize());
      layerObjectTemplate.setDigest(cachedLayerWithMetadata.getBlobDescriptor().getDigest());
      layerObjectTemplate.setDiffId(cachedLayerWithMetadata.getDiffId());
      layerObjectTemplate.setExistsOn(layerMetadata.getExistsOn());

      switch (layerMetadata.getType()) {
        case DEPENDENCIES:
        case RESOURCES:
        case CLASSES:
          layerObjectTemplate.setSourceDirectories(layerMetadata.getSourceDirectories());
          layerObjectTemplate.setLastModifiedTime(layerMetadata.getLastModifiedTime());
          break;
      }

      template.addLayer(layerObjectTemplate);
    }

    return template;
  }
}
