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

import com.google.cloud.tools.jib.Timer;
import com.google.cloud.tools.jib.http.Authorization;
import com.google.cloud.tools.jib.registry.credentials.RegistryCredentials;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nullable;

/** Retrieves credentials to push to a target registry. */
class AuthenticatePushStep implements Callable<Authorization> {

  private static final String DESCRIPTION = "Authenticating with push to %s";

  private final BuildConfiguration buildConfiguration;
  private final ListenableFuture<RegistryCredentials> registryCredentialsFuture;

  AuthenticatePushStep(
      BuildConfiguration buildConfiguration,
      ListenableFuture<RegistryCredentials> registryCredentialsFuture) {
    this.buildConfiguration = buildConfiguration;
    this.registryCredentialsFuture = registryCredentialsFuture;
  }

  /** Depends on nothing. */
  @Override
  @Nullable
  public Authorization call() throws ExecutionException, InterruptedException {
    try (Timer ignored =
        new Timer(
            buildConfiguration.getBuildLogger(),
            String.format(DESCRIPTION, buildConfiguration.getTargetServerUrl()))) {
      RegistryCredentials registryCredentials = NonBlockingFutures.get(registryCredentialsFuture);
      String registry = buildConfiguration.getTargetServerUrl();

      String credentialHelperSuffix = registryCredentials.getCredentialHelperUsed(registry);
      Authorization authorization = registryCredentials.getAuthorization(registry);
      if (credentialHelperSuffix == null || authorization == null) {
        // If no credentials found, give a warning and return null.
        buildConfiguration
            .getBuildLogger()
            .warn("No credentials could be retrieved for registry " + registry);
        return null;
      }
      buildConfiguration
          .getBuildLogger()
          .info(
              "Using docker-credential-" + credentialHelperSuffix + " for pushing to " + registry);
      return authorization;
    }
  }
}
