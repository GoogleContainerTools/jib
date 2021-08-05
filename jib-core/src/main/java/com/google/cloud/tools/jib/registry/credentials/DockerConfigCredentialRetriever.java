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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.util.Base64;
import com.google.cloud.tools.jib.api.Credential;
import com.google.cloud.tools.jib.api.LogEvent;
import com.google.cloud.tools.jib.registry.RegistryAliasGroup;
import com.google.cloud.tools.jib.registry.credentials.json.DockerConfigTemplate;
import com.google.cloud.tools.jib.registry.credentials.json.DockerConfigTemplate.AuthTemplate;
import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Retrieves registry credentials from the Docker config.
 *
 * <p>The credentials are searched in the following order (stopping when credentials are found):
 *
 * <ol>
 *   <li>The credential helper from {@code credHelpers} defined for a registry, if available.
 *   <li>The {@code credsStore} credential helper, if available.
 *   <li>If there is an {@code auth} defined for a registry.
 * </ol>
 *
 * @see <a
 *     href="https://docs.docker.com/engine/reference/commandline/login/">https://docs.docker.com/engine/reference/commandline/login/</a>
 */
public class DockerConfigCredentialRetriever {

  private final String registry;
  private final Path dockerConfigFile;
  private final boolean legacyConfigFormat;

  public static DockerConfigCredentialRetriever create(String registry, Path dockerConfigFile) {
    return new DockerConfigCredentialRetriever(registry, dockerConfigFile, false);
  }

  public static DockerConfigCredentialRetriever createForLegacyFormat(
      String registry, Path dockerConfigFile) {
    return new DockerConfigCredentialRetriever(registry, dockerConfigFile, true);
  }

  private DockerConfigCredentialRetriever(
      String registry, Path dockerConfigFile, boolean legacyConfigFormat) {
    this.registry = registry;
    this.dockerConfigFile = dockerConfigFile;
    this.legacyConfigFormat = legacyConfigFormat;
  }

  public Path getDockerConfigFile() {
    return dockerConfigFile;
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

    ObjectMapper objectMapper =
        new ObjectMapper().configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true);
    try (InputStream fileIn = Files.newInputStream(dockerConfigFile)) {
      if (legacyConfigFormat) {
        // legacy config format is the value of the "auths":{ <map> } block of the new config (i.e.,
        // the <map> of string -> DockerConfigTemplate.AuthTemplate).
        Map<String, AuthTemplate> auths =
            objectMapper.readValue(fileIn, new TypeReference<Map<String, AuthTemplate>>() {});
        DockerConfig dockerConfig = new DockerConfig(new DockerConfigTemplate(auths));
        return retrieve(dockerConfig, logger);
      }

      DockerConfig dockerConfig =
          new DockerConfig(objectMapper.readValue(fileIn, DockerConfigTemplate.class));
      return retrieve(dockerConfig, logger);
    }
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
      // First, find a credential helper from "credentialHelpers" and "credsStore" in order.
      DockerCredentialHelper dockerCredentialHelper =
          dockerConfig.getCredentialHelperFor(registryAlias);
      if (dockerCredentialHelper != null) {
        try {
          Path helperPath = dockerCredentialHelper.getCredentialHelper();
          logger.accept(LogEvent.info("trying " + helperPath + " for " + registryAlias));
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

      // Lastly, find defined auth.
      AuthTemplate auth = dockerConfig.getAuthFor(registryAlias);
      if (auth != null) {
        if (auth.getAuth() != null) {
          // 'auth' is a basic authentication token that should be parsed back into credentials
          String usernameColonPassword =
              new String(Base64.decodeBase64(auth.getAuth()), StandardCharsets.UTF_8);
          String username = usernameColonPassword.substring(0, usernameColonPassword.indexOf(":"));
          String password = usernameColonPassword.substring(usernameColonPassword.indexOf(":") + 1);
          logger.accept(
              LogEvent.info(
                  "Docker config auths section defines credentials for " + registryAlias));
          if (auth.getIdentityToken() != null
              // These username and password checks may be unnecessary, but doing so to restrict the
              // scope only to the Azure behavior to maintain maximum backward-compatibilty.
              && username.equals("00000000-0000-0000-0000-000000000000")
              && password.isEmpty()) {
            logger.accept(
                LogEvent.info("Using 'identityToken' in Docker config auth for " + registryAlias));
            return Optional.of(
                Credential.from(Credential.OAUTH2_TOKEN_USER_NAME, auth.getIdentityToken()));
          }
          return Optional.of(Credential.from(username, password));
        } else if (auth.getUsername() != null && auth.getPassword() != null) {
          logger.accept(
              LogEvent.info(
                  "Docker config auths section defines username and password for "
                      + registryAlias));
          return Optional.of(Credential.from(auth.getUsername(), auth.getPassword()));
        }
      }
    }
    return Optional.empty();
  }
}
