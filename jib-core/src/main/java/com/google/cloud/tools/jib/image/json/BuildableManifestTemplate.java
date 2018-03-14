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
import java.util.List;
import javax.annotation.Nullable;

/**
 * Parent class for image manifest JSON templates that can be built.
 *
 * @see V22ManifestTemplate for Docker V2.2 format
 * @see OCIManifestTemplate for OCI format
 */
public interface BuildableManifestTemplate extends ManifestTemplate {

  /**
   * Template for inner JSON object representing content descriptor for a layer or container
   * configuration.
   *
   * @see <a href="https://github.com/opencontainers/image-spec/blob/master/descriptor.md">OCI
   *     Content Descriptors</a>
   */
  @VisibleForTesting
  class ContentDescriptorTemplate implements JsonTemplate {

    @Nullable private String mediaType;
    @Nullable private DescriptorDigest digest;
    private long size;

    ContentDescriptorTemplate(String mediaType, long size, DescriptorDigest digest) {
      this.mediaType = mediaType;
      this.size = size;
      this.digest = digest;
    }

    /** Necessary for Jackson to create from JSON. */
    private ContentDescriptorTemplate() {}

    @VisibleForTesting
    public long getSize() {
      return size;
    }

    void setSize(long size) {
      this.size = size;
    }

    @VisibleForTesting
    @Nullable
    public DescriptorDigest getDigest() {
      return digest;
    }

    void setDigest(DescriptorDigest digest) {
      this.digest = digest;
    }
  }

  /** @return the media type for this manifest, specific to the image format */
  String getManifestMediaType();

  /** @return the content descriptor of the container configuration */
  @Nullable
  ContentDescriptorTemplate getContainerConfiguration();

  /** @return an unmodifiable view of the layers */
  List<ContentDescriptorTemplate> getLayers();

  /** Sets the content descriptor of the container configuration. */
  void setContainerConfiguration(long size, DescriptorDigest digest);

  /** Adds a layer to the manifest. */
  void addLayer(long size, DescriptorDigest digest);
}
