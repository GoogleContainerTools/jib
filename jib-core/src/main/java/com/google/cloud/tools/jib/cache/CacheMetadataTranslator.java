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

import com.google.cloud.tools.jib.blob.BlobDescriptor;
import com.google.cloud.tools.jib.cache.json.CacheMetadataLayerObjectTemplate;
import com.google.cloud.tools.jib.cache.json.CacheMetadataLayerPropertiesObjectTemplate;
import com.google.cloud.tools.jib.cache.json.CacheMetadataLayerPropertiesObjectTemplate.LayerEntryTemplate;
import com.google.cloud.tools.jib.cache.json.CacheMetadataTemplate;
import com.google.cloud.tools.jib.image.LayerEntry;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/** Translates {@link CacheMetadata} to and from {@link CacheMetadataTemplate}. */
public class CacheMetadataTranslator {

  /** Translates {@link CacheMetadataTemplate} to {@link CacheMetadata}. */
  static CacheMetadata fromTemplate(CacheMetadataTemplate template, Path cacheDirectory)
      throws CacheMetadataCorruptedException {
    CacheMetadata.Builder cacheMetadataBuilder = CacheMetadata.builder();

    // Converts each layer object in the template to a cache metadata layer.
    for (CacheMetadataLayerObjectTemplate layerObjectTemplate : template.getLayers()) {
      if (layerObjectTemplate.getDigest() == null || layerObjectTemplate.getDiffId() == null) {
        throw new CacheMetadataCorruptedException(
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
        // Converts the layer entry templates to layer entries.
        ImmutableList.Builder<LayerEntry> layerEntries =
            ImmutableList.builderWithExpectedSize(
                propertiesObjectTemplate.getLayerEntries().size());
        for (LayerEntryTemplate layerEntryTemplate : propertiesObjectTemplate.getLayerEntries()) {
          if (layerEntryTemplate.getSourceFileString() == null
              || layerEntryTemplate.getExtractionPathString() == null) {
            throw new CacheMetadataCorruptedException(
                "Cannot translate cache metadata layer entry without source files or extraction path");
          }
          layerEntries.add(
              new LayerEntry(
                  Paths.get(layerEntryTemplate.getSourceFileString()),
                  Paths.get(layerEntryTemplate.getExtractionPathString())));
        }

        layerMetadata =
            LayerMetadata.from(
                layerEntries.build(), propertiesObjectTemplate.getLastModifiedTime());
      }

      CachedLayer cachedLayer =
          new CachedLayer(
              layerContentFile,
              new BlobDescriptor(layerObjectTemplate.getSize(), layerObjectTemplate.getDigest()),
              layerObjectTemplate.getDiffId());

      CachedLayerWithMetadata cachedLayerWithMetadata =
          new CachedLayerWithMetadata(cachedLayer, layerMetadata);
      cacheMetadataBuilder.addLayer(cachedLayerWithMetadata);
    }

    return cacheMetadataBuilder.build();
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
        // Constructs the layer entry templates to add to the layer object template.
        ImmutableList<LayerMetadata.LayerMetadataEntry> metadataEntries =
            cachedLayerWithMetadata.getMetadata().getEntries();
        List<LayerEntryTemplate> layerEntryTemplates = new ArrayList<>(metadataEntries.size());
        for (LayerMetadata.LayerMetadataEntry metadataEntry : metadataEntries) {
          layerEntryTemplates.add(
              new LayerEntryTemplate(
                  metadataEntry.getAbsoluteSourceFileString(),
                  metadataEntry.getAbsoluteExtractionPathString()));
        }

        layerObjectTemplate.setProperties(
            new CacheMetadataLayerPropertiesObjectTemplate()
                .setLayerEntries(layerEntryTemplates)
                .setLastModifiedTime(cachedLayerWithMetadata.getMetadata().getLastModifiedTime()));
      }

      template.addLayer(layerObjectTemplate);
    }

    return template;
  }
}
