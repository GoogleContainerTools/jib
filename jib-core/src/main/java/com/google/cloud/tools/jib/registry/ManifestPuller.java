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

package com.google.cloud.tools.jib.registry;

import com.google.cloud.tools.jib.image.json.ManifestTemplate;

/** Pulls an image's manifest. */
class ManifestPuller<T extends ManifestTemplate>
    extends AbstractManifestPuller<T, ManifestAndDigest<T>> {

  ManifestPuller(
      RegistryEndpointRequestProperties registryEndpointRequestProperties,
      String imageTag,
      Class<T> manifestTemplateClass) {
    super(registryEndpointRequestProperties, imageTag, manifestTemplateClass);
  }

  @Override
  ManifestAndDigest<T> computeReturn(ManifestAndDigest<T> manifestAndDigest) {
    return manifestAndDigest;
  }
}
