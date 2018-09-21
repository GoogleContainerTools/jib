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

package com.google.cloud.tools.jib.registry.credentials;

import com.google.cloud.tools.jib.http.Authorization;

/**
 * Stores retrieved registry credentials and their source.
 *
 * <p>The credentials are referred to by the registry they are used for.
 */
public class RegistryCredentials {

  private final Authorization authorization;

  /**
   * A string representation of where the credentials were retrieved from. This is useful for
   * letting the user know which credentials were used.
   */
  private final String credentialSource;

  public RegistryCredentials(String credentialSource, Authorization authorization) {
    this.authorization = authorization;
    this.credentialSource = credentialSource;
  }

  public Authorization getAuthorization() {
    return authorization;
  }

  public String getCredentialSource() {
    return credentialSource;
  }
}
