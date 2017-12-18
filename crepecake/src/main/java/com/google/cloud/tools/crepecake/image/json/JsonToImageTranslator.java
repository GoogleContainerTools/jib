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

package com.google.cloud.tools.crepecake.image.json;

import com.google.cloud.tools.crepecake.blob.BlobDescriptor;
import com.google.cloud.tools.crepecake.image.DescriptorDigest;
import com.google.cloud.tools.crepecake.image.DigestOnlyLayer;
import com.google.cloud.tools.crepecake.image.DuplicateLayerException;
import com.google.cloud.tools.crepecake.image.Image;
import com.google.cloud.tools.crepecake.image.Layer;
import com.google.cloud.tools.crepecake.image.LayerCountMismatchException;
import com.google.cloud.tools.crepecake.image.LayerPropertyNotFoundException;
import com.google.cloud.tools.crepecake.image.ReferenceLayer;
import com.google.common.collect.ImmutableList;

// TODO: Add translation of container configuration config.
/** Translates {@link V21ManifestTemplate} and {@link V22ManifestTemplate} into {@link Image}. */
public abstract class JsonToImageTranslator {

  /** Translates {@link V21ManifestTemplate} to {@link Image}. */
  public static Image toImage(V21ManifestTemplate manifestTemplate)
      throws LayerPropertyNotFoundException, DuplicateLayerException {
    Image image = new Image();

    for (V21ManifestTemplate.LayerObjectTemplate layerObjectTemplate :
        manifestTemplate.getFsLayers()) {
      Layer layer = new DigestOnlyLayer(layerObjectTemplate.getDigest());
      image.addLayer(layer);
    }

    return image;
  }

  /**
   * Translates {@link V22ManifestTemplate} to {@link Image}. Uses the corresponding {@link
   * ContainerConfigurationTemplate} to get the layer diff IDs.
   */
  public static Image toImage(
      V22ManifestTemplate manifestTemplate,
      ContainerConfigurationTemplate containerConfigurationTemplate)
      throws LayerCountMismatchException, LayerPropertyNotFoundException, DuplicateLayerException {
    Image image = new Image();

    ImmutableList<V22ManifestTemplate.LayerObjectTemplate> layerObjectTemplates =
        manifestTemplate.getLayers();
    ImmutableList<DescriptorDigest> diffIds = containerConfigurationTemplate.getDiffIds();

    if (layerObjectTemplates.size() != diffIds.size()) {
      throw new LayerCountMismatchException(
          "Mismatch between image manifest and container configuration");
    }

    for (int layerIndex = 0; layerIndex < layerObjectTemplates.size(); layerIndex++) {
      V22ManifestTemplate.LayerObjectTemplate layerObjectTemplate =
          layerObjectTemplates.get(layerIndex);
      DescriptorDigest diffId = diffIds.get(layerIndex);

      BlobDescriptor blobDescriptor =
          new BlobDescriptor(layerObjectTemplate.getSize(), layerObjectTemplate.getDigest());
      Layer layer = new ReferenceLayer(blobDescriptor, diffId);
      image.addLayer(layer);
    }

    return image;
  }
}
