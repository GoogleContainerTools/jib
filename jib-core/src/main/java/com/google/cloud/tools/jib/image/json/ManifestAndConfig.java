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

import java.util.Optional;
import javax.annotation.Nullable;

/** Stores a manifest and container config. */
public class ManifestAndConfig {

  private final ManifestTemplate manifest;
  @Nullable private final ContainerConfigurationTemplate config;

  public ManifestAndConfig(
      ManifestTemplate manifest, @Nullable ContainerConfigurationTemplate config) {
    this.manifest = manifest;
    this.config = config;
  }

  /**
   * Gets the manifest.
   *
   * @return the manifest
   */
  public ManifestTemplate getManifest() {
    return manifest;
  }

  /**
   * Gets the container configuration.
   *
   * @return the container configuration
   */
  public Optional<ContainerConfigurationTemplate> getConfig() {
    return Optional.ofNullable(config);
  }
}
