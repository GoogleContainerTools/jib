/*
 * Copyright 2019 Google LLC.
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
import com.google.common.collect.Lists;
import java.io.IOException;
import java.net.URL;
import java.security.DigestException;
import java.util.List;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import org.junit.After;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;

/** Integration tests for building Spring Boot images. */
public class SpringBootProjectIntegrationTest {

  private static final Logger LOGGER =
      Logger.getLogger(SpringBootProjectIntegrationTest.class.getName());

  @ClassRule public static final TestProject springBootProject = new TestProject("spring-boot");

  @Nullable private String containerName;

  private final String dockerHost =
      System.getenv("DOCKER_IP") != null ? System.getenv("DOCKER_IP") : "localhost";

  @After
  public void tearDown() throws IOException, InterruptedException {
    if (containerName != null) {
      new Command("docker", "stop", containerName).run();
    }
  }

  @Test
  public void testBuild_packagedMode() throws IOException, InterruptedException, DigestException {
    buildAndRunWebApp("springboot:gradle", "build.gradle");

    String output =
        new Command(
                "docker",
                "exec",
                containerName,
                "/busybox/wc",
                "-c",
                "/app/classpath/spring-boot-original.jar")
            .run();
    Assert.assertEquals("1360 /app/classpath/spring-boot-original.jar\n", output);

    Assert.assertEquals(
        "Hello world", JibRunHelper.getContent(new URL("http://" + dockerHost + ":8080")));
  }

  private void buildAndRunWebApp(String label, String gradleBuildFile)
      throws IOException, InterruptedException, DigestException {
    String nameBase = IntegrationTestingConfiguration.getTestRepositoryLocation() + '/';
    String targetImage = nameBase + label + System.nanoTime();
    String output =
        JibRunHelper.buildAndRun(
            springBootProject, targetImage, gradleBuildFile, "--detach", "-p8080:8080");
    containerName = output.trim();
    LOGGER.info("Container name: " + containerName);
    if (System.getenv("KOKORO_JOB_CLUSTER") != null
        && System.getenv("KOKORO_JOB_CLUSTER").equals("GCP_UBUNTU_DOCKER")) {
      String containerIp = getAndMapContainerIp(containerName);
      LOGGER.info("Mapped registry container IP to localhost: " + containerIp);
    }
  }

  /** Gets container IP and associates it to localhost. */
  private String getAndMapContainerIp(String containerName) {
    String containerIp;

    // Gets local registry container IP
    List<String> dockerTokens =
        Lists.newArrayList(
            "docker",
            "inspect",
            "-f",
            "'{{range.NetworkSettings.Networks}}{{.IPAddress}}{{end}}'",
            containerName);
    try {
      String result = new Command(dockerTokens).run();
      // Remove single quotes and LF from result (e.g. '127.0.0.1'\n)
      containerIp = result.replaceAll("['\n]", "");
    } catch (InterruptedException | IOException ex) {
      throw new RuntimeException("Could get container IP for: " + containerName, ex);
    }

    // Associate container IP with localhost
    try {
      String addHost =
          new Command("bash", "-c", "echo \"" + containerIp + " localhost\" >> /etc/hosts").run();
    } catch (InterruptedException | IOException ex) {
      throw new RuntimeException("Could not associate container IP to localhost: " + containerIp);
    }

    return containerIp;
  }
}
