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
import com.google.cloud.tools.jib.registry.DockerCredentialRetriever;
import com.google.cloud.tools.jib.registry.NonexistentDockerCredentialHelperException;
import com.google.cloud.tools.jib.registry.NonexistentServerUrlDockerCredentialHelperException;
import java.io.IOException;
import java.util.concurrent.Callable;

/** Attempts to retrieve registry credentials. */
class RetrieveRegistryCredentialsStep implements Callable<Authorization> {

  private static final String DESCRIPTION = "Retrieving registry credentials for %s";

  private final BuildConfiguration buildConfiguration;
  private final String registry;

  RetrieveRegistryCredentialsStep(BuildConfiguration buildConfiguration, String registry) {
    this.buildConfiguration = buildConfiguration;
    this.registry = registry;
  }

  @Override
  public Authorization call() throws IOException, NonexistentDockerCredentialHelperException {
    try (Timer ignored =
        new Timer(
            buildConfiguration.getBuildLogger(),
            String.format(DESCRIPTION, buildConfiguration.getTargetRegistry()))) {
      // Tries to get registry credentials from Docker credential helpers.
      for (String credentialHelperSuffix : buildConfiguration.getCredentialHelperNames()) {
        // Attempts to retrieve authorization for the registry using
        // docker-credential-[credentialSource].
        try {
          Authorization authorization =
              new DockerCredentialRetriever(registry, credentialHelperSuffix).retrieve();
          logGotCredentialsFrom("docker-credential-" + credentialHelperSuffix);
          return authorization;

        } catch (NonexistentServerUrlDockerCredentialHelperException ex) {
          // No authorization is found, so continues on to the next credential helper.
        }
      }

      // Tries to get registry credentials from known registry credentials.
      if (buildConfiguration.getKnownRegistryCredentials().has(registry)) {
        logGotCredentialsFrom(
            buildConfiguration.getKnownRegistryCredentials().getCredentialSource(registry));
        return buildConfiguration.getKnownRegistryCredentials().getAuthorization(registry);
      }

      /*
       * If no credentials found, give an info (not warning because in most cases, the base image is
       * public and does not need extra credentials) and return null.
       */
      buildConfiguration
          .getBuildLogger()
          .info("No credentials could be retrieved for registry " + registry);
      return null;
    }
  }

  private void logGotCredentialsFrom(String credentialSource) {
    buildConfiguration
        .getBuildLogger()
        .info("Using " + credentialSource + " for pulling from " + registry);
  }
}
