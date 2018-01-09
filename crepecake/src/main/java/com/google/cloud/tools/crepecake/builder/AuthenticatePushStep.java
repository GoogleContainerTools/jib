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
import com.google.cloud.tools.crepecake.registry.DockerCredentialRetriever;
import com.google.cloud.tools.crepecake.registry.NonexistentDockerCredentialHelperException;
import com.google.cloud.tools.crepecake.registry.NonexistentServerUrlDockerCredentialHelperException;
import java.io.IOException;

/** Retrieves credentials to push to a target registry. */
class AuthenticatePushStep implements Step<Void, Authorization> {

  private final BuildConfiguration buildConfiguration;

  AuthenticatePushStep(BuildConfiguration buildConfiguration) {
    this.buildConfiguration = buildConfiguration;

    if (buildConfiguration.getCredentialHelperName() == null) {
      throw new IllegalArgumentException(
          "Cannot authenticate push without a credential helper name specified in the build configuration");
    }
  }

  @Override
  public Authorization run(Void input)
      throws NonexistentServerUrlDockerCredentialHelperException,
          NonexistentDockerCredentialHelperException, IOException {
    DockerCredentialRetriever dockerCredentialRetriever =
        new DockerCredentialRetriever(
            buildConfiguration.getTargetServerUrl(), buildConfiguration.getCredentialHelperName());

    return dockerCredentialRetriever.retrieve();
  }
}
