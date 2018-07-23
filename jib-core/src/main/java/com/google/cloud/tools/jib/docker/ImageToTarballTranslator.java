/*
 * Copyright 2018 Google LLC. All rights reserved.
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

package com.google.cloud.tools.jib.docker;

import com.google.cloud.tools.jib.blob.Blob;
import com.google.cloud.tools.jib.blob.Blobs;
import com.google.cloud.tools.jib.cache.CachedLayer;
import com.google.cloud.tools.jib.docker.json.DockerLoadManifestTemplate;
import com.google.cloud.tools.jib.image.Image;
import com.google.cloud.tools.jib.image.ImageReference;
import com.google.cloud.tools.jib.image.json.ImageToJsonTranslator;
import com.google.cloud.tools.jib.json.JsonTemplateMapper;
import com.google.cloud.tools.jib.tar.TarStreamBuilder;
import java.io.IOException;
import java.nio.file.Path;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;

/** Translates an {@link Image} to a tarball that can be loaded into Docker. */
public class ImageToTarballTranslator {

  /** File name for the container configuration in the tarball. */
  private static final String CONTAINER_CONFIGURATION_JSON_FILE_NAME = "config.json";
  /** File name for the manifest in the tarball. */
  private static final String MANIFEST_JSON_FILE_NAME = "manifest.json";

  private final Image<CachedLayer> image;

  /**
   * Instantiate with an {@link Image}.
   *
   * @param image the image to convert into a tarball.
   */
  public ImageToTarballTranslator(Image<CachedLayer> image) {
    this.image = image;
  }

  public Blob toTarballBlob(ImageReference imageReference) throws IOException {
    TarStreamBuilder tarStreamBuilder = new TarStreamBuilder();
    DockerLoadManifestTemplate manifestTemplate = new DockerLoadManifestTemplate();

    // Adds all the layers to the tarball and manifest.
    for (CachedLayer layer : image.getLayers()) {
      Path layerContentFile = layer.getContentFile();
      String layerName = layerContentFile.getFileName().toString();

      tarStreamBuilder.addTarArchiveEntry(
          new TarArchiveEntry(layerContentFile.toFile(), layerName));
      manifestTemplate.addLayerFile(layerName);
    }

    // Adds the container configuration to the tarball.
    Blob containerConfigurationBlob =
        new ImageToJsonTranslator(image).getContainerConfigurationBlob();
    tarStreamBuilder.addByteEntry(
        Blobs.writeToByteArray(containerConfigurationBlob), CONTAINER_CONFIGURATION_JSON_FILE_NAME);

    // Adds the manifest to tarball.
    manifestTemplate.setRepoTags(imageReference.toStringWithTag());
    tarStreamBuilder.addByteEntry(
        Blobs.writeToByteArray(JsonTemplateMapper.toBlob(manifestTemplate)),
        MANIFEST_JSON_FILE_NAME);

    return tarStreamBuilder.toBlob();
  }
}
