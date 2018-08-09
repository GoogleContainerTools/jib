/*
 * Copyright 2018 Google LLC. All rights reserved.
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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import javax.annotation.Nullable;
import org.junit.rules.ExternalResource;

/** Runs a local registry. */
public class LocalRegistry extends ExternalResource {

  private final String containerName = "registry-" + UUID.randomUUID();
  private final int port;
  private final boolean pullBusyBox;
  @Nullable private final String username;
  @Nullable private final String password;

  public LocalRegistry(int port, boolean pullBusyBox) {
    this(port, pullBusyBox, null, null);
  }

  public LocalRegistry(int port, boolean pullBusyBox, String username, String password) {
    this.port = port;
    this.pullBusyBox = pullBusyBox;
    this.username = username;
    this.password = password;
  }

  /** Starts the local registry. */
  @Override
  protected void before() throws IOException, InterruptedException {
    // Runs the Docker registry.
    if (username == null || password == null) {
      new Command(
              "docker",
              "run",
              "-d",
              "-p",
              port + ":5000",
              "--restart=always",
              "--name",
              containerName,
              "registry:2")
          .run();
    } else {
      // Generate the htpasswd file to store credentials
      String credentialString =
          new Command(
                  "docker",
                  "run",
                  "--entrypoint",
                  "htpasswd",
                  "registry:2",
                  "-Bbn",
                  username,
                  password)
              .run();
      Path tempFolder = Files.createTempDirectory("auth");
      Files.write(
          tempFolder.resolve("htpasswd"), credentialString.getBytes(StandardCharsets.UTF_8));

      // Run the Docker registry
      new Command(
              "docker",
              "run",
              "-d",
              "-p",
              port + ":5000",
              "--restart=always",
              "--name",
              containerName,
              "-v",
              // Volume mount used for storing credentials
              tempFolder + ":/auth",
              "-e",
              "REGISTRY_AUTH=htpasswd",
              "-e",
              "REGISTRY_AUTH_HTPASSWD_REALM=Registry Realm",
              "-e",
              "REGISTRY_AUTH_HTPASSWD_PATH=/auth/htpasswd",
              "registry:2")
          .run();
    }

    if (pullBusyBox) {
      pullAndPushToLocal("busybox", "busybox");
    }
  }

  @Override
  protected void after() {
    try {
      logout();
      new Command("docker", "stop", containerName).run();
      new Command("docker", "rm", "-v", containerName).run();

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
      new Command("docker", "login", "localhost:" + port, "-u", username, "-p", password).run();
    }
  }

  private void logout() throws IOException, InterruptedException {
    if (username != null && password != null) {
      new Command("docker", "logout", "localhost:" + port).run();
    }
  }
}
