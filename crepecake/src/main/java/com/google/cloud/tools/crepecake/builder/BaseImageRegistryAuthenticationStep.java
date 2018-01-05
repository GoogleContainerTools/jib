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

package com.google.cloud.tools.crepecake.builder;

import com.google.cloud.tools.crepecake.http.Authorization;
import com.google.cloud.tools.crepecake.registry.RegistryAuthenticationFailedException;
import com.google.cloud.tools.crepecake.registry.RegistryAuthenticator;
import com.google.cloud.tools.crepecake.registry.RegistryAuthenticators;

/** Authenticates with the base image registry. */
class BaseImageRegistryAuthenticationStep implements Step<Void, Authorization> {

  private final BuildConfiguration buildConfiguration;

  BaseImageRegistryAuthenticationStep(BuildConfiguration buildConfiguration) {
    this.buildConfiguration = buildConfiguration;
  }

  @Override
  public Authorization run(Void input) throws RegistryAuthenticationFailedException {
    if (buildConfiguration.getBaseImageServerUrl().isEmpty()) {
      // No server URL means the base image is on Docker Hub.
      return RegistryAuthenticators.forDockerHub(buildConfiguration.getBaseImageName()).authenticate();
    }

    return RegistryAuthenticators
  }
}
