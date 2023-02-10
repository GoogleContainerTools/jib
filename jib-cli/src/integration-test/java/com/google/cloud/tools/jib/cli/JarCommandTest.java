/*
 * Copyright 2020 Google LLC.
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
import com.google.common.io.Resources;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import org.junit.After;
import org.junit.ClassRule;
import org.junit.Test;
import picocli.CommandLine;

public class JarCommandTest {

  private static final Logger LOGGER = Logger.getLogger(JarCommandTest.class.getName());

  @ClassRule
  public static final TestProject springBootProject = new TestProject("jarTest/spring-boot");

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

    Integer exitCode = jibCli.execute("jar", "--target", "docker://jib-cli-image", "unknown.jar");

    assertThat(exitCode).isEqualTo(1);
    assertThat(stringWriter.toString())
        .isEqualTo("[ERROR] The file path provided does not exist: unknown.jar\n");
  }

  @Test
  public void testErrorLogging_directoryGiven() {
    StringWriter stringWriter = new StringWriter();
    CommandLine jibCli = new CommandLine(new JibCli()).setErr(new PrintWriter(stringWriter));

    Path jarFile = Paths.get("/");
    Integer exitCode =
        jibCli.execute("jar", "--target", "docker://jib-cli-image", jarFile.toString());

    assertThat(exitCode).isEqualTo(1);
    assertThat(stringWriter.toString())
        .isEqualTo(
            "[ERROR] The file path provided is for a directory. Please provide a path to a JAR: "
                + jarFile.toString()
                + "\n");
  }

  @Test
  public void testStandardJar_explodedMode_toDocker()
      throws IOException, InterruptedException, URISyntaxException {
    Path jarPath = Paths.get(Resources.getResource("jarTest/standard/jarWithCp.jar").toURI());
    Integer exitCode =
        new CommandLine(new JibCli())
            .execute(
                "jar",
                "--from",
                "eclipse-temurin:8-jdk-focal",
                "--target",
                "docker://exploded-jar",
                jarPath.toString());
    String output =
        new Command("docker", "run", "--rm", "exploded-jar", "--privileged", "--network=host")
            .run();

    try (JarFile jarFile = new JarFile(jarPath.toFile())) {
      String classPath =
          jarFile.getManifest().getMainAttributes().getValue(Attributes.Name.CLASS_PATH);

      assertThat(classPath).isEqualTo("dependency1.jar directory/dependency2.jar");
      assertThat(exitCode).isEqualTo(0);
      assertThat(output).isEqualTo("Hello World");
    }
  }

  @Test
  public void testNoDependencyStandardJar_explodedMode_toDocker()
      throws IOException, InterruptedException, URISyntaxException {
    Path jarPath = Paths.get(Resources.getResource("jarTest/standard/noDependencyJar.jar").toURI());
    Integer exitCode =
        new CommandLine(new JibCli())
            .execute(
                "jar",
                "--from",
                "eclipse-temurin:8-jdk-focal",
                "--target",
                "docker://exploded-no-dep-jar",
                jarPath.toString());
    String output =
        new Command(
                "docker", "run", "--rm", "exploded-no-dep-jar", "--privileged", "--network=host")
            .run();
    try (JarFile jarFile = new JarFile(jarPath.toFile())) {
      String classPath =
          jarFile.getManifest().getMainAttributes().getValue(Attributes.Name.CLASS_PATH);

      assertThat(classPath).isNull();
      assertThat(exitCode).isEqualTo(0);
      assertThat(output).isEqualTo("Hello World");
    }
  }

  @Test
  public void testStandardJar_packagedMode_toDocker()
      throws IOException, InterruptedException, URISyntaxException {
    Path jarPath = Paths.get(Resources.getResource("jarTest/standard/jarWithCp.jar").toURI());
    Integer exitCode =
        new CommandLine(new JibCli())
            .execute(
                "jar",
                "--from",
                "eclipse-temurin:8-jdk-focal",
                "--target",
                "docker://packaged-jar",
                jarPath.toString(),
                "--mode=packaged");
    String output =
        new Command("docker", "run", "--rm", "packaged-jar", "--privileged", "--network=host")
            .run();

    try (JarFile jarFile = new JarFile(jarPath.toFile())) {
      String classPath =
          jarFile.getManifest().getMainAttributes().getValue(Attributes.Name.CLASS_PATH);

      assertThat(classPath).isEqualTo("dependency1.jar directory/dependency2.jar");
      assertThat(exitCode).isEqualTo(0);
      assertThat(output).isEqualTo("Hello World");
    }
  }

  @Test
  public void testNoDependencyStandardJar_packagedMode_toDocker()
      throws IOException, InterruptedException, URISyntaxException {
    Path jarPath = Paths.get(Resources.getResource("jarTest/standard/noDependencyJar.jar").toURI());
    Integer exitCode =
        new CommandLine(new JibCli())
            .execute(
                "jar",
                "--from",
                "eclipse-temurin:8-jdk-focal",
                "--target",
                "docker://packaged-no-dep-jar",
                jarPath.toString(),
                "--mode=packaged");
    String output =
        new Command(
                "docker", "run", "--rm", "packaged-no-dep-jar", "--privileged", "--network=host")
            .run();
    try (JarFile jarFile = new JarFile(jarPath.toFile())) {
      String classPath =
          jarFile.getManifest().getMainAttributes().getValue(Attributes.Name.CLASS_PATH);

      assertThat(classPath).isNull();
      assertThat(exitCode).isEqualTo(0);
      assertThat(output).isEqualTo("Hello World");
    }
  }

  @Test
  public void testSpringBootLayeredJar_explodedMode() throws IOException, InterruptedException {
    springBootProject.build("-c", "settings-layered.gradle", "clean", "bootJar");
    Path jarParentPath = springBootProject.getProjectRoot().resolve("build").resolve("libs");
    Path jarPath = jarParentPath.resolve("spring-boot.jar");

    Integer exitCode =
        new CommandLine(new JibCli())
            .execute(
                "jar",
                "--from",
                "eclipse-temurin:8-jdk-focal",
                "--target",
                "docker://spring-boot-jar-layered",
                jarPath.toString());
    assertThat(exitCode).isEqualTo(0);

    runJarInDocker("spring-boot-jar-layered");
    try (JarFile jarFile = new JarFile(jarPath.toFile())) {
      assertThat(jarFile.getEntry("BOOT-INF/layers.idx")).isNotNull();
      assertThat(getContent(new URL("http://" + getDockerHost() + ":8080")))
          .isEqualTo("Hello world");
    }
  }

  @Test
  public void testSpringBootNonLayeredJar_explodedMode() throws IOException, InterruptedException {
    springBootProject.build("clean", "bootJar");
    Path jarParentPath = springBootProject.getProjectRoot().resolve("build").resolve("libs");
    Path jarPath = jarParentPath.resolve("spring-boot.jar");

    Integer exitCode =
        new CommandLine(new JibCli())
            .execute(
                "jar",
                "--from",
                "eclipse-temurin:8-jdk-focal",
                "--target",
                "docker://spring-boot-jar",
                jarPath.toString(),
                "--expose=8080");
    assertThat(exitCode).isEqualTo(0);

    runJarInDocker("spring-boot-jar");
    try (JarFile jarFile = new JarFile(jarPath.toFile())) {
      assertThat(jarFile.getEntry("BOOT-INF/layers.idx")).isNull();
      assertThat(getContent(new URL("http://" + getDockerHost() + ":8080")))
          .isEqualTo("Hello world");
    }
  }

  @Test
  public void testSpringBootJar_packagedMode() throws IOException, InterruptedException {
    springBootProject.build("clean", "bootJar");
    Path jarParentPath = springBootProject.getProjectRoot().resolve("build").resolve("libs");
    Path jarPath = jarParentPath.resolve("spring-boot.jar");
    Integer exitCode =
        new CommandLine(new JibCli())
            .execute(
                "jar",
                "--from",
                "eclipse-temurin:8-jdk-focal",
                "--target",
                "docker://packaged-spring-boot",
                jarPath.toString(),
                "--expose=8080",
                "--mode=packaged");
    assertThat(exitCode).isEqualTo(0);

    runJarInDocker("packaged-spring-boot");
    assertThat(getContent(new URL("http://" + getDockerHost() + ":8080"))).isEqualTo("Hello world");
  }

  @Test
  public void testJar_baseImageSpecified()
      throws IOException, URISyntaxException, InterruptedException {
    Path jarPath = Paths.get(Resources.getResource("jarTest/standard/noDependencyJar.jar").toURI());
    Integer exitCode =
        new CommandLine(new JibCli())
            .execute(
                "jar",
                "--target=docker://cli-gcr-base",
                "--from=gcr.io/google-appengine/openjdk:8",
                jarPath.toString(),
                "--expose=8080");
    assertThat(exitCode).isEqualTo(0);
    String output = new Command("docker", "run", "--rm", "cli-gcr-base").run();
    assertThat(output).isEqualTo("Hello World");
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
        ignored.printStackTrace();
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
    LOGGER.info("Container IP: " + containerIp);
    return containerIp;
  }

  private String runJarInDocker(String name) throws IOException, InterruptedException {
    LOGGER.info("Jar name: " + name);
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

    // Log port info
    String port = new Command("docker", "port", containerName).run();
    LOGGER.info("Port: " + port);

    //    // Log container info
    //    String dockerInspectOutput = new Command("docker", "inspect", containerName).run();
    //    LOGGER.info(dockerInspectOutput);

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
