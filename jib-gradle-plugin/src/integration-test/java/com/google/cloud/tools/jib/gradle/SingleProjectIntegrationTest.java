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

package com.google.cloud.tools.jib.gradle;

import com.google.cloud.tools.jib.Command;
import com.google.cloud.tools.jib.IntegrationTestingConfiguration;
import com.google.cloud.tools.jib.registry.LocalRegistry;
import java.io.IOException;
import java.security.DigestException;
import java.time.Instant;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.UnexpectedBuildFailure;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

/** Integration tests for building single project images. */
public class SingleProjectIntegrationTest {

  @ClassRule
  public static final LocalRegistry localRegistry1 =
      new LocalRegistry(5000, "testuser", "testpassword");

  @ClassRule
  public static final LocalRegistry localRegistry2 =
      new LocalRegistry(6000, "testuser2", "testpassword2");

  @ClassRule public static final TestProject simpleTestProject = new TestProject("simple");

  /**
   * Asserts that the creation time of the simple test project is set. If the time parsed from the
   * {@code docker inspect} command occurs before the specified time (i.e. if it is 1970), then the
   * assertion will fail.
   *
   * @param before the specified time to compare the resulting image's creation time to
   * @param imageReference the image to test
   * @throws IOException if the {@code docker inspect} command fails to run
   * @throws InterruptedException if the {@code docker inspect} command is interrupted
   */
  private static void assertSimpleCreationTimeIsAfter(Instant before, String imageReference)
      throws IOException, InterruptedException {
    String inspect =
        new Command("docker", "inspect", "-f", "{{.Created}}", imageReference).run().trim();
    Instant parsed = Instant.parse(inspect);
    Assert.assertTrue(parsed.isAfter(before) || parsed.equals(before));
  }

  private static void assertWorkingDirectory(String expected, String imageReference)
      throws IOException, InterruptedException {
    Assert.assertEquals(
        expected,
        new Command("docker", "inspect", "-f", "{{.Config.WorkingDir}}", imageReference)
            .run()
            .trim());
  }

  /**
   * Asserts that the test project has the required exposed ports, labels and volumes.
   *
   * @param imageReference the image to test
   * @throws IOException if the {@code docker inspect} command fails to run
   * @throws InterruptedException if the {@code docker inspect} command is interrupted
   */
  private static void assertDockerInspect(String imageReference)
      throws IOException, InterruptedException {
    String dockerInspect = new Command("docker", "inspect", imageReference).run();
    Assert.assertThat(
        dockerInspect,
        CoreMatchers.containsString(
            "            \"Volumes\": {\n"
                + "                \"/var/log\": {},\n"
                + "                \"/var/log2\": {}\n"
                + "            },"));
    Assert.assertThat(
        dockerInspect,
        CoreMatchers.containsString(
            "            \"ExposedPorts\": {\n"
                + "                \"1000/tcp\": {},\n"
                + "                \"2000/udp\": {},\n"
                + "                \"2001/udp\": {},\n"
                + "                \"2002/udp\": {},\n"
                + "                \"2003/udp\": {}"));
    Assert.assertThat(
        dockerInspect,
        CoreMatchers.containsString(
            "            \"Labels\": {\n"
                + "                \"key1\": \"value1\",\n"
                + "                \"key2\": \"value2\"\n"
                + "            }"));
  }

  private static String buildAndRunComplex(
      String imageReference, String username, String password, LocalRegistry targetRegistry)
      throws IOException, InterruptedException {
    BuildResult buildResult =
        simpleTestProject.build(
            "clean",
            "jib",
            "-D_TARGET_IMAGE=" + imageReference,
            "-D_TARGET_USERNAME=" + username,
            "-D_TARGET_PASSWORD=" + password,
            "-DsendCredentialsOverHttp=true",
            "-b=complex-build.gradle");

    JibRunHelper.assertBuildSuccess(buildResult, "jib", "Built and pushed image as ");
    Assert.assertThat(buildResult.getOutput(), CoreMatchers.containsString(imageReference));

    targetRegistry.pull(imageReference);
    assertDockerInspect(imageReference);
    String history = new Command("docker", "history", imageReference).run();
    Assert.assertThat(history, CoreMatchers.containsString("jib-gradle-plugin"));
    return new Command("docker", "run", "--rm", imageReference).run();
  }

  @Before
  public void setup() throws IOException, InterruptedException {
    // Pull distroless and push to local registry so we can test 'from' credentials
    localRegistry1.pullAndPushToLocal("gcr.io/distroless/java:latest", "distroless/java");
  }

  @Test
  public void testBuild_simple() throws IOException, InterruptedException, DigestException {
    String targetImage =
        "gcr.io/"
            + IntegrationTestingConfiguration.getGCPProject()
            + "/simpleimage:gradle"
            + System.nanoTime();

    // Test empty output error
    try {
      simpleTestProject.build("clean", "jib", "-x=classes", "-D_TARGET_IMAGE=" + targetImage);
      Assert.fail();

    } catch (UnexpectedBuildFailure ex) {
      Assert.assertThat(
          ex.getMessage(),
          CoreMatchers.containsString(
              "No classes files were found - did you compile your project?"));
    }

    Instant beforeBuild = Instant.now();
    Assert.assertEquals(
        "Hello, world. An argument.\nrw-r--r--\nrw-r--r--\nfoo\ncat\n",
        JibRunHelper.buildAndRun(simpleTestProject, targetImage));
    assertDockerInspect(targetImage);
    assertSimpleCreationTimeIsAfter(beforeBuild, targetImage);
    assertWorkingDirectory("/home", targetImage);
  }

  @Test
  public void testBuild_complex() throws IOException, InterruptedException {
    String targetImage = "localhost:6000/compleximage:gradle" + System.nanoTime();
    Instant beforeBuild = Instant.now();
    Assert.assertEquals(
        "Hello, world. An argument.\nrwxr-xr-x\nrwxrwxrwx\nfoo\ncat\n-Xms512m\n-Xdebug\nenvvalue1\nenvvalue2\n",
        buildAndRunComplex(targetImage, "testuser2", "testpassword2", localRegistry2));
    assertSimpleCreationTimeIsAfter(beforeBuild, targetImage);
    assertWorkingDirectory("", targetImage);
  }

  @Test
  public void testBuild_complex_sameFromAndToRegistry() throws IOException, InterruptedException {
    String targetImage = "localhost:5000/compleximage:gradle" + System.nanoTime();
    Instant beforeBuild = Instant.now();
    Assert.assertEquals(
        "Hello, world. An argument.\nrwxr-xr-x\nrwxrwxrwx\nfoo\ncat\n-Xms512m\n-Xdebug\nenvvalue1\nenvvalue2\n",
        buildAndRunComplex(targetImage, "testuser", "testpassword", localRegistry1));
    assertSimpleCreationTimeIsAfter(beforeBuild, targetImage);
    assertWorkingDirectory("", targetImage);
  }

  @Test
  public void testDockerDaemon_simple() throws IOException, InterruptedException, DigestException {
    String targetImage = "simpleimage:gradle" + System.nanoTime();
    Instant beforeBuild = Instant.now();
    Assert.assertEquals(
        "Hello, world. An argument.\nrw-r--r--\nrw-r--r--\nfoo\ncat\n",
        JibRunHelper.buildToDockerDaemonAndRun(simpleTestProject, targetImage));
    assertSimpleCreationTimeIsAfter(beforeBuild, targetImage);
    assertDockerInspect(targetImage);
    assertWorkingDirectory("/home", targetImage);
  }

  @Test
  public void testBuildTar_simple() throws IOException, InterruptedException {
    String targetImage = "simpleimage:gradle" + System.nanoTime();

    String outputPath =
        simpleTestProject.getProjectRoot().resolve("build").resolve("jib-image.tar").toString();
    Instant beforeBuild = Instant.now();
    BuildResult buildResult =
        simpleTestProject.build("clean", "jibBuildTar", "-D_TARGET_IMAGE=" + targetImage);

    JibRunHelper.assertBuildSuccess(buildResult, "jibBuildTar", "Built image tarball at ");
    Assert.assertThat(buildResult.getOutput(), CoreMatchers.containsString(outputPath));

    new Command("docker", "load", "--input", outputPath).run();
    Assert.assertEquals(
        "Hello, world. An argument.\nrw-r--r--\nrw-r--r--\nfoo\ncat\n",
        new Command("docker", "run", "--rm", targetImage).run());
    assertDockerInspect(targetImage);
    assertSimpleCreationTimeIsAfter(beforeBuild, targetImage);
    assertWorkingDirectory("/home", targetImage);
  }
}
