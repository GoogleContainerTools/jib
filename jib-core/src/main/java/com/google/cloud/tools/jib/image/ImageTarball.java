/*
 * Copyright 2018 Google LLC.
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

package com.google.cloud.tools.jib.image;

import com.google.cloud.tools.jib.api.DescriptorDigest;
import com.google.cloud.tools.jib.api.ImageReference;
import com.google.cloud.tools.jib.blob.BlobDescriptor;
import com.google.cloud.tools.jib.docker.json.DockerManifestEntryTemplate;
import com.google.cloud.tools.jib.hash.Digests;
import com.google.cloud.tools.jib.image.json.ImageToJsonTranslator;
import com.google.cloud.tools.jib.image.json.OciIndexTemplate;
import com.google.cloud.tools.jib.image.json.OciManifestTemplate;
import com.google.cloud.tools.jib.json.JsonTemplate;
import com.google.cloud.tools.jib.json.JsonTemplateMapper;
import com.google.cloud.tools.jib.tar.TarStreamBuilder;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;

/** Translates an {@link Image} to a tarball that can be loaded into Docker. */
public class ImageTarball {

  /** File name for the container configuration in the tarball. */
  private static final String CONTAINER_CONFIGURATION_JSON_FILE_NAME = "config.json";

  /** File name for the manifest in the tarball. */
  private static final String MANIFEST_JSON_FILE_NAME = "manifest.json";

  /** File name extension for the layer content files. */
  private static final String LAYER_FILE_EXTENSION = ".tar.gz";

  /** Time that entry is set in the tar. */
  private static final Instant TAR_ENTRY_MODIFICATION_TIME = Instant.EPOCH;

  private static final String BLOB_PREFIX = "blobs/sha256/";

  private final Image image;
  private final ImageReference imageReference;
  private final ImmutableSet<String> allTargetImageTags;

  /**
   * Instantiate with an {@link Image}.
   *
   * @param image the image to convert into a tarball
   * @param imageReference image reference to set in the manifest (note that the tag portion of the
   *     image reference is ignored)
   * @param allTargetImageTags the tags to tag the image with
   */
  public ImageTarball(
      Image image, ImageReference imageReference, ImmutableSet<String> allTargetImageTags) {
    this.image = image;
    this.imageReference = imageReference;
    this.allTargetImageTags = allTargetImageTags;
  }

  /**
   * Writes image tar bar in configured {@link Image#getImageFormat()} of OCI or Docker to output
   * stream.
   *
   * @param out the target output stream
   * @throws IOException if an error occurs writing out the image to stream
   */
  public void writeTo(OutputStream out) throws IOException {
    if (image.getImageFormat() == OciManifestTemplate.class) {
      ociWriteTo(out);
    } else {
      dockerWriteTo(out);
    }
  }

  private void ociWriteTo(OutputStream out) throws IOException {
    TarStreamBuilder tarStreamBuilder = new TarStreamBuilder();
    OciManifestTemplate manifest = new OciManifestTemplate();

    // Adds all the layers to the tarball and manifest
    for (Layer layer : image.getLayers()) {
      DescriptorDigest digest = layer.getBlobDescriptor().getDigest();
      long size = layer.getBlobDescriptor().getSize();

      tarStreamBuilder.addBlobEntry(
          layer.getBlob(), size, BLOB_PREFIX + digest.getHash(), TAR_ENTRY_MODIFICATION_TIME);
      manifest.addLayer(size, digest, layer.getCompressionAlgorithm());
    }

    // Adds the container configuration to the tarball and manifest
    JsonTemplate containerConfiguration =
        new ImageToJsonTranslator(image).getContainerConfiguration();
    BlobDescriptor configDescriptor = Digests.computeDigest(containerConfiguration);
    manifest.setContainerConfiguration(configDescriptor.getSize(), configDescriptor.getDigest());
    tarStreamBuilder.addByteEntry(
        JsonTemplateMapper.toByteArray(containerConfiguration),
        BLOB_PREFIX + configDescriptor.getDigest().getHash(),
        TAR_ENTRY_MODIFICATION_TIME);

    // Adds the manifest to the tarball
    BlobDescriptor manifestDescriptor = Digests.computeDigest(manifest);
    tarStreamBuilder.addByteEntry(
        JsonTemplateMapper.toByteArray(manifest),
        BLOB_PREFIX + manifestDescriptor.getDigest().getHash(),
        TAR_ENTRY_MODIFICATION_TIME);

    // Adds the oci-layout and index.json
    tarStreamBuilder.addByteEntry(
        "{\"imageLayoutVersion\": \"1.0.0\"}".getBytes(StandardCharsets.UTF_8),
        "oci-layout",
        TAR_ENTRY_MODIFICATION_TIME);
    OciIndexTemplate index = new OciIndexTemplate();
    // TODO: figure out how to tag with allTargetImageTags
    index.addManifest(manifestDescriptor, imageReference.toStringWithQualifier());
    tarStreamBuilder.addByteEntry(
        JsonTemplateMapper.toByteArray(index), "index.json", TAR_ENTRY_MODIFICATION_TIME);

    tarStreamBuilder.writeAsTarArchiveTo(out);
  }

  private void dockerWriteTo(OutputStream out) throws IOException {
    TarStreamBuilder tarStreamBuilder = new TarStreamBuilder();
    DockerManifestEntryTemplate manifestTemplate = new DockerManifestEntryTemplate();

    // Adds all the layers to the tarball and manifest.
    for (Layer layer : image.getLayers()) {
      String layerName = layer.getBlobDescriptor().getDigest().getHash() + LAYER_FILE_EXTENSION;

      tarStreamBuilder.addBlobEntry(
          layer.getBlob(),
          layer.getBlobDescriptor().getSize(),
          layerName,
          TAR_ENTRY_MODIFICATION_TIME);
      manifestTemplate.addLayerFile(layerName);
    }

    // Adds the container configuration to the tarball.
    JsonTemplate containerConfiguration =
        new ImageToJsonTranslator(image).getContainerConfiguration();
    tarStreamBuilder.addByteEntry(
        JsonTemplateMapper.toByteArray(containerConfiguration),
        CONTAINER_CONFIGURATION_JSON_FILE_NAME,
        TAR_ENTRY_MODIFICATION_TIME);

    // Adds the manifest to tarball.
    for (String tag : allTargetImageTags) {
      manifestTemplate.addRepoTag(imageReference.withQualifier(tag).toStringWithQualifier());
    }
    tarStreamBuilder.addByteEntry(
        JsonTemplateMapper.toByteArray(Collections.singletonList(manifestTemplate)),
        MANIFEST_JSON_FILE_NAME,
        TAR_ENTRY_MODIFICATION_TIME);

    tarStreamBuilder.writeAsTarArchiveTo(out);
  }

  /**
   * Returns the total size of the image's layers in bytes.
   *
   * @return the total size of the image's layers in bytes
   */
  public long getTotalLayerSize() {
    long size = 0;
    for (Layer layer : image.getLayers()) {
      size += layer.getBlobDescriptor().getSize();
    }
    return size;
  }
}
