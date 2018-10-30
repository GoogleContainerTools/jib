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
import com.google.cloud.tools.jib.blob.Blobs;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestException;
import javax.annotation.Nullable;
import org.junit.After;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;

/** Integration tests for building WAR images. */
public class WarProjectIntegrationTest {

  @ClassRule public static final TestProject servlet25Project = new TestProject("war_servlet25");

  @Nullable
  private static String getContent(URL url) throws InterruptedException {
    for (int i = 0; i < 40; i++) {
      Thread.sleep(500);
      try {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
          try (InputStream in = connection.getInputStream()) {
            return Blobs.writeToString(Blobs.from(in));
          }
        }
      } catch (IOException ex) {
      }
    }
    return null;
  }

  @Nullable private String containerName;

  @After
  public void tearDown() throws IOException, InterruptedException {
    if (containerName != null) {
      new Command("docker", "stop", containerName).run();
    }
  }

  @Test
  public void testBuild_jettyServlet25() throws IOException, InterruptedException, DigestException {
    verifyBuildAndRun(servlet25Project, "war_jetty_servlet25:gradle", "build.gradle");
  }

  @Test
  public void testBuild_tomcatServlet25()
      throws IOException, InterruptedException, DigestException {
    verifyBuildAndRun(servlet25Project, "war_tomcat_servlet25:gradle", "build-tomcat.gradle");
  }

  @Test
  public void testDockerContext_jettyServlet25() throws IOException, InterruptedException {
    String expectedDockerContext =
        "FROM gcr.io/distroless/java/jetty\n"
            + "\n"
            + "COPY libs /\n"
            + "COPY resources /\n"
            + "COPY classes /";
    verifyDockerContextBuildAndRun(expectedDockerContext, "build.gradle");
  }

  @Test
  public void testDockerContext_tomcatServlet25() throws IOException, InterruptedException {
    String expectedDockerContext =
        "FROM tomcat:8.5-jre8-alpine\n"
            + "\n"
            + "COPY libs /\n"
            + "COPY resources /\n"
            + "COPY classes /";
    verifyDockerContextBuildAndRun(expectedDockerContext, "build-tomcat.gradle");
  }

  private void verifyBuildAndRun(TestProject project, String label, String gradleBuildFile)
      throws IOException, InterruptedException, DigestException {
    String nameBase = "gcr.io/" + IntegrationTestingConfiguration.getGCPProject() + '/';
    String targetImage = nameBase + label + System.nanoTime();
    String output =
        JibRunHelper.buildAndRun(project, targetImage, gradleBuildFile, "--detach", "-p8080:8080");
    containerName = output.trim();

    Assert.assertEquals("Hello world", getContent(new URL("http://localhost:8080/hello")));
  }

  private void verifyDockerContextBuildAndRun(String expectedDockerfile, String gradleBuildFile)
      throws IOException, InterruptedException {
    servlet25Project.build("clean", "jibExportDockerContext", "-b=" + gradleBuildFile);

    Path dockerContext =
        servlet25Project.getProjectRoot().resolve("build").resolve("jib-docker-context");
    Assert.assertTrue(Files.exists(dockerContext));
    String dockerfile = String.join("\n", Files.readAllLines(dockerContext.resolve("Dockerfile")));
    Assert.assertEquals(expectedDockerfile, dockerfile);

    String imageName = "jib/integration-test" + System.nanoTime();
    new Command("docker", "build", "-t", imageName, dockerContext.toString()).run();
    containerName =
        new Command("docker", "run", "--rm", "--detach", "-p8080:8080", imageName).run().trim();

    Assert.assertEquals("Hello world", getContent(new URL("http://localhost:8080/hello")));
  }
}
