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
  public void testJar_explodedMode_toDocker()
      throws IOException, InterruptedException, URISyntaxException {
    Path jarPath = Paths.get(Resources.getResource("jarTest/jarWithCp.jar").toURI());
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
  public void testNoDependencyJar_explodedMode_toDocker()
      throws IOException, InterruptedException, URISyntaxException {
    Path jarPath = Paths.get(Resources.getResource("noDependencyJar.jar").toURI());
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
  public void testJar_packagedMode_toDocker()
      throws IOException, InterruptedException, URISyntaxException {
    Path jarPath = Paths.get(Resources.getResource("jarTest/jarWithCp.jar").toURI());
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
  public void testNoDependencyJar_packagedMode_toDocker()
      throws IOException, InterruptedException, URISyntaxException {
    Path jarPath = Paths.get(Resources.getResource("noDependencyJar.jar").toURI());
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
  public void testJar_unknownMode() {
    StringWriter stringWriter = new StringWriter();
    CommandLine jibCli = new CommandLine(new JibCli()).setErr(new PrintWriter(stringWriter));

    Integer exitCode =
        jibCli.execute(
            "jar", "--target", "docker://jib-cli-image", "ignored.jar", "--mode=unknown");

    assertThat(exitCode).isEqualTo(2);
    assertThat(stringWriter.toString())
        .contains(
            "Invalid value for option '--mode': expected one of [exploded, packaged] (case-sensitive) but was 'unknown'");
  }
}
