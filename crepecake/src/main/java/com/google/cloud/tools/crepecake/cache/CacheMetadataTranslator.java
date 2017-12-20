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

import com.google.cloud.tools.crepecake.cache.json.CacheMetadataTemplate;

/** Translates {@link CacheMetadata} to and from {@link CacheMetadataTemplate}. */
public class CacheMetadataTranslator {

  //  /** Translates {@link CacheMetadataTemplate} to {@link CacheMetadata}. */
  //  static CacheMetadata fromTemplate(CacheMetadataTemplate template, Path cacheDirectory)
  //      throws LayerPropertyNotFoundException, DuplicateLayerException {
  //    CacheMetadata cacheMetadata = new CacheMetadata();
  //
  //    // Adds the base image layers from the template.
  //    for (CacheMetadataTemplate.LayerObjectTemplate baseImageLayerTemplate :
  //        template.getBaseImageLayers()) {
  //      File cachedLayerFile =
  //          CacheFiles.getLayerFile(cacheDirectory, baseImageLayerTemplate.getDigest());
  //      TimestampedCachedLayer timestampedCachedLayer =
  //          fromTemplate(baseImageLayerTemplate, cachedLayerFile);
  //      cacheMetadata.addBaseImageLayer(timestampedCachedLayer);
  //    }
  //
  //    // Adds the application layers from the template.
  //    for (CachedLayerType layerType : CachedLayerType.values()) {
  //      CacheMetadataTemplate.LayerObjectTemplate applicationLayerTemplate =
  //          getTemplateLayer(template, layerType);
  //      File layerFile =
  //          CacheFiles.getLayerFile(cacheDirectory, applicationLayerTemplate.getDigest());
  //      TimestampedCachedLayer cachedLayer = fromTemplate(applicationLayerTemplate, layerFile);
  //      cacheMetadata.setApplicationLayer(layerType, cachedLayer);
  //    }
  //
  //    return cacheMetadata;
  //  }
  //
  //  static CacheMetadataTemplate toTemplate(CacheMetadata cacheMetadata) {
  //    CacheMetadataTemplate template = new CacheMetadataTemplate();
  //
  //    // Adds the base image layers to the template.
  //    for (TimestampedCachedLayer layer : cacheMetadata.getBaseImageLayers().asList()) {
  //      CacheMetadataTemplate.LayerObjectTemplate layerObjectTemplate =
  //          new CacheMetadataTemplate.LayerObjectTemplate(
  //              layer.getBlobDescriptor(), layer.getDiffId(), layer.getLastModifiedTime());
  //      template.addBaseImageLayer(layerObjectTemplate);
  //    }
  //
  //    // Adds the application layers to the template.
  //    for (CachedLayerType layerType : CachedLayerType.values()) {
  //      TimestampedCachedLayer layer = cacheMetadata.getApplicationLayer(layerType);
  //      if (null == layer) {
  //        continue;
  //      }
  //      CacheMetadataTemplate.LayerObjectTemplate layerObjectTemplate =
  //          new CacheMetadataTemplate.LayerObjectTemplate(
  //              layer.getBlobDescriptor(), layer.getDiffId(), layer.getLastModifiedTime());
  //
  //      switch (layerType) {
  //        case DEPENDENCIES:
  //          template.setDependenciesLayer(layerObjectTemplate);
  //          break;
  //        case RESOURCES:
  //          template.setResourcesLayer(layerObjectTemplate);
  //          break;
  //        case CLASSES:
  //          template.setClassesLayer(layerObjectTemplate);
  //          break;
  //      }
  //    }
  //
  //    return template;
  //  }
  //
  //  /** Gets a layer from a {@link CacheMetadataTemplate} by type. */
  //  private static CacheMetadataTemplate.LayerObjectTemplate getTemplateLayer(
  //      CacheMetadataTemplate template, CachedLayerType layerType) {
  //    switch (layerType) {
  //      case DEPENDENCIES:
  //        return template.getDependenciesLayer();
  //      case RESOURCES:
  //        return template.getResourcesLayer();
  //      case CLASSES:
  //        return template.getClassesLayer();
  //    }
  //    throw new IllegalStateException("Switch above should be exhaustive");
  //  }
  //
  //  /**
  //   * Translates a {@link CacheMetadataTemplate.LayerObjectTemplate} into a {@link
  //   * TimestampedCachedLayer}.
  //   */
  //  private static TimestampedCachedLayer fromTemplate(
  //      CacheMetadataTemplate.LayerObjectTemplate layerObjectTemplate, File contentTarFile) {
  //    CachedLayer cachedLayer =
  //        new CachedLayer(
  //            contentTarFile,
  //            new BlobDescriptor(layerObjectTemplate.getSize(), layerObjectTemplate.getDigest()),
  //            layerObjectTemplate.getDiffId());
  //    return new TimestampedCachedLayer(cachedLayer, layerObjectTemplate.getLastModifiedTime());
  //  }
}
