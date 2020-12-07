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

package com.google.cloud.tools.jib.cli.cli2;

import static com.google.common.truth.Truth.assertThat;

import com.google.cloud.tools.jib.Command;
import com.google.common.io.Resources;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import org.junit.Test;
import picocli.CommandLine;

public class JarCommandTest {

  @Test
  public void testErrorLogging_fileDoesNotExist() {
    CommandLine jibCli = new CommandLine(new JibCli());
    StringWriter stringWriter = new StringWriter();
    jibCli.setErr(new PrintWriter(stringWriter));

    Integer exitCode = jibCli.execute("jar", "--target", "docker://jib-cli-image", "unknown.jar");

    assertThat(exitCode).isEqualTo(1);
    assertThat(stringWriter.toString())
        .isEqualTo("[ERROR] The file path provided does not exist: unknown.jar\n");
  }

  @Test
  public void testErrorLogging_directoryGiven() throws URISyntaxException {
    CommandLine jibCli = new CommandLine(new JibCli());
    StringWriter stringWriter = new StringWriter();
    jibCli.setErr(new PrintWriter(stringWriter));

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
            .execute("jar", "--target", "docker://exploded-jar", jarPath.toString());
    String output = new Command("docker", "run", "--rm", "exploded-jar").run();
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
            .execute("jar", "--target", "docker://exploded-no-dep-jar", jarPath.toString());
    String output = new Command("docker", "run", "--rm", "exploded-no-dep-jar").run();
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
                "jar", "--target", "docker://packaged-jar", jarPath.toString(), "--mode=packaged");
    String output = new Command("docker", "run", "--rm", "packaged-jar").run();
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
                "--target",
                "docker://packaged-no-dep-jar",
                jarPath.toString(),
                "--mode=packaged");
    String output = new Command("docker", "run", "--rm", "packaged-no-dep-jar").run();
    try (JarFile jarFile = new JarFile(jarPath.toFile())) {
      String classPath =
          jarFile.getManifest().getMainAttributes().getValue(Attributes.Name.CLASS_PATH);

      assertThat(classPath).isNull();
      assertThat(exitCode).isEqualTo(0);
      assertThat(output).isEqualTo("Hello World");
    }
  }

  @Test
  public void testSpringbootLayeredJar_explodedMode_toDocker()
      throws IOException, InterruptedException, URISyntaxException {
    Path jarPath =
        Paths.get(Resources.getResource("jarTest/springboot/springboot_layered.jar").toURI());
    Integer exitCode =
        new CommandLine(new JibCli())
            .execute(
                "jar", "--target", "docker://exploded-springboot-layered-jar", jarPath.toString());
    String output = new Command("docker", "run", "--rm", "exploded-springboot-layered-jar").run();
    try (JarFile jarFile = new JarFile(jarPath.toFile())) {
      assertThat(jarFile.getEntry("BOOT-INF/layers.idx")).isNotNull();
      assertThat(exitCode).isEqualTo(0);
      assertThat(output).isEqualTo("Hello World");
    }
  }

  @Test
  public void testSpringbootNonLayeredJar_explodedMode_toDocker()
      throws IOException, InterruptedException, URISyntaxException {
    Path jarPath =
        Paths.get(Resources.getResource("jarTest/springboot/springboot_nonLayered.jar").toURI());
    Integer exitCode =
        new CommandLine(new JibCli())
            .execute("jar", "--target", "docker://exploded-springboot-jar", jarPath.toString());
    String output = new Command("docker", "run", "--rm", "exploded-springboot-jar").run();
    try (JarFile jarFile = new JarFile(jarPath.toFile())) {
      assertThat(jarFile.getEntry("BOOT-INF/layers.idx")).isNull();
      assertThat(exitCode).isEqualTo(0);
      assertThat(output).isEqualTo("Hello World");
    }
  }

  @Test
  public void testSpringbootJar_packagedMode_toDocker()
      throws IOException, InterruptedException, URISyntaxException {
    Path jarPath =
        Paths.get(Resources.getResource("jarTest/springboot/springboot_nonLayered.jar").toURI());
    Integer exitCode =
        new CommandLine(new JibCli())
            .execute("jar", "--target", "docker://packaged-springboot-jar", jarPath.toString());
    String output = new Command("docker", "run", "--rm", "packaged-springboot-jar").run();

    assertThat(exitCode).isEqualTo(0);
    assertThat(output).isEqualTo("Hello World");
  }

  @Test
  public void testJar_unknownMode() {
    CommandLine jibCli = new CommandLine(new JibCli());
    StringWriter stringWriter = new StringWriter();
    jibCli.setErr(new PrintWriter(stringWriter));

    Integer exitCode =
        jibCli.execute(
            "jar", "--target", "docker://jib-cli-image", "ignored.jar", "--mode=unknown");

    assertThat(exitCode).isEqualTo(2);
    assertThat(stringWriter.toString())
        .contains(
            "Invalid value for option '--mode': expected one of [exploded, packaged] (case-sensitive) but was 'unknown'");
  }
}
