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

import com.google.cloud.tools.jib.image.DescriptorDigest;
import com.google.cloud.tools.jib.json.JsonTemplate;
import com.google.common.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * JSON Template for OCI Manifest Schema
 *
 * <p>Example manifest JSON:
 *
 * <pre>{@code
 * {
 *   "schemaVersion": 2,
 *   "mediaType": "application/vnd.oci.image.manifest.v1+json",
 *   "config": {
 *     "mediaType": "application/vnd.oci.image.config.v1+json",
 *     "size": 631,
 *     "digest": "sha256:26b84ca5b9050d32e68f66ad0f3e2bbcd247198a6e6e09a7effddf126eb8d873"
 *   },
 *   "layers": [
 *     {
 *       "mediaType": "application/vnd.oci.image.layer.v1.tar+gzip",
 *       "size": 1991435,
 *       "digest": "sha256:b56ae66c29370df48e7377c8f9baa744a3958058a766793f821dadcb144a4647"
 *     },
 *     {
 *       "mediaType": "application/vnd.oci.image.layer.v1.tar+gzip",
 *       "size": 32,
 *       "digest": "sha256:a3ed95caeb02ffe68cdd9fd84406680ae93d633cb16422d00e8a7c22955b46d4"
 *     }
 *   ]
 * }
 * }</pre>
 *
 * @see <a href="https://github.com/opencontainers/image-spec/blob/master/manifest.md">OCI Image Manifest Specification</a>
 */
public class OCIManifestTemplate extends ManifestTemplate {

  public static final String MEDIA_TYPE = "application/vnd.docker.distribution.manifest.v2+json";

  private final int schemaVersion = 2;
  private final String mediaType = MEDIA_TYPE;

  /** The container configuration reference. */
  private final ContainerConfigurationObjectTemplate config =
      new ContainerConfigurationObjectTemplate();

  /** The list of layer references. */
  private final List<ContentDescriptorTemplate> layers = new ArrayList<>();

  /** Template for inner JSON object representing the container configuration reference. */
  private static class ContainerConfigurationObjectTemplate extends JsonTemplate {

    private final String mediaType = "application/vnd.docker.container.image.v1+json";

    private DescriptorDigest digest;
    private long size;
  }

  /**
   * Template for inner JSON object representing a layer as part of the list of layer references.
   *
   * @see <a href="https://github.com/opencontainers/image-spec/blob/master/descriptor.md">OCI Content Descriptors</a>
   */
  static class ContentDescriptorTemplate extends JsonTemplate {

    private final String mediaType = "application/vnd.docker.image.rootfs.diff.tar.gzip";

    private DescriptorDigest digest;
    private long size;

    long getSize() {
      return size;
    }

    DescriptorDigest getDigest() {
      return digest;
    }
  }

  @Override
  public int getSchemaVersion() {
    return schemaVersion;
  }

  public List<ContentDescriptorTemplate> getLayers() {
    return Collections.unmodifiableList(layers);
  }

  @Override
  public void setContainerConfiguration(long size, DescriptorDigest digest) {
    config.size = size;
    config.digest = digest;
  }

  @Override
  public void addLayer(long size, DescriptorDigest digest) {
    ContentDescriptorTemplate contentDescriptorTemplate = new ContentDescriptorTemplate();
    contentDescriptorTemplate.size = size;
    contentDescriptorTemplate.digest = digest;
    layers.add(contentDescriptorTemplate);
  }

  @VisibleForTesting
  public DescriptorDigest getContainerConfigurationDigest() {
    return config.digest;
  }

  @VisibleForTesting
  long getContainerConfigurationSize() {
    return config.size;
  }

  @VisibleForTesting
  public DescriptorDigest getLayerDigest(int index) {
    return layers.get(index).digest;
  }

  @VisibleForTesting
  long getLayerSize(int index) {
    return layers.get(index).size;
  }
}
