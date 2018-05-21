/*
 * Copyright 2018 Google LLC. All rights reserved.
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
import com.google.cloud.tools.jib.registry.RegistryAuthenticationFailedException;
import com.google.cloud.tools.jib.registry.RegistryAuthenticator;
import com.google.cloud.tools.jib.registry.RegistryAuthenticators;
import com.google.cloud.tools.jib.registry.RegistryException;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nullable;

/**
 * Authenticates push to a target registry using Docker Token Authentication.
 *
 * @see <a
 *     href="https://docs.docker.com/registry/spec/auth/token/">https://docs.docker.com/registry/spec/auth/token/</a>
 */
class AuthenticatePushStep implements AsyncStep<Authorization> {

  private static final String DESCRIPTION = "Authenticating with push to %s";

  private final BuildConfiguration buildConfiguration;
  private final RetrieveRegistryCredentialsStep retrieveRegistryCredentialsStep;

  private final ListeningExecutorService listeningExecutorService;
  @Nullable private ListenableFuture<Authorization> listenableFuture;

  AuthenticatePushStep(
      ListeningExecutorService listeningExecutorService,
      BuildConfiguration buildConfiguration,
      RetrieveRegistryCredentialsStep retrieveRegistryCredentialsStep) {
    this.listeningExecutorService = listeningExecutorService;
    this.buildConfiguration = buildConfiguration;
    this.retrieveRegistryCredentialsStep = retrieveRegistryCredentialsStep;
  }

  @Override
  public ListenableFuture<Authorization> getFuture() {
    if (listenableFuture == null) {
      listenableFuture =
          Futures.whenAllSucceed(retrieveRegistryCredentialsStep.getFuture())
              .call(this, listeningExecutorService);
    }

    return listenableFuture;
  }

  /** Depends on {@link RetrieveRegistryCredentialsStep}. */
  @Override
  @Nullable
  public Authorization call()
      throws ExecutionException, InterruptedException, RegistryAuthenticationFailedException,
          IOException, RegistryException {
    try (Timer ignored =
        new Timer(
            buildConfiguration.getBuildLogger(),
            String.format(DESCRIPTION, buildConfiguration.getTargetImageRegistry()))) {
      Authorization registryCredentials = NonBlockingSteps.get(retrieveRegistryCredentialsStep);
      RegistryAuthenticator registryAuthenticator =
          RegistryAuthenticators.forOther(
              buildConfiguration.getTargetImageRegistry(),
              buildConfiguration.getTargetImageRepository());
      if (registryAuthenticator == null) {
        return registryCredentials;
      }
      return registryAuthenticator.setAuthorization(registryCredentials).authenticatePush();
    }
  }
}
