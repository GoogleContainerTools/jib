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
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import org.junit.rules.ExternalResource;
import org.mindrot.jbcrypt.BCrypt;

/** Runs a local registry. */
public class LocalRegistry extends ExternalResource {

  private final String containerName = "registry-" + UUID.randomUUID();
  private final String dockerHost =
      System.getenv("DOCKER_IP") != null ? System.getenv("DOCKER_IP") : "localhost";
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

  public String getDockerHost() {
    if (System.getenv("KOKORO_JOB_CLUSTER") != null
        && System.getenv("KOKORO_JOB_CLUSTER").equals("GCP_UBUNTU_DOCKER")) {
      // Since build script will be running inside a container, will need to use
      // registry container IP to reach local registry through HTTP
      return getRegistryContainerIp();
    } else {
      return dockerHost;
    }
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
    List<String> dockerTokens =
        Lists.newArrayList(
            "docker", "run", "--rm", "-d", "-p", port + ":5000", "--name", containerName);
    if (username != null && password != null) {
      // Equivalent of "$ htpasswd -nbB username password".
      // https://httpd.apache.org/docs/2.4/misc/password_encryptions.html
      // BCrypt generates hashes using $2a$ algorithm (instead of $2y$ from docs), but this seems
      // to work okay
      String credentialString = username + ":" + BCrypt.hashpw(password, BCrypt.gensalt());
      FileAttribute<Set<PosixFilePermission>> attrs =
          PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwxr-xr-x"));
      Path tempFolder = Files.createTempDirectory(Paths.get("/tmp"), "", attrs);
      Files.write(
          tempFolder.resolve("htpasswd"), credentialString.getBytes(StandardCharsets.UTF_8));
      boolean isOnKokoroCI =
          System.getenv("KOKORO_JOB_CLUSTER") != null
              && System.getenv("KOKORO_JOB_CLUSTER").equals("MACOS_EXTERNAL");
      String tempAuthFile = tempFolder + ":/auth";
      String authenticationVolume = isOnKokoroCI ? "/home/docker/auth:/auth" : tempAuthFile;
      // Run the Docker registry
      dockerTokens.addAll(
          Arrays.asList(
              "-v",
              // Volume mount used for storing credentials
              authenticationVolume,
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

  /** Gets local registry container IP. */
  public String getRegistryContainerIp() {
    // Gets local registry container IP
    List<String> dockerTokens =
        Lists.newArrayList(
            "docker",
            "inspect",
            "-f",
            "'{{range.NetworkSettings.Networks}}{{.IPAddress}}{{end}}'",
            containerName);
    try {
      String result = new Command(dockerTokens).run();
      // Remove single quotes and LF from result (e.g. '127.0.0.1'\n)
      return result.replaceAll("['\n]", "");
    } catch (InterruptedException | IOException ex) {
      throw new RuntimeException("Could get local registry IP for: " + containerName, ex);
    }
  }

  /**
   * Pulls an image to a Docker daemon (not to this local registry).
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
    new Command("docker", "tag", from, getDockerHost() + ":" + port + "/" + to).run();
    new Command("docker", "push", getDockerHost() + ":" + port + "/" + to).run();
    logout();
  }

  private void login() throws IOException, InterruptedException {
    if (username != null && password != null) {
      new Command(
              "docker", "login", getDockerHost() + ":" + port, "-u", username, "--password-stdin")
          .run(password.getBytes(StandardCharsets.UTF_8));
    }
  }

  private void logout() throws IOException, InterruptedException {
    if (username != null && password != null) {
      new Command("docker", "logout", getDockerHost() + ":" + port).run();
    }
  }

  private void waitUntilReady() throws InterruptedException, IOException {
    URL queryUrl = new URL("http://" + getDockerHost() + ":" + port + "/v2/_catalog");

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
