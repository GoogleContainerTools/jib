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
import com.google.cloud.tools.jib.api.HttpRequestTester;
import com.google.common.base.Preconditions;
import com.google.common.io.Resources;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import javax.annotation.Nullable;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import picocli.CommandLine;

public class JarCommandTest {

  @ClassRule
  public static final TestProject springBootProject = new TestProject("jarTest/spring-boot");

  @Nullable private String containerName;

  @BeforeClass
  public static void createJars() throws IOException, URISyntaxException {
    createJarFile(
        "jarWithCp.jar", "HelloWorld", "dependency1.jar directory/dependency2.jar", "HelloWorld");
    createJarFile("noDependencyJar.jar", "HelloWorld", null, "HelloWorld");
    createJarFile("dependency1.jar", "dep/A", null, null);
    createJarFile("directory/dependency2.jar", "dep2/B", null, null);
  }

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

    String output =
        new Command(
                "docker",
                "run",
                "--rm",
                "--detach",
                "-p8080:8080",
                "spring-boot-jar-layered",
                "--privileged",
                "--network=host")
            .run();
    containerName = output.trim();
    try (JarFile jarFile = new JarFile(jarPath.toFile())) {

      assertThat(jarFile.getEntry("BOOT-INF/layers.idx")).isNotNull();
      HttpRequestTester.verifyBody(
          "Hello world",
          new URL("http://" + HttpRequestTester.fetchDockerHostForHttpRequest() + ":8080"));
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
                jarPath.toString());
    assertThat(exitCode).isEqualTo(0);

    String output =
        new Command(
                "docker",
                "run",
                "--rm",
                "--detach",
                "-p8080:8080",
                "spring-boot-jar",
                "--privileged",
                "--network=host")
            .run();
    containerName = output.trim();
    try (JarFile jarFile = new JarFile(jarPath.toFile())) {

      assertThat(jarFile.getEntry("BOOT-INF/layers.idx")).isNull();
      HttpRequestTester.verifyBody(
          "Hello world",
          new URL("http://" + HttpRequestTester.fetchDockerHostForHttpRequest() + ":8080"));
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
                "--mode=packaged");
    assertThat(exitCode).isEqualTo(0);

    String output =
        new Command(
                "docker",
                "run",
                "--rm",
                "--detach",
                "-p8080:8080",
                "packaged-spring-boot",
                "--privileged",
                "--network=host")
            .run();
    containerName = output.trim();

    HttpRequestTester.verifyBody(
        "Hello world",
        new URL("http://" + HttpRequestTester.fetchDockerHostForHttpRequest() + ":8080"));
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
                jarPath.toString());
    assertThat(exitCode).isEqualTo(0);
    String output = new Command("docker", "run", "--rm", "cli-gcr-base").run();
    assertThat(output).isEqualTo("Hello World");
  }

  public static void createJarFile(
      String name, String className, String classPath, String mainClass)
      throws IOException, URISyntaxException {
    Path javaFilePath =
        Paths.get(Resources.getResource("jarTest/standard/" + className + ".java").toURI());
    Path workingDir = Paths.get(Resources.getResource("jarTest/standard/").toURI());

    // compile the java file
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    Preconditions.checkNotNull(compiler);
    StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null);
    Iterable<? extends JavaFileObject> compilationUnits =
        fileManager.getJavaFileObjectsFromFiles(Collections.singletonList(javaFilePath.toFile()));
    Iterable<String> options = Arrays.asList("-source", "1.8", "-target", "1.8");
    JavaCompiler.CompilationTask task =
        compiler.getTask(null, fileManager, null, options, null, compilationUnits);
    boolean success = task.call();
    assertThat(success).isTrue();

    // Create a manifest file
    Manifest manifest = new Manifest();
    Attributes attributes = new Attributes();
    attributes.putValue("Manifest-Version", "1.0");
    if (classPath != null) {
      attributes.putValue("Class-Path", classPath);
    }
    if (mainClass != null) {
      attributes.putValue("Main-Class", mainClass);
    }
    manifest.getMainAttributes().putAll(attributes);

    // Create JAR
    File jarFile = workingDir.resolve(name).toFile();
    jarFile.getParentFile().mkdirs();
    try (FileOutputStream fileOutputStream = new FileOutputStream(jarFile);
        JarOutputStream jarOutputStream = new JarOutputStream(fileOutputStream, manifest)) {
      ZipEntry zipEntry = new ZipEntry(className + ".class");
      jarOutputStream.putNextEntry(zipEntry);
      jarOutputStream.write(Files.readAllBytes(workingDir.resolve(className + ".class")));
      jarOutputStream.closeEntry();
    }
  }
}
