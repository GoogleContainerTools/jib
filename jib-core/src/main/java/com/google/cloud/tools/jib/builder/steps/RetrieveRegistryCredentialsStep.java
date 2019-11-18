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
import com.google.cloud.tools.jib.api.CredentialRetriever;
import com.google.cloud.tools.jib.api.LogEvent;
import com.google.cloud.tools.jib.builder.ProgressEventDispatcher;
import com.google.cloud.tools.jib.builder.TimerEventDispatcher;
import com.google.cloud.tools.jib.configuration.BuildContext;
import com.google.cloud.tools.jib.event.EventHandlers;
import com.google.cloud.tools.jib.registry.credentials.CredentialRetrievalException;
import com.google.common.collect.ImmutableList;
import java.util.Optional;
import java.util.concurrent.Callable;

/** Attempts to retrieve registry credentials. */
class RetrieveRegistryCredentialsStep implements Callable<Optional<Credential>> {

  /** Retrieves credentials for the base image. */
  static RetrieveRegistryCredentialsStep forBaseImage(
      BuildContext buildContext, ProgressEventDispatcher.Factory progressEventDispatcherFactory) {
    return new RetrieveRegistryCredentialsStep(
        buildContext,
        progressEventDispatcherFactory,
        buildContext.getBaseImageConfiguration().getImageRegistry(),
        buildContext.getBaseImageConfiguration().getCredentialRetrievers());
  }

  /** Retrieves credentials for the target image. */
  static RetrieveRegistryCredentialsStep forTargetImage(
      BuildContext buildContext, ProgressEventDispatcher.Factory progressEventDispatcherFactory) {
    return new RetrieveRegistryCredentialsStep(
        buildContext,
        progressEventDispatcherFactory,
        buildContext.getTargetImageConfiguration().getImageRegistry(),
        buildContext.getTargetImageConfiguration().getCredentialRetrievers());
  }

  private final BuildContext buildContext;
  private final ProgressEventDispatcher.Factory progressEventDispatcherFactory;

  private final String registry;
  private final ImmutableList<CredentialRetriever> credentialRetrievers;

  RetrieveRegistryCredentialsStep(
      BuildContext buildContext,
      ProgressEventDispatcher.Factory progressEventDispatcherFactory,
      String registry,
      ImmutableList<CredentialRetriever> credentialRetrievers) {
    this.buildContext = buildContext;
    this.progressEventDispatcherFactory = progressEventDispatcherFactory;
    this.registry = registry;
    this.credentialRetrievers = credentialRetrievers;
  }

  @Override
  public Optional<Credential> call() throws CredentialRetrievalException {
    String description = "Retrieving registry credentials for " + registry;
    EventHandlers eventHandlers = buildContext.getEventHandlers();
    eventHandlers.dispatch(LogEvent.progress(description + "..."));

    try (ProgressEventDispatcher ignored =
            progressEventDispatcherFactory.create("retrieving credentials for " + registry, 1);
        TimerEventDispatcher ignored2 = new TimerEventDispatcher(eventHandlers, description)) {
      for (CredentialRetriever credentialRetriever : credentialRetrievers) {
        Optional<Credential> optionalCredential = credentialRetriever.retrieve();
        if (optionalCredential.isPresent()) {
          return optionalCredential;
        }
      }

      // If no credentials found, give an info (not warning because in most cases, the base image is
      // public and does not need extra credentials) and return empty.
      eventHandlers.dispatch(
          LogEvent.info("No credentials could be retrieved for registry " + registry));
      return Optional.empty();
    }
  }
}
