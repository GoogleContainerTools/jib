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

package com.google.cloud.tools.jib.registry;

/** Properties of registry endpoints. */
class RegistryEndpointProperties {

  private final String serverUrl;
  private final String imageName;

  /**
   * @param serverUrl the server URL for the registry (for example, {@code gcr.io})
   * @param imageName the image/repository name (also known as, namespace)
   */
  RegistryEndpointProperties(String serverUrl, String imageName) {
    this.serverUrl = serverUrl;
    this.imageName = imageName;
  }

  String getServerUrl() {
    return serverUrl;
  }

  String getImageName() {
    return imageName;
  }
}
