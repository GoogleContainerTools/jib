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

import com.google.cloud.tools.jib.JibLogger;
import com.google.cloud.tools.jib.Timer;
import com.google.cloud.tools.jib.async.AsyncStep;
import com.google.cloud.tools.jib.configuration.BuildConfiguration;
import com.google.cloud.tools.jib.configuration.credentials.Credential;
import com.google.cloud.tools.jib.configuration.credentials.CredentialRetriever;
import com.google.cloud.tools.jib.registry.credentials.CredentialRetrievalException;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import java.util.Optional;
import java.util.concurrent.Callable;
import javax.annotation.Nullable;

/** Attempts to retrieve registry credentials. */
class RetrieveRegistryCredentialsStep implements AsyncStep<Credential>, Callable<Credential> {

  private static final String DESCRIPTION = "Retrieving registry credentials for %s";

  /** Retrieves credentials for the base image. */
  static RetrieveRegistryCredentialsStep forBaseImage(
      ListeningExecutorService listeningExecutorService, BuildConfiguration buildConfiguration) {
    return new RetrieveRegistryCredentialsStep(
        listeningExecutorService,
        buildConfiguration.getBuildLogger(),
        buildConfiguration.getBaseImageConfiguration().getImageRegistry(),
        buildConfiguration.getBaseImageConfiguration().getCredentialRetrievers());
  }

  /** Retrieves credentials for the target image. */
  static RetrieveRegistryCredentialsStep forTargetImage(
      ListeningExecutorService listeningExecutorService, BuildConfiguration buildConfiguration) {
    return new RetrieveRegistryCredentialsStep(
        listeningExecutorService,
        buildConfiguration.getBuildLogger(),
        buildConfiguration.getTargetImageConfiguration().getImageRegistry(),
        buildConfiguration.getTargetImageConfiguration().getCredentialRetrievers());
  }

  private final JibLogger buildLogger;
  private final String registry;
  private final ImmutableList<CredentialRetriever> credentialRetrievers;

  private final ListenableFuture<Credential> listenableFuture;

  @VisibleForTesting
  RetrieveRegistryCredentialsStep(
      ListeningExecutorService listeningExecutorService,
      JibLogger buildLogger,
      String registry,
      ImmutableList<CredentialRetriever> credentialRetrievers) {
    this.buildLogger = buildLogger;
    this.registry = registry;
    this.credentialRetrievers = credentialRetrievers;

    listenableFuture = listeningExecutorService.submit(this);
  }

  @Override
  public ListenableFuture<Credential> getFuture() {
    return listenableFuture;
  }

  @Override
  @Nullable
  public Credential call() throws CredentialRetrievalException {
    buildLogger.lifecycle(String.format(DESCRIPTION, registry) + "...");

    try (Timer ignored = new Timer(buildLogger, String.format(DESCRIPTION, registry))) {
      for (CredentialRetriever credentialRetriever : credentialRetrievers) {
        Optional<Credential> optionalCredential = credentialRetriever.retrieve();
        if (optionalCredential.isPresent()) {
          return optionalCredential.get();
        }
      }

      /*
       * If no credentials found, give an info (not warning because in most cases, the base image is
       * public and does not need extra credentials) and return null.
       */
      buildLogger.info("No credentials could be retrieved for registry " + registry);
      return null;
    }
  }
}
