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

package com.google.cloud.tools.jib.plugins.common;

import java.util.Optional;

/** An auth provider specific to the client's architecture. */
public interface InferredAuthProvider {

  /**
   * Find auth credentials for a specific registry.
   *
   * @param registry we want credential for
   * @return auth information for the registry (can be empty)
   * @throws InferredAuthException if the auth discovery process resulted in a fatal error
   */
  Optional<AuthProperty> inferAuth(String registry) throws InferredAuthException;
}
