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

package com.google.cloud.tools.jib.registry;

import com.google.cloud.tools.jib.Command;
import com.google.common.collect.Lists;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;
import javax.annotation.Nullable;
import org.junit.rules.ExternalResource;
import org.mindrot.jbcrypt.BCrypt;

/** Runs a local registry. */
public class LocalRegistry extends ExternalResource {

  private final String containerName = "registry-" + UUID.randomUUID();
  private final int port;
  @Nullable private final String username;
  @Nullable private final String password;

  public LocalRegistry(int port) {
    this(port, null, null);
  }

  public LocalRegistry(int port, String username, String password) {
    this.port = port;
    this.username = username;
    this.password = password;
  }

  /** Starts the local registry. */
  @Override
  protected void before() throws IOException, InterruptedException {
    start();
  }

  @Override
  protected void after() {
    stop();
  }

  /** Starts the registry. */
  public void start() throws IOException, InterruptedException {
    // Runs the Docker registry.
    ArrayList<String> dockerTokens =
        Lists.newArrayList(
            "docker", "run", "--rm", "-d", "-p", port + ":5000", "--name", containerName);
    if (username != null && password != null) {
      // Equivalent of "$ htpasswd -nbB username password".
      // https://httpd.apache.org/docs/2.4/misc/password_encryptions.html
      // BCrypt generates hashes using $2a$ algorithm (instead of $2y$ from docs), but this seems
      // to work okay
      String credentialString = username + ":" + BCrypt.hashpw(password, BCrypt.gensalt());
      Path tempFolder = Files.createTempDirectory(Paths.get("/tmp"), "");
      Files.write(
          tempFolder.resolve("htpasswd"), credentialString.getBytes(StandardCharsets.UTF_8));

      // Run the Docker registry
      dockerTokens.addAll(
          Arrays.asList(
              "-v",
              // Volume mount used for storing credentials
              tempFolder + ":/auth",
              "-e",
              "REGISTRY_AUTH=htpasswd",
              "-e",
              "REGISTRY_AUTH_HTPASSWD_REALM=Registry Realm",
              "-e",
              "REGISTRY_AUTH_HTPASSWD_PATH=/auth/htpasswd"));
    }
    dockerTokens.add("registry:2");
    new Command(dockerTokens).run();
    waitUntilReady();
  }

  /** Stops the registry. */
  public void stop() {
    try {
      logout();
      new Command("docker", "stop", containerName).run();

    } catch (InterruptedException | IOException ex) {
      throw new RuntimeException("Could not stop local registry fully: " + containerName, ex);
    }
  }

  /**
   * Pulls an image.
   *
   * @param from the image reference to pull
   * @throws IOException if the pull command fails
   * @throws InterruptedException if the pull command is interrupted
   */
  public void pull(String from) throws IOException, InterruptedException {
    login();
    new Command("docker", "pull", from).run();
    logout();
  }

  /**
   * Pulls an image and pushes it to the local registry under a new tag.
   *
   * @param from the image reference to pull
   * @param to the new location of the image (i.e. {@code localhost:[port]/[to]}
   * @throws IOException if the commands fail
   * @throws InterruptedException if the commands are interrupted
   */
  public void pullAndPushToLocal(String from, String to) throws IOException, InterruptedException {
    login();
    new Command("docker", "pull", from).run();
    new Command("docker", "tag", from, "localhost:" + port + "/" + to).run();
    new Command("docker", "push", "localhost:" + port + "/" + to).run();
    logout();
  }

  private void login() throws IOException, InterruptedException {
    if (username != null && password != null) {
      new Command("docker", "login", "localhost:" + port, "-u", username, "--password-stdin")
          .run(password.getBytes(StandardCharsets.UTF_8));
    }
  }

  private void logout() throws IOException, InterruptedException {
    if (username != null && password != null) {
      new Command("docker", "logout", "localhost:" + port).run();
    }
  }

  private void waitUntilReady() throws InterruptedException, MalformedURLException {
    URL queryUrl = new URL("http://localhost:" + port + "/v2/_catalog");

    for (int i = 0; i < 40; i++) {
      try {
        HttpURLConnection connection = (HttpURLConnection) queryUrl.openConnection();
        int code = connection.getResponseCode();
        if (code == HttpURLConnection.HTTP_OK || code == HttpURLConnection.HTTP_UNAUTHORIZED) {
          return;
        }
      } catch (IOException ex) {
        // ignored
      }
      Thread.sleep(250);
    }
  }
}
