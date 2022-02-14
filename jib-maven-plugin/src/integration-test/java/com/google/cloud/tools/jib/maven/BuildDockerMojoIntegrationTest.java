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

package com.google.cloud.tools.jib.maven;

import static com.google.common.truth.Truth.assertThat;

import com.google.cloud.tools.jib.Command;
import java.io.IOException;
import java.security.DigestException;
import java.util.Arrays;
import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.ClassRule;
import org.junit.Test;

/** Integration tests for {@link BuildDockerMojo}. */
public class BuildDockerMojoIntegrationTest {

  @ClassRule public static final TestProject simpleTestProject = new TestProject("simple");

  @ClassRule public static final TestProject emptyTestProject = new TestProject("empty");

  @ClassRule
  public static final TestProject defaultTargetTestProject = new TestProject("default-target");

  private static void buildToDockerDaemon(TestProject project, String imageReference, String pomXml)
      throws VerificationException, DigestException, IOException {
    Verifier verifier = new Verifier(project.getProjectRoot().toString());
    verifier.setSystemProperty("jib.useOnlyProjectCache", "true");
    verifier.setSystemProperty("_TARGET_IMAGE", imageReference);
    verifier.setAutoclean(false);
    verifier.addCliOption("--file=" + pomXml);
    verifier.executeGoal("package");

    verifier.executeGoal("jib:dockerBuild");
    verifier.verifyErrorFreeLog();

    BuildImageMojoIntegrationTest.readDigestFile(
        project.getProjectRoot().resolve("target/jib-image.digest"));
  }

  /**
   * Builds and runs jib:buildDocker on a project at {@code projectRoot} pushing to {@code
   * imageReference}.
   */
  private static String buildToDockerDaemonAndRun(TestProject project, String imageReference)
      throws VerificationException, IOException, InterruptedException, DigestException {
    buildToDockerDaemon(project, imageReference, "pom.xml");

    String dockerInspect = new Command("docker", "inspect", imageReference).run();
    MatcherAssert.assertThat(
        dockerInspect,
        CoreMatchers.containsString(
            "            \"Volumes\": {\n"
                + "                \"/var/log\": {},\n"
                + "                \"/var/log2\": {}\n"
                + "            },"));
    MatcherAssert.assertThat(
        dockerInspect,
        CoreMatchers.containsString(
            "            \"ExposedPorts\": {\n"
                + "                \"1000/tcp\": {},\n"
                + "                \"2000/udp\": {},\n"
                + "                \"2001/udp\": {},\n"
                + "                \"2002/udp\": {},\n"
                + "                \"2003/udp\": {}"));
    MatcherAssert.assertThat(
        dockerInspect,
        CoreMatchers.containsString(
            "            \"Labels\": {\n"
                + "                \"key1\": \"value1\",\n"
                + "                \"key2\": \"value2\"\n"
                + "            }"));

    return new Command("docker", "run", "--rm", imageReference).run();
  }

  @Test
  public void testExecute_simple()
      throws VerificationException, IOException, InterruptedException, DigestException {
    String targetImage = "simpleimage:maven" + System.nanoTime();

    Assert.assertEquals(
        "Hello, world. An argument.\n1970-01-01T00:00:01Z\nrw-r--r--\nrw-r--r--\nfoo\ncat\n"
            + "1970-01-01T00:00:01Z\n1970-01-01T00:00:01Z\n",
        buildToDockerDaemonAndRun(simpleTestProject, targetImage));
    Assert.assertEquals(
        "1970-01-01T00:00:00Z",
        new Command("docker", "inspect", "-f", "{{.Created}}", targetImage).run().trim());
  }

  @Test
  public void testExecute_simple_extraDirectoriesFiltering()
      throws DigestException, IOException, InterruptedException, VerificationException {
    String targetImage = "simpleimage:maven" + System.nanoTime();
    buildToDockerDaemon(simpleTestProject, targetImage, "pom-extra-dirs-filtering.xml");
    String output =
        new Command("docker", "run", "--rm", "--entrypoint=ls", targetImage, "-1R", "/extras")
            .run();

    //   /extras/cat.txt
    //   /extras/foo
    //   /extras/sub/
    //   /extras/sub/a.json
    assertThat(output).isEqualTo("/extras:\ncat.txt\nfoo\nsub\n\n/extras/sub:\na.json\n");
  }

  @Test
  public void testExecute_dockerClient()
      throws VerificationException, IOException, InterruptedException {
    Assume.assumeFalse(System.getProperty("os.name").startsWith("Windows"));
    new Command(
            "chmod", "+x", simpleTestProject.getProjectRoot().resolve("mock-docker.sh").toString())
        .run();

    String targetImage = "simpleimage:maven" + System.nanoTime();
    Verifier verifier = new Verifier(simpleTestProject.getProjectRoot().toString());
    verifier.setSystemProperty("jib.useOnlyProjectCache", "true");
    verifier.setSystemProperty("_TARGET_IMAGE", targetImage);
    verifier.setAutoclean(false);
    verifier.addCliOption("--file=pom-dockerclient.xml");
    verifier.addCliOption("--debug");
    verifier.executeGoal("package");

    verifier.executeGoal("jib:dockerBuild");
    verifier.verifyTextInLog("Docker load called. value1 value2");
    verifier.verifyErrorFreeLog();
  }

  @Test
  public void testExecute_empty()
      throws InterruptedException, IOException, VerificationException, DigestException {
    String targetImage = "emptyimage:maven" + System.nanoTime();

    Assert.assertEquals("", buildToDockerDaemonAndRun(emptyTestProject, targetImage));
    Assert.assertEquals(
        "1970-01-01T00:00:00Z",
        new Command("docker", "inspect", "-f", "{{.Created}}", targetImage).run().trim());
  }

  @Test
  public void testExecute_defaultTarget()
      throws VerificationException, IOException, InterruptedException, DigestException {
    Assert.assertEquals(
        "Hello, world. An argument.\n",
        buildToDockerDaemonAndRun(
            defaultTargetTestProject, "default-target-name:default-target-version"));
  }

  @Test
  public void testExecute_jibSkip() throws VerificationException, IOException {
    SkippedGoalVerifier.verifyJibSkip(emptyTestProject, BuildDockerMojo.GOAL_NAME);
  }

  @Test
  public void testExecute_jibContainerizeSkips() throws VerificationException, IOException {
    SkippedGoalVerifier.verifyJibContainerizeSkips(emptyTestProject, BuildDockerMojo.GOAL_NAME);
  }

  @Test
  public void testExecute_userNumeric()
      throws VerificationException, IOException, InterruptedException, DigestException {
    String targetImage = "emptyimage:maven" + System.nanoTime();
    buildToDockerDaemon(emptyTestProject, targetImage, "pom.xml");
    Assert.assertEquals(
        "12345:54321",
        new Command("docker", "inspect", "-f", "{{.Config.User}}", targetImage).run().trim());
  }

  @Test
  public void testExecute_userNames()
      throws VerificationException, IOException, InterruptedException, DigestException {
    String targetImage = "brokenuserimage:maven" + System.nanoTime();
    buildToDockerDaemon(emptyTestProject, targetImage, "pom-broken-user.xml");
    Assert.assertEquals(
        "myuser:mygroup",
        new Command("docker", "inspect", "-f", "{{.Config.User}}", targetImage).run().trim());
  }

  @Test
  public void testExecute_noToImageAndInvalidProjectName()
      throws DigestException, VerificationException, IOException, InterruptedException {
    buildToDockerDaemon(simpleTestProject, "image reference ignored", "pom-no-to-image.xml");
    Assert.assertEquals(
        "Hello, world. \n1970-01-01T00:00:01Z\n",
        new Command("docker", "run", "--rm", "my-artifact-id:1").run());
  }

  @Test
  public void testExecute_jarContainerization()
      throws DigestException, VerificationException, IOException, InterruptedException {
    String targetImage = "jarcontainerizationimage:maven" + System.nanoTime();
    buildToDockerDaemon(simpleTestProject, targetImage, "pom-jar-containerization.xml");
    Assert.assertEquals(
        "Hello, world. \nImplementation-Title: hello-world\nImplementation-Version: 1\n",
        new Command("docker", "run", "--rm", targetImage).run());
  }

  @Test
  public void testExecute_jarContainerizationOnMissingJar() throws IOException {
    try {
      Verifier verifier = new Verifier(simpleTestProject.getProjectRoot().toString());
      verifier.setSystemProperty("_TARGET_IMAGE", "erroronmissingjar");
      verifier.setAutoclean(false);
      verifier.addCliOption("--file=pom-jar-containerization.xml");
      verifier.executeGoals(Arrays.asList("clean", "jib:dockerBuild"));
      Assert.fail();

    } catch (VerificationException ex) {
      MatcherAssert.assertThat(
          ex.getMessage(),
          CoreMatchers.containsString(
              "Obtaining project build output files failed; make sure you have packaged your "
                  + "project before trying to build the image. (Did you accidentally run \"mvn "
                  + "clean jib:build\" instead of \"mvn clean package jib:build\"?)"));
    }
  }

  @Test
  public void testExecute_jibRequireVersion_ok() throws VerificationException, IOException {
    String targetImage = "simpleimage:maven" + System.nanoTime();

    Verifier verifier = new Verifier(simpleTestProject.getProjectRoot().toString());
    // this plugin should match 1.0
    verifier.setSystemProperty("jib.requiredVersion", "1.0");
    verifier.setSystemProperty("_TARGET_IMAGE", targetImage);
    verifier.executeGoals(Arrays.asList("package", "jib:dockerBuild"));
    verifier.verifyErrorFreeLog();
  }

  @Test
  public void testExecute_jibRequireVersion_fail() throws IOException {
    String targetImage = "simpleimage:maven" + System.nanoTime();

    try {
      Verifier verifier = new Verifier(simpleTestProject.getProjectRoot().toString());
      verifier.setSystemProperty("jib.requiredVersion", "[,1.0]");
      verifier.setSystemProperty("_TARGET_IMAGE", targetImage);
      verifier.executeGoals(Arrays.asList("package", "jib:dockerBuild"));
      Assert.fail();
    } catch (VerificationException ex) {
      MatcherAssert.assertThat(
          ex.getMessage(), CoreMatchers.containsString("but is required to be [,1.0]"));
    }
  }

  @Test
  public void testCredHelperConfigurationSimple()
      throws DigestException, VerificationException, IOException, InterruptedException {
    String targetImage = "simpleimage:maven" + System.nanoTime();
    buildToDockerDaemon(simpleTestProject, targetImage, "pom-cred-helper-1.xml");
    Assert.assertEquals(
        "Hello, world. \n1970-01-01T00:00:01Z\n",
        new Command("docker", "run", "--rm", targetImage).run());
  }

  @Test
  public void testCredHelperConfigurationComplex()
      throws DigestException, VerificationException, IOException, InterruptedException {
    String targetImage = "simpleimage:maven" + System.nanoTime();
    buildToDockerDaemon(simpleTestProject, targetImage, "pom-cred-helper-2.xml");
    Assert.assertEquals(
        "Hello, world. \n1970-01-01T00:00:01Z\n",
        new Command("docker", "run", "--rm", targetImage).run());
  }
}
