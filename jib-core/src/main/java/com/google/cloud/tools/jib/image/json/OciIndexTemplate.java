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

import com.google.cloud.tools.jib.blob.BlobDescriptor;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.List;

/**
 * JSON template for OCI archive "index.json" file.
 *
 * <p>Example manifest JSON:
 *
 * <pre>{@code
 * {
 *   "schemaVersion": 2,
 *   "mediaType": "application/vnd.oci.image.index.v1+json",
 *   "manifests": [
 *     {
 *       "mediaType": "application/vnd.oci.image.manifest.v1+json",
 *       "digest": "sha256:e684b1dceef404268f17d4adf7f755fd9912b8ae64864b3954a83ebb8aa628b3",
 *       "size": 1132,
 *       "annotations": {
 *         "org.opencontainers.image.ref.name": "gcr.io/project/image:tag"
 *       }
 *     }
 *   ]
 * }
 * }</pre>
 *
 * @see <a href="https://github.com/opencontainers/image-spec/blob/master/image-index.md">OCI Image
 *     Index Specification</a>
 */
public class OciIndexTemplate implements ManifestTemplate {

  /** The OCI Index media type. */
  public static final String MEDIA_TYPE = "application/vnd.oci.image.index.v1+json";

  private final int schemaVersion = 2;
  private final String mediaType = MEDIA_TYPE;

  private final List<BuildableManifestTemplate.ContentDescriptorTemplate> manifests =
      new ArrayList<>();

  @Override
  public int getSchemaVersion() {
    return schemaVersion;
  }

  @Override
  public String getManifestMediaType() {
    return mediaType;
  }

  /**
   * Adds a manifest reference with the given {@link BlobDescriptor}.
   *
   * @param descriptor the manifest blob descriptor
   * @param imageReferenceName the image reference name
   */
  public void addManifest(BlobDescriptor descriptor, String imageReferenceName) {
    BuildableManifestTemplate.ContentDescriptorTemplate contentDescriptorTemplate =
        new BuildableManifestTemplate.ContentDescriptorTemplate(
            OciManifestTemplate.MANIFEST_MEDIA_TYPE, descriptor.getSize(), descriptor.getDigest());
    contentDescriptorTemplate.setAnnotations(
        ImmutableMap.of("org.opencontainers.image.ref.name", imageReferenceName));
    manifests.add(contentDescriptorTemplate);
  }

  @VisibleForTesting
  public List<BuildableManifestTemplate.ContentDescriptorTemplate> getManifests() {
    return manifests;
  }
}
