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
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import javax.annotation.Nullable;
import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.hamcrest.CoreMatchers;
import org.junit.After;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

/** Integration tests for {@link DockerContextMojo}. */
@RunWith(MockitoJUnitRunner.class)
public class DockerContextMojoIntegrationTest {

  @ClassRule public static final TestPlugin testPlugin = new TestPlugin();

  @ClassRule
  public static final TestProject simpleTestProject = new TestProject(testPlugin, "simple");

  @ClassRule
  public static final TestProject skippedTestProject = new TestProject(testPlugin, "empty");

  @ClassRule
  public static final TestProject servlet25Project = new TestProject(testPlugin, "war_servlet25");

  @Nullable private String detachedContainerName;

  @After
  public void tearDown() throws IOException, InterruptedException {
    if (detachedContainerName != null) {
      new Command("docker", "stop", detachedContainerName).run();
    }
  }

  @Test
  public void testExecute() throws VerificationException, IOException, InterruptedException {
    Verifier verifier = new Verifier(simpleTestProject.getProjectRoot().toString());
    verifier.setAutoclean(false);
    verifier.executeGoal("compile");
    verifier.executeGoal("jib:" + DockerContextMojo.GOAL_NAME);

    Path dockerContextDirectory =
        simpleTestProject.getProjectRoot().resolve("target").resolve("jib-docker-context");
    Assert.assertTrue(Files.exists(dockerContextDirectory));

    String imageName = "jib/integration-test" + System.nanoTime();
    new Command("docker", "build", "-t", imageName, dockerContextDirectory.toString()).run();
    String dockerInspect = new Command("docker", "inspect", imageName).run();
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

    String output = new Command("docker", "run", "--rm", imageName).run();
    Assert.assertThat(output, CoreMatchers.startsWith("Hello, world. An argument.\n"));
    Assert.assertThat(output, CoreMatchers.endsWith("foo\ncat\n"));
  }

  @Test
  public void testExecute_skipJibGoal() throws VerificationException, IOException {
    SkippedGoalVerifier.verifyGoalIsSkipped(skippedTestProject, BuildDockerMojo.GOAL_NAME);
  }

  @Test
  public void testExecute_jettyServlet25()
      throws VerificationException, IOException, InterruptedException {
    String expectedDockerfile =
        "FROM gcr.io/distroless/java/jetty\n"
            + "\n"
            + "COPY libs /\n"
            + "COPY resources /\n"
            + "COPY classes /";
    verifyWarBuildAndRun(expectedDockerfile, "pom.xml");
  }

  @Test
  public void testExecute_tomcatServlet25()
      throws VerificationException, IOException, InterruptedException {
    String expectedDockerfile =
        "FROM tomcat:8.5-jre8-alpine\n"
            + "\n"
            + "COPY libs /\n"
            + "COPY resources /\n"
            + "COPY classes /";
    verifyWarBuildAndRun(expectedDockerfile, "pom-tomcat.xml");
  }

  private void verifyWarBuildAndRun(String expectedDockerfile, String pomXml)
      throws VerificationException, IOException, InterruptedException {
    Verifier verifier = new Verifier(servlet25Project.getProjectRoot().toString());
    verifier.setAutoclean(false);
    verifier.addCliOption("--file=" + pomXml);
    verifier.executeGoals(Arrays.asList("clean", "package", "jib:exportDockerContext"));

    Path dockerContext =
        servlet25Project.getProjectRoot().resolve("target").resolve("jib-docker-context");
    Assert.assertTrue(Files.exists(dockerContext));
    String dockerfile = String.join("\n", Files.readAllLines(dockerContext.resolve("Dockerfile")));
    Assert.assertEquals(expectedDockerfile, dockerfile);

    String imageName = "jib/integration-test" + System.nanoTime();
    new Command("docker", "build", "-t", imageName, dockerContext.toString()).run();
    detachedContainerName =
        new Command("docker", "run", "--rm", "--detach", "-p8080:8080", imageName).run().trim();

    HttpGetVerifier.verifyBody("Hello world", new URL("http://localhost:8080/hello"));
  }
}
