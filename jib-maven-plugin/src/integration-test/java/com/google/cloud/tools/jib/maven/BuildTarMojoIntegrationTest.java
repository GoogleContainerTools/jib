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

import com.google.cloud.tools.jib.Command;
import java.io.IOException;
import java.nio.file.Path;
import java.security.DigestException;
import java.util.Arrays;
import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.Assert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class BuildTarMojoIntegrationTest {

  @TempDir Path tempDir;

  @RegisterExtension
  public final TestProject simpleTestProject = new TestProject("simple", tempDir);

  @RegisterExtension
  public final TestProject skippedTestProject = new TestProject("empty", tempDir);

  @Test
  public void testExecute_simple()
      throws VerificationException, IOException, InterruptedException, DigestException {
    String targetImage = "simpleimage:maven" + System.nanoTime();

    Verifier verifier = new Verifier(simpleTestProject.getProjectRoot().toString());
    verifier.setSystemProperty("jib.useOnlyProjectCache", "true");
    verifier.setSystemProperty("_TARGET_IMAGE", targetImage);
    verifier.setAutoclean(false);
    verifier.executeGoal("package");

    verifier.executeGoal("jib:" + BuildTarMojo.GOAL_NAME);
    verifier.verifyErrorFreeLog();

    BuildImageMojoIntegrationTest.readDigestFile(
        simpleTestProject.getProjectRoot().resolve("target/jib-image.digest"));

    new Command(
            "docker",
            "load",
            "--input",
            simpleTestProject
                .getProjectRoot()
                .resolve("target")
                .resolve("different-jib-image.tar")
                .toString())
        .run();
    Assert.assertEquals(
        "Hello, world. An argument.\n1970-01-01T00:00:01Z\nrw-r--r--\nrw-r--r--\nfoo\ncat\n1970-01-01T00:00:01Z\n1970-01-01T00:00:01Z\n",
        new Command("docker", "run", "--rm", targetImage).run());
    Assert.assertEquals(
        "1970-01-01T00:00:00Z",
        new Command("docker", "inspect", "-f", "{{.Created}}", targetImage).run().trim());
  }

  @Test
  public void testExecute_jibSkip() throws VerificationException, IOException {
    SkippedGoalVerifier.verifyJibSkip(skippedTestProject, BuildTarMojo.GOAL_NAME);
  }

  @Test
  public void testExecute_jibContainerizeSkips() throws VerificationException, IOException {
    SkippedGoalVerifier.verifyJibContainerizeSkips(simpleTestProject, BuildDockerMojo.GOAL_NAME);
  }

  @Test
  public void testExecute_jibRequireVersion_ok() throws VerificationException, IOException {
    String targetImage = "simpleimage:maven" + System.nanoTime();

    Verifier verifier = new Verifier(simpleTestProject.getProjectRoot().toString());
    // this plugin should match 1.0
    verifier.setSystemProperty("jib.requiredVersion", "1.0");
    verifier.setSystemProperty("_TARGET_IMAGE", targetImage);
    verifier.executeGoals(Arrays.asList("package", "jib:buildTar"));
    verifier.verifyErrorFreeLog();
  }

  @Test
  public void testExecute_jibRequireVersion_fail() throws IOException {
    String targetImage = "simpleimage:maven" + System.nanoTime();

    try {
      Verifier verifier = new Verifier(simpleTestProject.getProjectRoot().toString());
      verifier.setSystemProperty("jib.requiredVersion", "[,1.0]");
      verifier.setSystemProperty("_TARGET_IMAGE", targetImage);
      verifier.executeGoals(Arrays.asList("package", "jib:buildTar"));
      Assert.fail();
    } catch (VerificationException ex) {
      MatcherAssert.assertThat(
          ex.getMessage(), CoreMatchers.containsString("but is required to be [,1.0]"));
    }
  }
}
