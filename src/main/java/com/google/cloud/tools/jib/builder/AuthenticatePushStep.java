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

package com.google.cloud.tools.jib.builder;

import com.google.cloud.tools.jib.http.Authorization;
import com.google.cloud.tools.jib.registry.DockerCredentialRetriever;
import com.google.cloud.tools.jib.registry.NonexistentDockerCredentialHelperException;
import com.google.cloud.tools.jib.registry.NonexistentServerUrlDockerCredentialHelperException;
import java.io.IOException;
import java.util.concurrent.Callable;
import javax.annotation.Nullable;

/** Retrieves credentials to push to a target registry. */
class AuthenticatePushStep implements Callable<Authorization> {

  private final BuildConfiguration buildConfiguration;

  AuthenticatePushStep(BuildConfiguration buildConfiguration) {
    this.buildConfiguration = buildConfiguration;
  }

  @Override
  @Nullable
  public Authorization call()
      throws NonexistentServerUrlDockerCredentialHelperException,
          NonexistentDockerCredentialHelperException, IOException {
    if (buildConfiguration.getCredentialHelperName() == null) {
      return null;
    }

    DockerCredentialRetriever dockerCredentialRetriever =
        new DockerCredentialRetriever(
            buildConfiguration.getTargetServerUrl(), buildConfiguration.getCredentialHelperName());

    return dockerCredentialRetriever.retrieve();
  }
}
