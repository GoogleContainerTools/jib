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
import com.google.cloud.tools.jib.api.InsecureRegistryException;
import com.google.cloud.tools.jib.api.RegistryException;
import com.google.cloud.tools.jib.async.AsyncDependencies;
import com.google.cloud.tools.jib.async.AsyncStep;
import com.google.cloud.tools.jib.async.NonBlockingSteps;
import com.google.cloud.tools.jib.builder.ProgressEventDispatcher;
import com.google.cloud.tools.jib.builder.TimerEventDispatcher;
import com.google.cloud.tools.jib.configuration.BuildConfiguration;
import com.google.cloud.tools.jib.http.Authorization;
import com.google.cloud.tools.jib.registry.RegistryAuthenticator;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nullable;

/**
 * Authenticates push to a target registry using Docker Token Authentication.
 *
 * @see <a
 *     href="https://docs.docker.com/registry/spec/auth/token/">https://docs.docker.com/registry/spec/auth/token/</a>
 */
class AuthenticatePushStep implements AsyncStep<Authorization>, Callable<Authorization> {

  private static final String DESCRIPTION = "Authenticating with push to %s";

  private final BuildConfiguration buildConfiguration;
  private final ProgressEventDispatcher.Factory progressEventDispatcherFactory;

  private final RetrieveRegistryCredentialsStep retrieveTargetRegistryCredentialsStep;

  private final ListenableFuture<Authorization> listenableFuture;

  AuthenticatePushStep(
      ListeningExecutorService listeningExecutorService,
      BuildConfiguration buildConfiguration,
      ProgressEventDispatcher.Factory progressEventDispatcherFactory,
      RetrieveRegistryCredentialsStep retrieveTargetRegistryCredentialsStep) {
    this.buildConfiguration = buildConfiguration;
    this.progressEventDispatcherFactory = progressEventDispatcherFactory;
    this.retrieveTargetRegistryCredentialsStep = retrieveTargetRegistryCredentialsStep;

    listenableFuture =
        AsyncDependencies.using(listeningExecutorService)
            .addStep(retrieveTargetRegistryCredentialsStep)
            .whenAllSucceed(this);
  }

  @Override
  public ListenableFuture<Authorization> getFuture() {
    return listenableFuture;
  }

  @Override
  @Nullable
  public Authorization call() throws ExecutionException, IOException, RegistryException {
    Credential registryCredential = NonBlockingSteps.get(retrieveTargetRegistryCredentialsStep);

    String registry = buildConfiguration.getTargetImageConfiguration().getImageRegistry();
    try (ProgressEventDispatcher ignored =
            progressEventDispatcherFactory.create("authenticating push to " + registry, 1);
        TimerEventDispatcher ignored2 =
            new TimerEventDispatcher(
                buildConfiguration.getEventHandlers(), String.format(DESCRIPTION, registry))) {
      RegistryAuthenticator registryAuthenticator =
          buildConfiguration
              .newTargetImageRegistryClientFactory()
              .newRegistryClient()
              .getRegistryAuthenticator();
      if (registryAuthenticator != null) {
        return registryAuthenticator.authenticatePush(registryCredential);
      }
    } catch (InsecureRegistryException ex) {
      // Cannot skip certificate validation or use HTTP; fall through.
    }

    return (registryCredential == null || registryCredential.isOAuth2RefreshToken())
        ? null
        : Authorization.fromBasicCredentials(
            registryCredential.getUsername(), registryCredential.getPassword());
  }
}
