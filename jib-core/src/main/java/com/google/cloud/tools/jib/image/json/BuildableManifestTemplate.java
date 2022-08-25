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

package com.google.cloud.tools.jib.image.json;

import com.google.cloud.tools.jib.api.DescriptorDigest;
import com.google.cloud.tools.jib.json.JsonTemplate;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Parent class for image manifest JSON templates that can be built.
 *
 * @see V22ManifestTemplate Docker V2.2 format
 * @see OciManifestTemplate OCI format
 */
public interface BuildableManifestTemplate extends ManifestTemplate {

  /**
   * Template for inner JSON object representing content descriptor for a layer or container
   * configuration.
   *
   * @see <a href="https://github.com/opencontainers/image-spec/blob/master/descriptor.md">OCI
   *     Content Descriptors</a>
   */
  class ContentDescriptorTemplate implements JsonTemplate {

    @SuppressWarnings("unused")
    @Nullable
    private String mediaType;

    @Nullable private DescriptorDigest digest;
    private long size;
    @Nullable private List<String> urls;
    @Nullable private Map<String, String> annotations;

    ContentDescriptorTemplate(String mediaType, long size, DescriptorDigest digest) {
      this.mediaType = mediaType;
      this.size = size;
      this.digest = digest;
    }

    /** Necessary for Jackson to create from JSON. */
    @SuppressWarnings("unused")
    protected ContentDescriptorTemplate() {}

    public long getSize() {
      return size;
    }

    void setSize(long size) {
      this.size = size;
    }

    @Nullable
    public DescriptorDigest getDigest() {
      return digest;
    }

    void setDigest(DescriptorDigest digest) {
      this.digest = digest;
    }

    @VisibleForTesting
    @Nullable
    public List<String> getUrls() {
      return urls;
    }

    void setUrls(List<String> urls) {
      this.urls = ImmutableList.copyOf(urls);
    }

    @VisibleForTesting
    @Nullable
    public Map<String, String> getAnnotations() {
      return annotations;
    }

    void setAnnotations(Map<String, String> annotations) {
      this.annotations = ImmutableMap.copyOf(annotations);
    }
  }

  /**
   * Returns the media type for this manifest, specific to the image format.
   *
   * @return the media type for this manifest, specific to the image format
   */
  @Override
  String getManifestMediaType();

  /**
   * Returns the content descriptor of the container configuration.
   *
   * @return the content descriptor of the container configuration
   */
  @Nullable
  ContentDescriptorTemplate getContainerConfiguration();

  /**
   * Returns an unmodifiable view of the layers.
   *
   * @return an unmodifiable view of the layers
   */
  List<ContentDescriptorTemplate> getLayers();

  /**
   * Sets the content descriptor of the container configuration.
   *
   * @param size the size of the container configuration.
   * @param digest the container configuration content descriptor digest.
   */
  void setContainerConfiguration(long size, DescriptorDigest digest);

  /**
   * Adds a layer to the manifest.
   *
   * @param size the size of the layer.
   * @param digest the layer descriptor digest.
   */
  void addLayer(long size, DescriptorDigest digest);
}
