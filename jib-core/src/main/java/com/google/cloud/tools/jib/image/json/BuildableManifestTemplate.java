/*
 * Copyright 2018 Google Inc.
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
 * Parent class for image manifest JSON templates that can be built.
 *
 * <p>Example manifest JSON:
 *
 * <pre>{@code
 * {
 *   "schemaVersion": 2,
 *   "mediaType": "application/vnd.docker.distribution.manifest.v2+json",
 *   "config": {
 *     "mediaType": "application/vnd.docker.container.image.v1+json",
 *     "size": 631,
 *     "digest": "sha256:26b84ca5b9050d32e68f66ad0f3e2bbcd247198a6e6e09a7effddf126eb8d873"
 *   },
 *   "layers": [
 *     {
 *       "mediaType": "application/vnd.docker.image.rootfs.diff.tar.gzip",
 *       "size": 1991435,
 *       "digest": "sha256:b56ae66c29370df48e7377c8f9baa744a3958058a766793f821dadcb144a4647"
 *     },
 *     {
 *       "mediaType": "application/vnd.docker.image.rootfs.diff.tar.gzip",
 *       "size": 32,
 *       "digest": "sha256:a3ed95caeb02ffe68cdd9fd84406680ae93d633cb16422d00e8a7c22955b46d4"
 *     }
 *   ]
 * }
 * }</pre>
 *
 * @see V22ManifestTemplate for Docker V2.2 format
 * @see OCIManifestTemplate for OCI format
 */
public abstract class BuildableManifestTemplate extends ManifestTemplate {

  /**
   * Template for inner JSON object representing a layer as part of the list of layer references.
   *
   * @see <a href="https://github.com/opencontainers/image-spec/blob/master/descriptor.md">OCI
   *     Content Descriptors</a>
   */
  @VisibleForTesting
  static class ContentDescriptorTemplate extends JsonTemplate {

    private String mediaType;
    private DescriptorDigest digest;
    private long size;

    long getSize() {
      return size;
    }

    DescriptorDigest getDigest() {
      return digest;
    }
  }

  /** Template for inner JSON object representing the container configuration reference. */
  private static class ContainerConfigurationObjectTemplate extends JsonTemplate {

    private String mediaType;
    private DescriptorDigest digest;
    private long size;
  }

  private final int schemaVersion = 2;
  private final String mediaType = getManifestMediaType();

  /** The container configuration reference. */
  private final ContainerConfigurationObjectTemplate config =
      newContainerConfigurationObjectTemplate();

  /** The list of layer references. */
  private final List<ContentDescriptorTemplate> layers = new ArrayList<>();

  @Override
  public int getSchemaVersion() {
    return schemaVersion;
  }

  public List<ContentDescriptorTemplate> getLayers() {
    return Collections.unmodifiableList(layers);
  }

  public void setContainerConfiguration(long size, DescriptorDigest digest) {
    config.size = size;
    config.digest = digest;
  }

  public void addLayer(long size, DescriptorDigest digest) {
    ContentDescriptorTemplate contentDescriptorTemplate = new ContentDescriptorTemplate();
    contentDescriptorTemplate.mediaType = getContentDescriptorMediaType();
    contentDescriptorTemplate.size = size;
    contentDescriptorTemplate.digest = digest;
    layers.add(contentDescriptorTemplate);
  }

  /** @return the media type for this manifest, specific to the image format */
  public abstract String getManifestMediaType();

  /** @return the container configuration media type, specific to the image format */
  abstract String getContainerConfigurationMediaType();

  /** @return the content descriptor media type, specific to the image format */
  abstract String getContentDescriptorMediaType();

  /** Constructs a new {@link ContainerConfigurationObjectTemplate} with the intended media type. */
  private ContainerConfigurationObjectTemplate newContainerConfigurationObjectTemplate() {
    ContainerConfigurationObjectTemplate containerConfigurationObjectTemplate =
        new ContainerConfigurationObjectTemplate();
    containerConfigurationObjectTemplate.mediaType = getContainerConfigurationMediaType();
    return containerConfigurationObjectTemplate;
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
