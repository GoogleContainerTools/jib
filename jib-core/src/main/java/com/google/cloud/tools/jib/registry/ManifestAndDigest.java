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

package com.google.cloud.tools.jib.registry;

import com.google.cloud.tools.jib.api.DescriptorDigest;
import com.google.cloud.tools.jib.image.json.ManifestTemplate;

/** Stores a manifest and digest. */
public class ManifestAndDigest<T extends ManifestTemplate> {

  private final T manifest;
  private final DescriptorDigest digest;

  public ManifestAndDigest(T manifest, DescriptorDigest digest) {
    this.manifest = manifest;
    this.digest = digest;
  }

  /**
   * Gets the manifest.
   *
   * @return the manifest
   */
  public T getManifest() {
    return manifest;
  }

  /**
   * Gets the digest.
   *
   * @return the digest
   */
  public DescriptorDigest getDigest() {
    return digest;
  }
}
