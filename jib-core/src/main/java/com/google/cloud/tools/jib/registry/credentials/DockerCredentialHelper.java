/*
 * Copyright 2017 Google LLC.
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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.cloud.tools.jib.api.Credential;
import com.google.cloud.tools.jib.json.JsonTemplate;
import com.google.cloud.tools.jib.json.JsonTemplateMapper;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.io.CharStreams;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.annotation.Nullable;

/**
 * Retrieves Docker credentials with a Docker credential helper.
 *
 * @see <a
 *     href="https://github.com/docker/docker-credential-helpers">https://github.com/docker/docker-credential-helpers</a>
 */
public class DockerCredentialHelper {

  public static final String CREDENTIAL_HELPER_PREFIX = "docker-credential-";

  private final String serverUrl;
  private final Path credentialHelper;

  /** Template for a Docker credential helper output. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  private static class DockerCredentialsTemplate implements JsonTemplate {

    @Nullable private String Username;
    @Nullable private String Secret;
  }

  /**
   * Constructs a new {@link DockerCredentialHelper}.
   *
   * @param serverUrl the server URL to pass into the credential helper
   * @param credentialHelper the path to the credential helper executable
   */
  public DockerCredentialHelper(String serverUrl, Path credentialHelper) {
    this.serverUrl = serverUrl;
    this.credentialHelper = credentialHelper;
  }

  DockerCredentialHelper(String registry, String credentialHelperSuffix) {
    this(registry, Paths.get(CREDENTIAL_HELPER_PREFIX + credentialHelperSuffix));
  }

  /**
   * Calls the credential helper CLI in the form:
   *
   * <pre>{@code
   * echo -n <server URL> | docker-credential-<credential helper suffix> get
   * }</pre>
   *
   * @return the Docker credentials by calling the corresponding CLI
   * @throws IOException if writing/reading process input/output fails
   * @throws CredentialHelperUnhandledServerUrlException if no credentials could be found for the
   *     corresponding server
   * @throws CredentialHelperNotFoundException if the credential helper CLI doesn't exist
   */
  public Credential retrieve()
      throws IOException, CredentialHelperUnhandledServerUrlException,
          CredentialHelperNotFoundException {
    try {
      String[] credentialHelperCommand = {credentialHelper.toString(), "get"};

      Process process = new ProcessBuilder(credentialHelperCommand).start();
      try (OutputStream processStdin = process.getOutputStream()) {
        processStdin.write(serverUrl.getBytes(StandardCharsets.UTF_8));
      }

      try (InputStreamReader processStdoutReader =
          new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)) {
        String output = CharStreams.toString(processStdoutReader);

        // Throws an exception if the credential store does not have credentials for serverUrl.
        if (output.contains("credentials not found in native keychain")) {
          throw new CredentialHelperUnhandledServerUrlException(
              credentialHelper, serverUrl, output);
        }
        if (output.isEmpty()) {
          try (InputStreamReader processStderrReader =
              new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8)) {
            String errorOutput = CharStreams.toString(processStderrReader);
            throw new CredentialHelperUnhandledServerUrlException(
                credentialHelper, serverUrl, errorOutput);
          }
        }

        try {
          DockerCredentialsTemplate dockerCredentials =
              JsonTemplateMapper.readJson(output, DockerCredentialsTemplate.class);
          if (Strings.isNullOrEmpty(dockerCredentials.Username)
              || Strings.isNullOrEmpty(dockerCredentials.Secret)) {
            throw new CredentialHelperUnhandledServerUrlException(
                credentialHelper, serverUrl, output);
          }

          return Credential.from(dockerCredentials.Username, dockerCredentials.Secret);

        } catch (JsonProcessingException ex) {
          throw new CredentialHelperUnhandledServerUrlException(
              credentialHelper, serverUrl, output);
        }
      }

    } catch (IOException ex) {
      if (ex.getMessage() == null) {
        throw ex;
      }

      // Checks if the failure is due to a nonexistent credential helper CLI.
      if (ex.getMessage().contains("No such file or directory")
          || ex.getMessage().contains("cannot find the file")) {
        throw new CredentialHelperNotFoundException(credentialHelper, ex);
      }

      throw ex;
    }
  }

  @VisibleForTesting
  Path getCredentialHelper() {
    return credentialHelper;
  }
}
