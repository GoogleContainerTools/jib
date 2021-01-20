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

package com.google.cloud.tools.jib.builder.steps;

import com.google.cloud.tools.jib.api.Credential;
import com.google.cloud.tools.jib.api.RegistryException;
import com.google.cloud.tools.jib.builder.ProgressEventDispatcher;
import com.google.cloud.tools.jib.builder.TimerEventDispatcher;
import com.google.cloud.tools.jib.configuration.BuildContext;
import com.google.cloud.tools.jib.registry.RegistryClient;
import com.google.cloud.tools.jib.registry.credentials.CredentialRetrievalException;
import java.io.IOException;
import java.util.concurrent.Callable;

/**
 * Authenticates push to a target registry using Docker Token Authentication.
 *
 * @see <a
 *     href="https://docs.docker.com/registry/spec/auth/token/">https://docs.docker.com/registry/spec/auth/token/</a>
 */
class AuthenticatePushStep implements Callable<RegistryClient> {

  @SuppressWarnings("InlineFormatString")
  private static final String DESCRIPTION = "Authenticating push to %s";

  private final BuildContext buildContext;
  private final ProgressEventDispatcher.Factory progressEventDispatcherFactory;

  AuthenticatePushStep(
      BuildContext buildContext, ProgressEventDispatcher.Factory progressEventDispatcherFactory) {
    this.buildContext = buildContext;
    this.progressEventDispatcherFactory = progressEventDispatcherFactory;
  }

  @Override
  public RegistryClient call() throws CredentialRetrievalException, IOException, RegistryException {
    String registry = buildContext.getTargetImageConfiguration().getImageRegistry();
    try (ProgressEventDispatcher progressDispatcher =
            progressEventDispatcherFactory.create("authenticating push to " + registry, 2);
        TimerEventDispatcher ignored2 =
            new TimerEventDispatcher(
                buildContext.getEventHandlers(), String.format(DESCRIPTION, registry))) {
      Credential credential =
          RegistryCredentialRetriever.getTargetImageCredential(buildContext).orElse(null);
      progressDispatcher.dispatchProgress(1);

      RegistryClient registryClient =
          buildContext
              .newTargetImageRegistryClientFactory()
              .setCredential(credential)
              .newRegistryClient();
      if (!registryClient.doPushBearerAuth()) {
        // server returned "WWW-Authenticate: Basic ..." (e.g., local Docker registry)
        if (credential != null && !credential.isOAuth2RefreshToken()) {
          registryClient.configureBasicAuth();
        }
      }
      return registryClient;
    }
  }
}
