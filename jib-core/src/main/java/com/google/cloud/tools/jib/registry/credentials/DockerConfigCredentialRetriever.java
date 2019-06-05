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

package com.google.cloud.tools.jib.registry.credentials;

import com.google.api.client.util.Base64;
import com.google.cloud.tools.jib.api.Credential;
import com.google.cloud.tools.jib.api.LogEvent;
import com.google.cloud.tools.jib.json.JsonTemplateMapper;
import com.google.cloud.tools.jib.registry.RegistryAliasGroup;
import com.google.cloud.tools.jib.registry.credentials.json.DockerConfigTemplate;
import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Retrieves registry credentials from the Docker config.
 *
 * <p>The credentials are searched in the following order (stopping when credentials are found):
 *
 * <ol>
 *   <li>If there is an {@code auth} defined for a registry.
 *   <li>Using the {@code credsStore} credential helper, if available.
 *   <li>Using the credential helper from {@code credHelpers}, if available.
 * </ol>
 *
 * @see <a
 *     href="https://docs.docker.com/engine/reference/commandline/login/">https://docs.docker.com/engine/reference/commandline/login/</a>
 */
public class DockerConfigCredentialRetriever {

  /**
   * @see <a
   *     href="https://docs.docker.com/engine/reference/commandline/login/#privileged-user-requirement">https://docs.docker.com/engine/reference/commandline/login/#privileged-user-requirement</a>
   */
  private static final Path DOCKER_CONFIG_FILE =
      Paths.get(System.getProperty("user.home"), ".docker", "config.json");

  private final String registry;
  private final Path dockerConfigFile;

  public DockerConfigCredentialRetriever(String registry) {
    this(registry, DOCKER_CONFIG_FILE);
  }

  @VisibleForTesting
  public DockerConfigCredentialRetriever(String registry, Path dockerConfigFile) {
    this.registry = registry;
    this.dockerConfigFile = dockerConfigFile;
  }

  /**
   * Retrieves credentials for a registry. Tries all possible known aliases.
   *
   * @param logger a consumer for handling log events
   * @return {@link Credential} found for {@code registry}, or {@link Optional#empty} if not found
   * @throws IOException if failed to parse the config JSON
   */
  public Optional<Credential> retrieve(Consumer<LogEvent> logger) throws IOException {
    if (!Files.exists(dockerConfigFile)) {
      return Optional.empty();
    }
    DockerConfig dockerConfig =
        new DockerConfig(
            JsonTemplateMapper.readJsonFromFile(dockerConfigFile, DockerConfigTemplate.class));
    return retrieve(dockerConfig, logger);
  }

  /**
   * Retrieves credentials for a registry alias from a {@link DockerConfig}.
   *
   * @param dockerConfig the {@link DockerConfig} to retrieve from
   * @param logger a consumer for handling log events
   * @return the retrieved credentials, or {@code Optional#empty} if none are found
   */
  @VisibleForTesting
  Optional<Credential> retrieve(DockerConfig dockerConfig, Consumer<LogEvent> logger) {
    for (String registryAlias : RegistryAliasGroup.getAliasesGroup(registry)) {
      // First, tries to find defined auth.
      String auth = dockerConfig.getAuthFor(registryAlias);
      if (auth != null) {
        // 'auth' is a basic authentication token that should be parsed back into credentials
        String usernameColonPassword =
            new String(Base64.decodeBase64(auth), StandardCharsets.UTF_8);
        String username = usernameColonPassword.substring(0, usernameColonPassword.indexOf(":"));
        String password = usernameColonPassword.substring(usernameColonPassword.indexOf(":") + 1);
        return Optional.of(Credential.from(username, password));
      }

      // Then, tries to use a defined credHelpers credential helper.
      DockerCredentialHelper dockerCredentialHelper =
          dockerConfig.getCredentialHelperFor(registryAlias);
      if (dockerCredentialHelper != null) {
        try {
          // Tries with the given registry alias (may be the original registry).
          return Optional.of(dockerCredentialHelper.retrieve());

        } catch (IOException
            | CredentialHelperUnhandledServerUrlException
            | CredentialHelperNotFoundException ex) {
          // Warns the user that the specified credential helper cannot be used.
          if (ex.getMessage() != null) {
            logger.accept(LogEvent.warn(ex.getMessage()));
            if (ex.getCause() != null && ex.getCause().getMessage() != null) {
              logger.accept(LogEvent.warn("  Caused by: " + ex.getCause().getMessage()));
            }
          }
        }
      }
    }
    return Optional.empty();
  }
}
