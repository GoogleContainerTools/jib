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
import com.google.cloud.tools.jib.json.JsonTemplate;
import com.google.cloud.tools.jib.json.JsonTemplateMapper;
import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * JSON template for Docker Manifest Schema V2.1
 *
 * <p>This is only for parsing manifests in the older V2.1 schema. Generated manifests should be in
 * the V2.2 schema using the {@link V22ManifestTemplate}.
 *
 * <p>Example manifest JSON (only the {@code fsLayers} and {@code history} fields are relevant for
 * parsing):
 *
 * <pre>{@code
 * {
 *   ...
 *   "fsLayers": {
 *     {
 *       "blobSum": "sha256:5f70bf18a086007016e948b04aed3b82103a36bea41755b6cddfaf10ace3c6ef"
 *     },
 *     {
 *       "blobSum": "sha256:5f70bf18a086007016e948b04aed3b82103a36bea41755b6cddfaf10ace3c6ef"
 *     }
 *   },
 *   "history": [
 *     {
 *       "v1Compatibility": "<some manifest V1 JSON object>"
 *     }
 *   ]
 *   ...
 * }
 * }</pre>
 *
 * @see <a href="https://docs.docker.com/registry/spec/manifest-v2-1/">Image Manifest Version 2,
 *     Schema 1</a>
 */
public class V21ManifestTemplate implements ManifestTemplate {

  public static final String MEDIA_TYPE = "application/vnd.docker.distribution.manifest.v1+json";

  private final int schemaVersion = 1;
  private final String mediaType = MEDIA_TYPE;

  /** The list of layer references. */
  private final List<LayerObjectTemplate> fsLayers = new ArrayList<>();

  private final List<HistoryObjectTemplate> history = new ArrayList<>();

  /**
   * Template for inner JSON object representing a layer as part of the list of layer references.
   */
  @VisibleForTesting
  static class LayerObjectTemplate implements JsonTemplate {

    @Nullable private DescriptorDigest blobSum;

    @Nullable
    DescriptorDigest getDigest() {
      return blobSum;
    }
  }

  /** Template for inner JSON object representing history for a layer. */
  private static class HistoryObjectTemplate implements JsonTemplate {

    // The value is basically free-form; they may be structured differently in practice, e.g.,
    // {"architecture": "amd64", "config": {"User": "1001", ...}, "parent": ...}
    // {"id": ..., "container_config": {"Cmd":[""]}}
    @Nullable private String v1Compatibility;
  }

  /**
   * Returns a list of descriptor digests for the layers in the image.
   *
   * @return a list of descriptor digests for the layers in the image.
   */
  public List<DescriptorDigest> getLayerDigests() {
    List<DescriptorDigest> layerDigests = new ArrayList<>();

    for (LayerObjectTemplate layerObjectTemplate : fsLayers) {
      layerDigests.add(layerObjectTemplate.blobSum);
    }

    return layerDigests;
  }

  @Override
  public int getSchemaVersion() {
    return schemaVersion;
  }

  @Override
  public String getManifestMediaType() {
    return mediaType;
  }

  public List<LayerObjectTemplate> getFsLayers() {
    return Collections.unmodifiableList(fsLayers);
  }

  /**
   * Attempts to parse the container configuration JSON (of format {@code
   * application/vnd.docker.container.image.v1+json}) from the {@code v1Compatibility} value of the
   * first {@code history} entry, which corresponds to the latest layer.
   *
   * @return container configuration if the first history string holds it; {@code null} otherwise
   */
  public Optional<ContainerConfigurationTemplate> getContainerConfiguration() {
    try {
      if (history.isEmpty()) {
        return Optional.empty();
      }
      String v1Compatibility = history.get(0).v1Compatibility;
      if (v1Compatibility == null) {
        return Optional.empty();
      }

      return Optional.of(
          JsonTemplateMapper.readJson(v1Compatibility, ContainerConfigurationTemplate.class));
    } catch (IOException ex) {
      // not a container configuration; ignore and continue
      return Optional.empty();
    }
  }
}
