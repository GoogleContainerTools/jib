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
import com.google.cloud.tools.jib.api.HttpRequestTester;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.security.DigestException;
import javax.annotation.Nullable;
import org.junit.After;
import org.junit.ClassRule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Integration tests for building WAR images. */
class WarProjectIntegrationTest {

  @TempDir Path tempDir;

  @ClassRule public final TestProject servlet25Project = new TestProject("war_servlet25", tempDir);

  @Nullable private String containerName;

  @After
  void tearDown() throws IOException, InterruptedException {
    if (containerName != null) {
      new Command("docker", "stop", containerName).run();
    }
  }

  @Test
  void testBuild_jettyServlet25() throws IOException, InterruptedException, DigestException {
    verifyBuildAndRun(servlet25Project, "war_jetty_servlet25:gradle", "build.gradle");
  }

  @Test
  void testBuild_tomcatServlet25() throws IOException, InterruptedException, DigestException {
    verifyBuildAndRun(servlet25Project, "war_tomcat_servlet25:gradle", "build-tomcat.gradle");
  }

  private void verifyBuildAndRun(TestProject project, String label, String gradleBuildFile)
      throws IOException, InterruptedException, DigestException {
    String nameBase = IntegrationTestingConfiguration.getTestRepositoryLocation() + '/';
    String targetImage = nameBase + label + System.nanoTime();
    String output =
        JibRunHelper.buildAndRun(project, targetImage, gradleBuildFile, "--detach", "-p8080:8080");
    containerName = output.trim();

    HttpRequestTester.verifyBody(
        "Hello world",
        new URL("http://" + HttpRequestTester.fetchDockerHostForHttpRequest() + ":8080/hello"));
  }
}
