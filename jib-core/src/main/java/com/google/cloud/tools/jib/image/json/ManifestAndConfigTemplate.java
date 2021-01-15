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

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.google.cloud.tools.jib.json.JsonTemplate;
import javax.annotation.Nullable;

/** Stores a manifest and container config. */
public class ManifestAndConfigTemplate implements JsonTemplate {

  @Nullable private String manifestDigest;

  @JsonTypeInfo(
      use = JsonTypeInfo.Id.CLASS,
      include = JsonTypeInfo.As.PROPERTY,
      property = "@class")
  @JsonSubTypes({
    @JsonSubTypes.Type(value = OciManifestTemplate.class),
    @JsonSubTypes.Type(value = V21ManifestTemplate.class),
    @JsonSubTypes.Type(value = V22ManifestTemplate.class),
  })
  @Nullable
  private ManifestTemplate manifest;

  @Nullable private ContainerConfigurationTemplate config;

  @SuppressWarnings("unused")
  private ManifestAndConfigTemplate() {}

  /**
   * Creates an instance.
   *
   * @param manifest the image manifest
   * @param config the container configuration
   */
  public ManifestAndConfigTemplate(
      // TODO: switch to BuildableManifestTemplate after we stop supporting V21 manifest.
      ManifestTemplate manifest,
      // TODO: remove @Nullable after we stop supporting V21 manifest.
      @Nullable ContainerConfigurationTemplate config) {
    this(manifest, config, null);
  }

  /**
   * Creates an instance.
   *
   * @param manifest the image manifest
   * @param config the container configuration
   * @param manifestDigest the digest of the manifest
   */
  public ManifestAndConfigTemplate(
      // TODO: switch to BuildableManifestTemplate after we stop supporting V21 manifest.
      ManifestTemplate manifest,
      // TODO: remove @Nullable after we stop supporting V21 manifest.
      @Nullable ContainerConfigurationTemplate config,
      @Nullable String manifestDigest) {
    this.manifest = manifest;
    this.config = config;
    this.manifestDigest = manifestDigest;
  }

  /**
   * Gets the digest of the manifest.
   *
   * @return the digest
   */
  @Nullable
  public String getManifestDigest() {
    return manifestDigest;
  }

  /**
   * Gets the manifest.
   *
   * @return the manifest
   */
  @Nullable
  public ManifestTemplate getManifest() {
    return manifest;
  }

  /**
   * Gets the container configuration.
   *
   * @return the container configuration
   */
  @Nullable
  public ContainerConfigurationTemplate getConfig() {
    return config;
  }
}
