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

package com.google.cloud.tools.jib.builder.steps;

import com.google.cloud.tools.jib.Timer;
import com.google.cloud.tools.jib.async.AsyncStep;
import com.google.cloud.tools.jib.builder.BuildLogger;
import com.google.cloud.tools.jib.configuration.BuildConfiguration;
import com.google.cloud.tools.jib.http.Authorization;
import com.google.cloud.tools.jib.registry.credentials.DockerConfigCredentialRetriever;
import com.google.cloud.tools.jib.registry.credentials.DockerCredentialHelperFactory;
import com.google.cloud.tools.jib.registry.credentials.NonexistentDockerCredentialHelperException;
import com.google.cloud.tools.jib.registry.credentials.NonexistentServerUrlDockerCredentialHelperException;
import com.google.cloud.tools.jib.registry.credentials.RegistryCredentials;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import java.io.IOException;
import java.util.concurrent.Callable;
import javax.annotation.Nullable;

/** Attempts to retrieve registry credentials. */
class RetrieveRegistryCredentialsStep implements AsyncStep<Authorization>, Callable<Authorization> {

  private static final String DESCRIPTION = "Retrieving registry credentials for %s";

  /**
   * Defines common credential helpers to use as defaults. Maps from registry suffix to credential
   * helper suffix.
   */
  private static final ImmutableMap<String, String> COMMON_CREDENTIAL_HELPERS =
      ImmutableMap.of("gcr.io", "gcr", "amazonaws.com", "ecr-login");

  /** Retrieves credentials for the base image. */
  static RetrieveRegistryCredentialsStep forBaseImage(
      ListeningExecutorService listeningExecutorService, BuildConfiguration buildConfiguration) {
    return new RetrieveRegistryCredentialsStep(
        listeningExecutorService,
        buildConfiguration.getBuildLogger(),
        buildConfiguration.getBaseImageRegistry(),
        buildConfiguration.getBaseImageCredentialHelperName(),
        buildConfiguration.getKnownBaseRegistryCredentials());
  }

  /** Retrieves credentials for the target image. */
  static RetrieveRegistryCredentialsStep forTargetImage(
      ListeningExecutorService listeningExecutorService, BuildConfiguration buildConfiguration) {
    return new RetrieveRegistryCredentialsStep(
        listeningExecutorService,
        buildConfiguration.getBuildLogger(),
        buildConfiguration.getTargetImageRegistry(),
        buildConfiguration.getTargetImageCredentialHelperName(),
        buildConfiguration.getKnownTargetRegistryCredentials());
  }

  private final BuildLogger buildLogger;
  private final String registry;
  @Nullable private final String credentialHelperSuffix;
  @Nullable private final RegistryCredentials knownRegistryCredentials;
  private final DockerCredentialHelperFactory dockerCredentialHelperFactory;
  private final DockerConfigCredentialRetriever dockerConfigCredentialRetriever;

  private final ListenableFuture<Authorization> listenableFuture;

  @VisibleForTesting
  RetrieveRegistryCredentialsStep(
      ListeningExecutorService listeningExecutorService,
      BuildLogger buildLogger,
      String registry,
      @Nullable String credentialHelperSuffix,
      @Nullable RegistryCredentials knownRegistryCredentials,
      DockerCredentialHelperFactory dockerCredentialHelperFactory,
      DockerConfigCredentialRetriever dockerConfigCredentialRetriever) {
    this.buildLogger = buildLogger;
    this.registry = registry;
    this.credentialHelperSuffix = credentialHelperSuffix;
    this.knownRegistryCredentials = knownRegistryCredentials;
    this.dockerCredentialHelperFactory = dockerCredentialHelperFactory;
    this.dockerConfigCredentialRetriever = dockerConfigCredentialRetriever;

    listenableFuture = listeningExecutorService.submit(this);
  }

  /** Instantiate with {@link #forBaseImage} or {@link #forTargetImage}. */
  private RetrieveRegistryCredentialsStep(
      ListeningExecutorService listeningExecutorService,
      BuildLogger buildLogger,
      String registry,
      @Nullable String credentialHelperSuffix,
      @Nullable RegistryCredentials knownRegistryCredentials) {
    this(
        listeningExecutorService,
        buildLogger,
        registry,
        credentialHelperSuffix,
        knownRegistryCredentials,
        new DockerCredentialHelperFactory(),
        new DockerConfigCredentialRetriever(registry));
  }

  @Override
  public ListenableFuture<Authorization> getFuture() {
    return listenableFuture;
  }

  @Override
  @Nullable
  public Authorization call() throws IOException, NonexistentDockerCredentialHelperException {
    buildLogger.lifecycle(String.format(DESCRIPTION, registry) + "...");

    try (Timer ignored = new Timer(buildLogger, String.format(DESCRIPTION, registry))) {
      // Tries to get registry credentials from Docker credential helpers.
      if (credentialHelperSuffix != null) {
        Authorization authorization = retrieveFromCredentialHelper(credentialHelperSuffix);
        if (authorization != null) {
          return authorization;
        }
      }

      // Tries to get registry credentials from known registry credentials.
      if (knownRegistryCredentials != null) {
        logGotCredentialsFrom(knownRegistryCredentials.getCredentialSource());
        return knownRegistryCredentials.getAuthorization();
      }

      // Tries to infer common credential helpers for known registries.
      for (String registrySuffix : COMMON_CREDENTIAL_HELPERS.keySet()) {
        if (registry.endsWith(registrySuffix)) {
          try {
            String commonCredentialHelper = COMMON_CREDENTIAL_HELPERS.get(registrySuffix);
            if (commonCredentialHelper == null) {
              throw new IllegalStateException("No COMMON_CREDENTIAL_HELPERS should be null");
            }
            Authorization authorization = retrieveFromCredentialHelper(commonCredentialHelper);
            if (authorization != null) {
              return authorization;
            }

          } catch (NonexistentDockerCredentialHelperException ex) {
            if (ex.getMessage() != null) {
              // Warns the user that the specified (or inferred) credential helper is not on the
              // system.
              buildLogger.warn(ex.getMessage());
            }
          }
        }
      }

      // Tries to get registry credentials from the Docker config.
      try {
        Authorization dockerConfigAuthorization = dockerConfigCredentialRetriever.retrieve();
        if (dockerConfigAuthorization != null) {
          buildLogger.info("Using credentials from Docker config for " + registry);
          return dockerConfigAuthorization;
        }

      } catch (IOException ex) {
        buildLogger.info("Unable to parse Docker config");
      }

      /*
       * If no credentials found, give an info (not warning because in most cases, the base image is
       * public and does not need extra credentials) and return null.
       */
      buildLogger.info("No credentials could be retrieved for registry " + registry);
      return null;
    }
  }

  /**
   * Attempts to retrieve authorization for the registry using {@code
   * docker-credential-[credentialHelperSuffix]}.
   */
  @VisibleForTesting
  @Nullable
  Authorization retrieveFromCredentialHelper(String credentialHelperSuffix)
      throws NonexistentDockerCredentialHelperException, IOException {
    buildLogger.info("Checking credentials from docker-credential-" + credentialHelperSuffix);

    try {
      Authorization authorization =
          dockerCredentialHelperFactory
              .newDockerCredentialHelper(registry, credentialHelperSuffix)
              .retrieve();
      logGotCredentialsFrom("docker-credential-" + credentialHelperSuffix);
      return authorization;

    } catch (NonexistentServerUrlDockerCredentialHelperException ex) {
      buildLogger.info(
          "No credentials for " + registry + " in docker-credential-" + credentialHelperSuffix);
      return null;
    }
  }

  private void logGotCredentialsFrom(String credentialSource) {
    buildLogger.info("Using " + credentialSource + " for " + registry);
  }
}
