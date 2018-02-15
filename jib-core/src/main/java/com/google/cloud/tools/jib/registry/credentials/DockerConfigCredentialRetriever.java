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

package com.google.cloud.tools.jib.registry.credentials;

import com.google.cloud.tools.jib.http.Authorization;
import com.google.cloud.tools.jib.http.Authorizations;
import com.google.cloud.tools.jib.json.JsonTemplateMapper;
import com.google.cloud.tools.jib.registry.credentials.json.DockerConfigTemplate;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Retrieves registry credentials from the Docker config.
 *
 * The credentials are searched in the following order (stopping when credentials are found):
 *
 * <ol>
 *   <li>If there is an {@code auth} defined for a registry.</li>
 *   <li>Using the {@code credsStore} credential helper, if available.</li>
 *   <li>Using the credential helper from {@code credHelpers}, if available.</li>
 * </ol>
 *
 * @see <a href="https://docs.docker.com/engine/reference/commandline/login/">https://docs.docker.com/engine/reference/commandline/login/</a>
 */
public class DockerConfigCredentialRetriever {

  private static final Path dockerConfigFile = Paths.get(System.getProperty("user.home")).resolve(".docker").resolve("config.json");

  private static DockerConfigTemplate dockerConfigTemplate;

  /** @return {@link Authorization} found for {@code registry}, or {@code null} if not found */
  @Nullable
  private static Authorization retrieve(String registry) throws IOException {
    if (dockerConfigTemplate == null) {
      // Loads the Docker config if it has not yet been loaded.
      if (!Files.exists(dockerConfigFile)) {
        return null;
      }
      dockerConfigTemplate = JsonTemplateMapper.readJsonFromFile(dockerConfigFile, DockerConfigTemplate.class);
    }

    String auth = dockerConfigTemplate.getAuthFor(registry);
    if (auth != null) {
      return Authorizations.withBasicToken(auth);
    }

    String credentialHelperSuffix = dockerConfigTemplate.getCredentialHelperFor(registry);
    if (credentialHelperSuffix != null) {
      try {
        DockerCredentialRetriever dockerCredentialRetriever = new DockerCredentialRetriever(registry, credentialHelperSuffix);
        return dockerCredentialRetriever.retrieve();

      } catch (NonexistentServerUrlDockerCredentialHelperException | NonexistentDockerCredentialHelperException ex) {
        // Ignores credential helper retrieval exceptions.
      }
    }

    return null;
  }
}
