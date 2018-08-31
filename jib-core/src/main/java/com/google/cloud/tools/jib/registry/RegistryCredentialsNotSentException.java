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

package com.google.cloud.tools.jib.registry;

/** Thrown when registry request was unauthorized because credentials weren't sent. */
public class RegistryCredentialsNotSentException extends RegistryException {

  /**
   * Identifies the image registry and repository that denied access.
   *
   * @param registry the image registry
   * @param repository the image repository
   */
  RegistryCredentialsNotSentException(String registry, String repository) {
    super(
        "Required credentials for "
            + registry
            + "/"
            + repository
            + " were not sent because the connection was over HTTP");
  }
}
