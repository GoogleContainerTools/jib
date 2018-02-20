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

package com.google.cloud.tools.jib.registry.credentials;

/** Factory class for constructing {@link DockerCredentialHelper}. */
public class DockerCredentialHelperFactory {

  private final String registry;

  public DockerCredentialHelperFactory(String registry) {
    this.registry = registry;
  }

  /**
   * @return a {@link DockerCredentialHelper} that uses the {@code
   *     docker-credential-[credentialHelperSuffix]} command
   */
  public DockerCredentialHelper withCredentialHelperSuffix(String credentialHelperSuffix) {
    return new DockerCredentialHelper(registry, credentialHelperSuffix);
  }
}
