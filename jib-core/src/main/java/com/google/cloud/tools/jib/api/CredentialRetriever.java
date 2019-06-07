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

package com.google.cloud.tools.jib.api;

import com.google.cloud.tools.jib.registry.credentials.CredentialRetrievalException;
import java.util.Optional;

/** Retrieves credentials for a registry. */
@FunctionalInterface
public interface CredentialRetriever {

  /**
   * Fetches the credentials. <b>Implementations must be thread-safe.</b>
   *
   * <p>Implementations should return {@link Optional#empty} if no credentials could be fetched with
   * this {@link CredentialRetriever} (and so other credential retrieval methods may be tried), or
   * throw an exception something went wrong when fetching the credentials.
   *
   * @return the fetched credentials or {@link Optional#empty} if no credentials could be fetched
   *     with this provider
   * @throws CredentialRetrievalException if the credential retrieval encountered an exception
   */
  Optional<Credential> retrieve() throws CredentialRetrievalException;
}
