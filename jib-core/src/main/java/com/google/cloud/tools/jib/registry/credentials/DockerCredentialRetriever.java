/*
 * Copyright 2017 Google Inc.
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

package com.google.cloud.tools.jib.registry;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.google.cloud.tools.jib.http.Authorization;
import com.google.cloud.tools.jib.http.Authorizations;
import com.google.cloud.tools.jib.json.JsonTemplate;
import com.google.cloud.tools.jib.json.JsonTemplateMapper;
import com.google.common.io.CharStreams;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

// TODO: Replace with non-CLI method.
/**
 * Retrieves Docker credentials with a Docker credential helper.
 *
 * @see <a
 *     href="https://github.com/docker/docker-credential-helpers">https://github.com/docker/docker-credential-helpers</a>
 */
public class DockerCredentialRetriever {

  private final String serverUrl;
  private final String credentialHelperSuffix;

  /** Template for a Docker credential helper output. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  private static class DockerCredentialsTemplate extends JsonTemplate {

    private String Username;
    private String Secret;
  }

  /**
   * @param serverUrl the server URL to pass into the credential helper
   * @param credentialHelperSuffix the credential helper CLI suffix
   */
  public DockerCredentialRetriever(String serverUrl, String credentialHelperSuffix) {
    this.serverUrl = serverUrl;
    this.credentialHelperSuffix = credentialHelperSuffix;
  }

  /**
   * Retrieves the Docker credentials by calling the corresponding CLI.
   *
   * <p>The credential helper CLI is called in the form:
   *
   * <pre>{@code
   * echo -n <server URL> | docker-credential-<credential helper suffix> get
   * }</pre>
   */
  public Authorization retrieve()
      throws IOException, NonexistentServerUrlDockerCredentialHelperException,
          NonexistentDockerCredentialHelperException {
    try {
      String credentialHelper = "docker-credential-" + credentialHelperSuffix;
      String[] credentialHelperCommand = {credentialHelper, "get"};

      Process process = new ProcessBuilder(credentialHelperCommand).start();
      process.getOutputStream().write(serverUrl.getBytes(StandardCharsets.UTF_8));
      process.getOutputStream().close();

      try (InputStreamReader processStdoutReader =
          new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)) {
        String output = CharStreams.toString(processStdoutReader);

        // Throws an exception if the credential store does not have credentials for serverUrl.
        if (output.contains("credentials not found in native keychain")) {
          throw new NonexistentServerUrlDockerCredentialHelperException(
              credentialHelper, serverUrl, output);
        }
        if (output.isEmpty()) {
          try (InputStreamReader processStderrReader =
              new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8)) {
            String errorOutput = CharStreams.toString(processStderrReader);
            throw new NonexistentServerUrlDockerCredentialHelperException(
                credentialHelper, serverUrl, errorOutput);
          }
        }

        try {
          DockerCredentialsTemplate dockerCredentials =
              JsonTemplateMapper.readJson(output, DockerCredentialsTemplate.class);

          return Authorizations.withBasicCredentials(
              dockerCredentials.Username, dockerCredentials.Secret);

        } catch (JsonMappingException ex) {
          throw new NonexistentServerUrlDockerCredentialHelperException(
              credentialHelper, serverUrl, output);
        }
      }

    } catch (IOException ex) {
      // Checks if the failure is due to a nonexistent credential helper CLI.
      if (ex.getMessage().contains("No such file or directory")) {
        throw new NonexistentDockerCredentialHelperException(credentialHelperSuffix, ex);
      }
      throw ex;
    }
  }
}
