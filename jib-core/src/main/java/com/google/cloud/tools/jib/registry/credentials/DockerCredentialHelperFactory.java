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

import java.nio.file.Path;
import java.nio.file.Paths;

/** Factory class for constructing {@link DockerCredentialHelper}. */
public class DockerCredentialHelperFactory {

  public static final String CREDENTIAL_HELPER_PREFIX = "docker-credential-";

  public DockerCredentialHelperFactory() {}

  /**
   * @param registry the {@code ServerURL} stored by the credential helper
   * @param credentialHelper the path to the Docker credential helper executable - usually in the
   *     form docker-credential-[suffix]
   * @return a {@link DockerCredentialHelper} retrieved from the command.
   */
  public DockerCredentialHelper newDockerCredentialHelper(String registry, Path credentialHelper) {
    return new DockerCredentialHelper(registry, credentialHelper);
  }

  public DockerCredentialHelper newDockerCredentialHelper(
      String registry, String credentialHelperSuffix) {
    return new DockerCredentialHelper(
        registry, Paths.get(CREDENTIAL_HELPER_PREFIX + credentialHelperSuffix));
  }
}
