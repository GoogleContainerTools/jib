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
import com.google.cloud.tools.jib.configuration.BuildContext;
import com.google.cloud.tools.jib.configuration.ImageConfiguration;
import com.google.cloud.tools.jib.event.EventHandlers;
import com.google.cloud.tools.jib.registry.credentials.CredentialRetrievalException;
import java.util.Optional;

/** Attempts to retrieve registry credentials. */
class RegistryCredentialRetriever {

  private RegistryCredentialRetriever() {}

  /** Retrieves credentials for the base image. */
  static Optional<Credential> getBaseImageCredential(BuildContext buildContext)
      throws CredentialRetrievalException {
    return retrieve(buildContext.getBaseImageConfiguration(), buildContext.getEventHandlers());
  }

  /** Retrieves credentials for the target image. */
  static Optional<Credential> getTargetImageCredential(BuildContext buildContext)
      throws CredentialRetrievalException {
    return retrieve(buildContext.getTargetImageConfiguration(), buildContext.getEventHandlers());
  }

  private static Optional<Credential> retrieve(
      ImageConfiguration imageConfiguration, EventHandlers eventHandlers)
      throws CredentialRetrievalException {
    for (CredentialRetriever retriever : imageConfiguration.getCredentialRetrievers()) {
      Optional<Credential> credential = retriever.retrieve();
      if (credential.isPresent()) {
        return credential;
      }
    }

    String registry = imageConfiguration.getImageRegistry();
    String repository = imageConfiguration.getImageRepository();
    eventHandlers.dispatch(
        LogEvent.info("No credentials could be retrieved for " + registry + "/" + repository));
    return Optional.empty();
  }
}
