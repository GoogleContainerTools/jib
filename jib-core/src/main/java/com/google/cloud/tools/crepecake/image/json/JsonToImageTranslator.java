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
import com.google.cloud.tools.crepecake.image.ReferenceNoDiffIdLayer;
import java.util.ArrayList;
import java.util.List;

/** Translates {@link V21ManifestTemplate} and {@link V22ManifestTemplate} into {@link Image}. */
public class JsonToImageTranslator {

  /** Translates {@link V21ManifestTemplate} to {@link Image}. */
  public static Image toImage(V21ManifestTemplate manifestTemplate)
      throws LayerPropertyNotFoundException, DuplicateLayerException {
    Image image = new Image();

    for (DescriptorDigest digest : manifestTemplate.getLayerDigests()) {
      Layer layer = new DigestOnlyLayer(digest);
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

    List<ReferenceNoDiffIdLayer> layers = new ArrayList<>();
    for (V22ManifestTemplate.LayerObjectTemplate layerObjectTemplate :
        manifestTemplate.getLayers()) {
      BlobDescriptor blobDescriptor =
          new BlobDescriptor(layerObjectTemplate.getSize(), layerObjectTemplate.getDigest());
      layers.add(new ReferenceNoDiffIdLayer(blobDescriptor));
    }
    List<DescriptorDigest> diffIds = containerConfigurationTemplate.getDiffIds();

    if (layers.size() != diffIds.size()) {
      throw new LayerCountMismatchException(
          "Mismatch between image manifest and container configuration");
    }

    for (int layerIndex = 0; layerIndex < layers.size(); layerIndex++) {
      ReferenceNoDiffIdLayer noDiffIdLayer = layers.get(layerIndex);
      DescriptorDigest diffId = diffIds.get(layerIndex);

      Layer layer = new ReferenceLayer(noDiffIdLayer.getBlobDescriptor(), diffId);
      image.addLayer(layer);
    }

    image.setEntrypoint(containerConfigurationTemplate.getContainerEntrypoint());

    for (String environmentVariable : containerConfigurationTemplate.getContainerEnvironment()) {
      image.addEnvironmentVariableDefinition(environmentVariable);
    }

    return image;
  }

  private JsonToImageTranslator() {}
}
