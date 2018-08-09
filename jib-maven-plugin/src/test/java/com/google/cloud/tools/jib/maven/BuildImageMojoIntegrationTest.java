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

package com.google.cloud.tools.jib.maven;

import com.google.cloud.tools.jib.Command;
import com.google.cloud.tools.jib.IntegrationTestingConfiguration;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;

/** Integration tests for {@link BuildImageMojo}. */
public class BuildImageMojoIntegrationTest {

  @ClassRule public static final TestPlugin testPlugin = new TestPlugin();

  @ClassRule
  public static final TestProject simpleTestProject = new TestProject(testPlugin, "simple");

  @ClassRule
  public static final TestProject complexTestProject =
      new TestProject(testPlugin, "simple", "pom-complex.xml");

  @ClassRule
  public static final TestProject emptyTestProject = new TestProject(testPlugin, "empty");

  @ClassRule
  public static final TestProject defaultTargetTestProject =
      new TestProject(testPlugin, "default-target");

  /**
   * Builds and runs jib:build on a project at {@code projectRoot} pushing to {@code
   * imageReference}.
   */
  private static String buildAndRun(Path projectRoot, String imageReference, boolean runTwice)
      throws VerificationException, IOException, InterruptedException {
    Verifier verifier = new Verifier(projectRoot.toString());
    verifier.setSystemProperty("_TARGET_IMAGE", imageReference);
    verifier.setAutoclean(false);
    verifier.addCliOption("-X");
    verifier.executeGoals(Arrays.asList("clean", "compile"));

    // Builds twice, and checks if the second build took less time.
    verifier.executeGoal("jib:" + BuildImageMojo.GOAL_NAME);
    float timeOne = getBuildTimeFromVerifierLog(verifier);

    if (runTwice) {
      verifier.resetStreams();
      verifier.executeGoal("jib:" + BuildImageMojo.GOAL_NAME);
      float timeTwo = getBuildTimeFromVerifierLog(verifier);

      Assert.assertTrue(
          "First build time ("
              + timeOne
              + ") is not greater than second build time ("
              + timeTwo
              + ")",
          timeOne > timeTwo);
    }

    verifier.verifyErrorFreeLog();

    new Command("docker", "pull", imageReference).run();
    Assert.assertThat(
        new Command("docker", "inspect", imageReference).run(),
        CoreMatchers.containsString(
            "            \"ExposedPorts\": {\n"
                + "                \"1000/tcp\": {},\n"
                + "                \"2000/udp\": {},\n"
                + "                \"2001/udp\": {},\n"
                + "                \"2002/udp\": {},\n"
                + "                \"2003/udp\": {}"));
    return new Command("docker", "run", imageReference).run();
  }

  private static float getBuildTimeFromVerifierLog(Verifier verifier) throws IOException {
    Pattern pattern = Pattern.compile("Building and pushing image : (?<time>.*) ms");

    for (String line :
        Files.readAllLines(Paths.get(verifier.getBasedir(), verifier.getLogFileName()))) {
      Matcher matcher = pattern.matcher(line);
      if (matcher.find()) {
        return Float.parseFloat(matcher.group("time"));
      }
    }

    Assert.fail("Could not find build execution time in logs");
    // Should not reach here.
    return -1;
  }

  @Test
  public void testExecute_simple() throws VerificationException, IOException, InterruptedException {
    String targetImage =
        "gcr.io/"
            + IntegrationTestingConfiguration.getGCPProject()
            + "/simpleimage:maven"
            + System.nanoTime();

    // Test empty output error
    try {
      Verifier verifier = new Verifier(simpleTestProject.getProjectRoot().toString());
      verifier.setSystemProperty("_TARGET_IMAGE", targetImage);
      verifier.setAutoclean(false);
      verifier.executeGoals(Arrays.asList("clean", "jib:" + BuildImageMojo.GOAL_NAME));
      Assert.fail();

    } catch (VerificationException ex) {
      Assert.assertThat(
          ex.getMessage(),
          CoreMatchers.containsString(
              "Obtaining project build output files failed; make sure you have compiled your "
                  + "project before trying to build the image. (Did you accidentally run \"mvn "
                  + "clean jib:build\" instead of \"mvn clean compile jib:build\"?)"));
    }

    Instant before = Instant.now();

    // The target registry these tests push to would already have all the layers cached from before,
    // causing this test to fail sometimes with the second build being a bit slower than the first
    // build. This file change makes sure that a new layer is always pushed the first time to solve
    // this issue.
    Files.write(
        simpleTestProject
            .getProjectRoot()
            .resolve("src")
            .resolve("main")
            .resolve("resources")
            .resolve("world"),
        before.toString().getBytes(StandardCharsets.UTF_8));

    Assert.assertEquals(
        "Hello, " + before + ". An argument.\nfoo\ncat\n",
        buildAndRun(simpleTestProject.getProjectRoot(), targetImage, true));

    Instant buildTime =
        Instant.parse(
            new Command("docker", "inspect", "-f", "{{.Created}}", targetImage).run().trim());
    Assert.assertTrue(buildTime.isAfter(before) || buildTime.equals(before));
  }

  @Test
  public void testExecute_empty() throws InterruptedException, IOException, VerificationException {
    String targetImage =
        "gcr.io/"
            + IntegrationTestingConfiguration.getGCPProject()
            + "/emptyimage:maven"
            + System.nanoTime();

    Assert.assertEquals("", buildAndRun(emptyTestProject.getProjectRoot(), targetImage, false));
    Assert.assertEquals(
        "1970-01-01T00:00:00Z",
        new Command("docker", "inspect", "-f", "{{.Created}}", targetImage).run().trim());
  }

  @Test
  public void testExecute_defaultTarget() {
    // Test error when 'to' is missing
    try {
      Verifier verifier = new Verifier(defaultTargetTestProject.getProjectRoot().toString());
      verifier.setAutoclean(false);
      verifier.executeGoals(Arrays.asList("clean", "jib:" + BuildImageMojo.GOAL_NAME));
      Assert.fail();

    } catch (VerificationException ex) {
      Assert.assertThat(
          ex.getMessage(),
          CoreMatchers.containsString(
              "Missing target image parameter, perhaps you should add a <to><image> configuration "
                  + "parameter to your pom.xml or set the parameter via the commandline (e.g. 'mvn "
                  + "compile jib:build -Dimage=<your image name>')."));
    }
  }

  @Test
  public void testExecute_complex()
      throws IOException, InterruptedException, VerificationException {
    String targetImage = "localhost:5000/compleximage:maven" + System.nanoTime();

    // Runs the Docker registry.
    // TODO: Refactor into LocalRegistry
    String containerName = "registry-" + UUID.randomUUID();
    new Command(
            "docker",
            "run",
            "-d",
            "-p",
            "5000:5000",
            "--restart=always",
            "--name",
            containerName,
            "-v",
            // Volume mount used for storing credentials
            complexTestProject.getProjectRoot().resolve("auth") + ":/auth",
            "-e",
            "REGISTRY_AUTH=htpasswd",
            "-e",
            "REGISTRY_AUTH_HTPASSWD_REALM=Registry Realm",
            "-e",
            "REGISTRY_AUTH_HTPASSWD_PATH=/auth/htpasswd",
            "registry:2")
        .run();

    // Login to push base image to local registry, then logout so we can test Jib's auth
    try {
      new Command("docker", "login", "localhost:5000", "-u", "testuser", "-p", "testpassword")
          .run();
      new Command("docker", "pull", "gcr.io/distroless/java:latest").run();
      new Command(
              "docker", "tag", "gcr.io/distroless/java:latest", "localhost:5000/distroless/java")
          .run();
      new Command("docker", "push", "localhost:5000/distroless/java").run();
      new Command("docker", "logout", "localhost:5000").run();

      // Run jib:build
      Instant before = Instant.now();
      Verifier verifier = new Verifier(complexTestProject.getProjectRoot().toString());
      verifier.setSystemProperty("_TARGET_IMAGE", targetImage);
      verifier.setAutoclean(false);
      verifier.addCliOption("-X");
      verifier.addCliOption("-DsendCredentialsOverHttp=true");
      verifier.addCliOption("--file=pom-complex.xml");
      verifier.executeGoals(Arrays.asList("clean", "compile", "jib:build"));
      verifier.verifyErrorFreeLog();

      // Verify output
      new Command("docker", "login", "localhost:5000", "-u", "testuser", "-p", "testpassword")
          .run();
      new Command("docker", "pull", targetImage).run();
      Assert.assertThat(
          new Command("docker", "inspect", targetImage).run(),
          CoreMatchers.containsString(
              "            \"ExposedPorts\": {\n"
                  + "                \"1000/tcp\": {},\n"
                  + "                \"2000/udp\": {},\n"
                  + "                \"2001/udp\": {},\n"
                  + "                \"2002/udp\": {},\n"
                  + "                \"2003/udp\": {}"));
      Assert.assertEquals(
          "Hello, world. An argument.\nfoo\ncat\n-Xms512m\n-Xdebug\n",
          new Command("docker", "run", targetImage).run());
      Instant buildTime =
          Instant.parse(
              new Command("docker", "inspect", "-f", "{{.Created}}", targetImage).run().trim());
      Assert.assertTrue(buildTime.isAfter(before) || buildTime.equals(before));
      new Command("docker", "logout", "localhost:5000").run();

    } finally {
      // Stops the local registry.
      new Command("docker", "stop", containerName).run();
      new Command("docker", "rm", "-v", containerName).run();
    }
  }
}
