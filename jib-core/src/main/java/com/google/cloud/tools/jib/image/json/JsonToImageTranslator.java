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

package com.google.cloud.tools.jib.image.json;

import com.google.cloud.tools.jib.blob.BlobDescriptor;
import com.google.cloud.tools.jib.image.DescriptorDigest;
import com.google.cloud.tools.jib.image.DigestOnlyLayer;
import com.google.cloud.tools.jib.image.DuplicateLayerException;
import com.google.cloud.tools.jib.image.Image;
import com.google.cloud.tools.jib.image.Layer;
import com.google.cloud.tools.jib.image.LayerCountMismatchException;
import com.google.cloud.tools.jib.image.LayerPropertyNotFoundException;
import com.google.cloud.tools.jib.image.ReferenceLayer;
import com.google.cloud.tools.jib.image.ReferenceNoDiffIdLayer;
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
    List<ReferenceNoDiffIdLayer> layers = new ArrayList<>();
    for (V22ManifestTemplate.LayerObjectTemplate layerObjectTemplate :
        manifestTemplate.getLayers()) {
      layers.add(new ReferenceNoDiffIdLayer(new BlobDescriptor(layerObjectTemplate.getSize(), layerObjectTemplate.getDigest())));
    }

    return toImage(containerConfigurationTemplate, layers);
  }

  /**
   * Translates {@link OCIManifestTemplate} to {@link Image}. Uses the corresponding {@link
   * ContainerConfigurationTemplate} to get the layer diff IDs.
   */
  public static Image toImage(
      OCIManifestTemplate manifestTemplate,
      ContainerConfigurationTemplate containerConfigurationTemplate) throws LayerPropertyNotFoundException, DuplicateLayerException, LayerCountMismatchException {
    List<ReferenceNoDiffIdLayer> layers = new ArrayList<>();
    for (OCIManifestTemplate.ContentDescriptorTemplate contentDescriptorTemplate :
        manifestTemplate.getLayers()) {
      layers.add(new ReferenceNoDiffIdLayer(new BlobDescriptor(contentDescriptorTemplate.getSize(), contentDescriptorTemplate.getDigest())));
    }

    return toImage(containerConfigurationTemplate, layers);
  }

  /** Helper method for creating an image from a container configuration and layers. */
  private static Image toImage(ContainerConfigurationTemplate containerConfigurationTemplate, List<ReferenceNoDiffIdLayer> layers) throws LayerCountMismatchException, LayerPropertyNotFoundException, DuplicateLayerException {
    Image image = new Image();

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
