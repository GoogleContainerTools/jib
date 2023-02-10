/*
 * Copyright 2021 Google LLC.
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

package com.google.cloud.tools.jib.cli;

import static com.google.common.truth.Truth.assertThat;

import com.google.cloud.tools.jib.Command;
import com.google.cloud.tools.jib.blob.Blobs;
import com.google.common.collect.Lists;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import org.junit.After;
import org.junit.ClassRule;
import org.junit.Test;
import picocli.CommandLine;

public class WarCommandTest {

  private static final Logger LOGGER = Logger.getLogger(WarCommandTest.class.getName());

  @ClassRule public static final TestProject servletProject = new TestProject("warTest");
  private final String dockerHost =
      System.getenv("DOCKER_IP") != null ? System.getenv("DOCKER_IP") : "localhost";

  @Nullable private String containerName;

  @After
  public void tearDown() throws IOException, InterruptedException {
    if (containerName != null) {
      new Command("docker", "stop", containerName).run();
    }
  }

  @Test
  public void testErrorLogging_fileDoesNotExist() {
    StringWriter stringWriter = new StringWriter();
    CommandLine jibCli = new CommandLine(new JibCli()).setErr(new PrintWriter(stringWriter));

    Integer exitCode = jibCli.execute("war", "--target", "docker://jib-cli-image", "unknown.war");

    assertThat(exitCode).isEqualTo(1);
    assertThat(stringWriter.toString())
        .isEqualTo("[ERROR] The file path provided does not exist: unknown.war\n");
  }

  @Test
  public void testErrorLogging_directoryGiven() {
    StringWriter stringWriter = new StringWriter();
    CommandLine jibCli = new CommandLine(new JibCli()).setErr(new PrintWriter(stringWriter));

    Path warFile = Paths.get("/");
    Integer exitCode =
        jibCli.execute("war", "--target", "docker://jib-cli-image", warFile.toString());

    assertThat(exitCode).isEqualTo(1);
    assertThat(stringWriter.toString())
        .isEqualTo(
            "[ERROR] The file path provided is for a directory. Please provide a path to a WAR: "
                + warFile.toString()
                + "\n");
  }

  @Test
  public void testWar_jetty() throws IOException, InterruptedException {
    servletProject.build("clean", "war");
    Path warParentPath = servletProject.getProjectRoot().resolve("build").resolve("libs");
    Path warPath = warParentPath.resolve("standard-war.war");
    Integer exitCode =
        new CommandLine(new JibCli())
            .execute("war", "--target", "docker://exploded-war", warPath.toString());
    assertThat(exitCode).isEqualTo(0);

    runWarInDocker("exploded-war");
    assertThat(getContent(new URL("http://" + getDockerHost() + ":8080/hello")))
        .isEqualTo("Hello world");
  }

  @Test
  public void testWar_customJettySpecified() throws IOException, InterruptedException {
    servletProject.build("clean", "war");
    Path warParentPath = servletProject.getProjectRoot().resolve("build").resolve("libs");
    Path warPath = warParentPath.resolve("standard-war.war");
    Integer exitCode =
        new CommandLine(new JibCli())
            .execute(
                "war",
                "--target",
                "docker://exploded-war-custom-jetty",
                "--from=jetty:11.0-jre11-slim-openjdk",
                warPath.toString());
    assertThat(exitCode).isEqualTo(0);

    runWarInDocker("exploded-war-custom-jetty");
    assertThat(getContent(new URL("http://" + getDockerHost() + ":8080/hello")))
        .isEqualTo("Hello world");
  }

  @Test
  public void testWar_tomcat() throws IOException, InterruptedException {
    servletProject.build("clean", "war");
    Path warParentPath = servletProject.getProjectRoot().resolve("build").resolve("libs");
    Path warPath = warParentPath.resolve("standard-war.war");
    Integer exitCode =
        new CommandLine(new JibCli())
            .execute(
                "war",
                "--target",
                "docker://exploded-war-tomcat",
                "--from=tomcat:10-jre8-openjdk-slim",
                "--app-root",
                "/usr/local/tomcat/webapps/ROOT",
                warPath.toString());
    assertThat(exitCode).isEqualTo(0);

    runWarInDocker("exploded-war-tomcat");
    assertThat(getContent(new URL("http://" + getDockerHost() + ":8080/hello")))
        .isEqualTo("Hello world");
  }

  @Nullable
  private static String getContent(URL url) throws InterruptedException {
    LOGGER.info("URL: " + url);
    for (int i = 0; i < 40; i++) {
      Thread.sleep(500);
      try {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
          try (InputStream in = connection.getInputStream()) {
            return Blobs.writeToString(Blobs.from(in));
          }
        }
      } catch (IOException ignored) {
        // ignored
        LOGGER.info("Exception: " + ignored);
      }
    }
    return null;
  }

  private String getDockerHost() {
    if (System.getenv("KOKORO_JOB_CLUSTER") != null
        && System.getenv("KOKORO_JOB_CLUSTER").equals("GCP_UBUNTU_DOCKER")) {
      return getRegistryContainerIp(containerName);
    }
    return dockerHost;
  }

  /** Gets local registry container IP. */
  private String getRegistryContainerIp(String containerName) {
    String containerIp;

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
      containerIp = result.replaceAll("['\n]", "");
    } catch (InterruptedException | IOException ex) {
      throw new RuntimeException("Could get local registry IP for: " + containerName, ex);
    }
    return containerIp;
  }

  private String runWarInDocker(String name) throws IOException, InterruptedException {
    LOGGER.info("War name: " + name);
    String output =
        new Command(
                "docker",
                "run",
                "--rm",
                "--detach",
                "-p",
                "8080:8080",
                name,
                "--privileged",
                "--network=host")
            .run();
    containerName = output.trim();
    LOGGER.info("Container name: " + containerName);
    //    if (System.getenv("KOKORO_JOB_CLUSTER") != null
    //        && System.getenv("KOKORO_JOB_CLUSTER").equals("GCP_UBUNTU_DOCKER")) {
    //      String containerIp = getAndMapRegistryContainerIp(containerName);
    //      LOGGER.info("Mapped registry container IP to localhost: " + containerIp);
    //    }

    String port = new Command("docker", "port", containerName).run();
    LOGGER.info("Port: " + port);
    return containerName;
  }

  //  /** Gets local registry container IP and associates it to localhost. */
  //  private String getAndMapRegistryContainerIp(String containerName) {
  //    String containerIp;
  //
  //    // Gets local registry container IP
  //    List<String> dockerTokens =
  //        Lists.newArrayList(
  //            "docker",
  //            "inspect",
  //            "-f",
  //            "'{{range.NetworkSettings.Networks}}{{.IPAddress}}{{end}}'",
  //            containerName);
  //    try {
  //      String result = new Command(dockerTokens).run();
  //      // Remove single quotes and LF from result (e.g. '127.0.0.1'\n)
  //      containerIp = result.replaceAll("['\n]", "");
  //    } catch (InterruptedException | IOException ex) {
  //      throw new RuntimeException("Could get local registry IP for: " + containerName, ex);
  //    }
  //
  //    // Associate container IP with localhost
  //    try {
  //      String addHost =
  //          new Command("bash", "-c", "echo \"" + containerIp + " localhost\" >>
  // /etc/hosts").run();
  //    } catch (InterruptedException | IOException ex) {
  //      throw new RuntimeException("Could not associate container IP to localhost: " +
  // containerIp);
  //    }
  //
  //    return containerIp;
  //  }
}
