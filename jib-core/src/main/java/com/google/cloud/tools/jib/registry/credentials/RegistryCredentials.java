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

import com.google.cloud.tools.jib.http.Authorization;
import java.util.HashMap;
import java.util.Map;

/**
 * Stores retrieved registry credentials.
 *
 * <p>The credentials are referred to by the registry they are used for.
 */
public class RegistryCredentials {

  /** Maps from registry to the credentials for that registry. */
  private final Map<String, Authorization> credentials = new HashMap<>();

  public void store(String registry, Authorization authorization) {
    credentials.put(registry, authorization);
  }

  public Authorization get(String registry) throws NoRegistryCredentialsException {
    if (!credentials.containsKey(registry)) {
      throw new NoRegistryCredentialsException(registry);
    }
    return credentials.get(registry);
  }
}
