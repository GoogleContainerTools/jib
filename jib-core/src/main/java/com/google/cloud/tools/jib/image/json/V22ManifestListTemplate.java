/*
 * Copyright 2019 Google LLC.
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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.cloud.tools.jib.json.JsonTemplate;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/**
 * JSON Template for Docker Manifest List Schema V2.2
 *
 * <p>Example manifest JSON:
 *
 * <pre>{@code
 * {
 *   "schemaVersion": 2,
 *   "mediaType": "application/vnd.docker.distribution.manifest.list.v2+json",
 *   "manifests": [
 *     {
 *       "mediaType": "application/vnd.docker.image.manifest.v2+json",
 *       "size": 7143,
 *       "digest": "sha256:e692418e4cbaf90ca69d05a66403747baa33ee08806650b51fab815ad7fc331f",
 *       "platform": {
 *         "architecture": "ppc64le",
 *         "os": "linux",
 *       }
 *     },
 *     {
 *       "mediaType": "application/vnd.docker.image.manifest.v2+json",
 *       "size": 7682,
 *       "digest": "sha256:5b0bcabd1ed22e9fb1310cf6c2dec7cdef19f0ad69efa1f392e94a4333501270",
 *       "platform": {
 *         "architecture": "amd64",
 *         "os": "linux",
 *         "features": [
 *           "sse4"
 *         ]
 *       }
 *     }
 *   ]
 * }
 * }</pre>
 *
 * @see <a href="https://docs.docker.com/registry/spec/manifest-v2-2/#manifest-list">Image Manifest
 *     Version 2, Schema 2: Manifest List</a>
 */
public class V22ManifestListTemplate implements ManifestTemplate {

  public static final String MANIFEST_MEDIA_TYPE =
      "application/vnd.docker.distribution.manifest.list.v2+json";
  private static final int SCHEMA_VERSION = 2;

  @Override
  public int getSchemaVersion() {
    return SCHEMA_VERSION;
  }

  private List<ManifestDescriptorTemplate> manifests;

  @VisibleForTesting
  List<ManifestDescriptorTemplate> getManifests() {
    Preconditions.checkNotNull(manifests);
    return manifests;
  }

  public List<String> getDigestForPlatform(String architecture, String os) {
    return manifests
        .stream()
        .filter(
            manifest ->
                manifest.platform != null
                    && os.equals(manifest.platform.os)
                    && architecture.equals(manifest.platform.architecture))
        .map(ManifestDescriptorTemplate::getDigest)
        .collect(Collectors.toList());
  }

  /**
   * Template for inner JSON object representing content descriptor for a layer or container
   * configuration.
   *
   * @see <a href="https://github.com/opencontainers/image-spec/blob/master/descriptor.md">OCI
   *     Content Descriptors</a>
   */
  static class ManifestDescriptorTemplate implements JsonTemplate {

    static class Platform implements JsonTemplate {
      @Nullable private String architecture;
      @Nullable private String os;

      // ignored properties
      @Nullable
      @JsonProperty("os.version")
      private String osVersion;

      @Nullable
      @JsonProperty("os.features")
      private List<String> osFeatures;

      @Nullable private String variant;
      @Nullable private List<String> features;
      // end ignored

      @Nullable
      String getArchitecture() {
        return architecture;
      }

      @Nullable
      public String getOs() {
        return os;
      }
    }

    @Nullable private String mediaType;
    @Nullable private String digest;
    private long size;
    @Nullable private Platform platform;

    @Nullable
    private String getDigest() {
      return digest;
    }

    @Nullable
    public String getMediaType() {
      return mediaType;
    }

    @VisibleForTesting
    @Nullable
    Platform getPlatform() {
      return platform;
    }
  }
}
