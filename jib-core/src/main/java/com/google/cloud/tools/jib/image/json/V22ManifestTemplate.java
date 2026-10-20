/*
 * Copyright 2017 Google LLC.
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
import com.google.cloud.tools.jib.api.buildplan.CompressionAlgorithm;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;

/**
 * JSON Template for Docker Manifest Schema V2.2
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
 * @see <a href="https://docs.docker.com/registry/spec/manifest-v2-2/">Image Manifest Version 2,
 *     Schema 2</a>
 */
public class V22ManifestTemplate implements BuildableManifestTemplate {

  /** The Docker V2.2 manifest media type. */
  public static final String MANIFEST_MEDIA_TYPE =
      "application/vnd.docker.distribution.manifest.v2+json";

  /** The Docker V2.2 container configuration media type. */
  private static final String CONTAINER_CONFIGURATION_MEDIA_TYPE =
      "application/vnd.docker.container.image.v1+json";

  /** The Docker V2.2 layer media type. */
  private static final String LAYER_MEDIA_TYPE =
      "application/vnd.docker.image.rootfs.diff.tar.gzip";

  private final int schemaVersion = 2;

  @SuppressWarnings("unused")
  private final String mediaType = MANIFEST_MEDIA_TYPE;

  /** The container configuration reference. */
  @Nullable private ContentDescriptorTemplate config;

  /** The list of layer references. */
  private final List<ContentDescriptorTemplate> layers = new ArrayList<>();

  @Override
  public int getSchemaVersion() {
    return schemaVersion;
  }

  @Override
  public String getManifestMediaType() {
    return MANIFEST_MEDIA_TYPE;
  }

  @Override
  @Nullable
  public ContentDescriptorTemplate getContainerConfiguration() {
    return config;
  }

  @Override
  public List<ContentDescriptorTemplate> getLayers() {
    return Collections.unmodifiableList(layers);
  }

  @Override
  public void setContainerConfiguration(long size, DescriptorDigest digest) {
    config = new ContentDescriptorTemplate(CONTAINER_CONFIGURATION_MEDIA_TYPE, size, digest);
  }

  @Override
  public void addLayer(
      long size, DescriptorDigest digest, CompressionAlgorithm compressionAlgorithm) {
    String layerMediaType;
    switch (compressionAlgorithm) {
      case GZIP:
        layerMediaType = LAYER_MEDIA_TYPE;
        break;
      case ZSTD:
        throw new IllegalArgumentException(
            "Compression algorithm "
                + compressionAlgorithm
                + " is not supported with Docker v2.2 manifests");
      default:
        throw new IllegalArgumentException(
            "Unsupported compression algorithm " + compressionAlgorithm);
    }
    layers.add(new ContentDescriptorTemplate(layerMediaType, size, digest));
  }
}
