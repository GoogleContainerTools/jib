/*
 * Copyright 2018 Google LLC. All rights reserved.
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

package com.google.cloud.tools.jib.registry.credentials;

/** Factory class for constructing {@link DockerCredentialHelper}. */
public class DockerCredentialHelperFactory {

  public DockerCredentialHelperFactory() {}

  /**
   * @param registry the {@code ServerURL} stored by the credential helper
   * @param credentialHelperSuffix the suffix of the docker-credential-[suffix] command to be run.
   * @return a {@link DockerCredentialHelper} retrieved from the command.
   */
  public DockerCredentialHelper newDockerCredentialHelper(
      String registry, String credentialHelperSuffix) {
    return new DockerCredentialHelper(registry, credentialHelperSuffix);
  }
}
