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

import static com.google.common.truth.Truth.assertThat;

import com.google.cloud.tools.jib.Command;
import com.google.cloud.tools.jib.IntegrationTestingConfiguration;
import com.google.cloud.tools.jib.api.InvalidImageReferenceException;
import java.io.IOException;
import java.security.DigestException;
import java.time.Instant;
import java.util.logging.Logger;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;

/** Integration tests for building empty project images. */
public class EmptyProjectIntegrationTest {

  private static final Logger LOGGER = Logger.getLogger(EmptyProjectIntegrationTest.class.getName());

  @ClassRule public static final TestProject emptyTestProject = new TestProject("empty");

  /**
   * Asserts that the test project has the required exposed ports and labels.
   *
   * @param imageReference the image to test
   * @throws IOException if the {@code docker inspect} command fails to run
   * @throws InterruptedException if the {@code docker inspect} command is interrupted
   */
  private static void assertDockerInspect(String imageReference)
      throws IOException, InterruptedException {
    String dockerInspectExposedPorts =
            new Command("docker", "inspect", "-f", "'{{.Config.ExposedPorts}}'", imageReference).run();
    String dockerInspectLabels =
            new Command("docker", "inspect", "-f", "'{{.Config.Labels}}'", imageReference).run();
    LOGGER.info(dockerInspectExposedPorts);
    LOGGER.info(dockerInspectLabels);
    MatcherAssert.assertThat(
        dockerInspectExposedPorts,
        CoreMatchers.containsString(
            "            \"ExposedPorts\": {\n"
                + "                \"1000/tcp\": {},\n"
                + "                \"2000/udp\": {},\n"
                + "                \"2001/udp\": {},\n"
                + "                \"2002/udp\": {},\n"
                + "                \"2003/udp\": {}"));
    MatcherAssert.assertThat(
        dockerInspectLabels,
        CoreMatchers.containsString(
            "            \"Labels\": {\n"
                + "                \"key1\": \"value1\",\n"
                + "                \"key2\": \"value2\"\n"
                + "            }"));
  }

  @Test
  public void testBuild_empty() throws IOException, InterruptedException, DigestException {
    String targetImage =
        IntegrationTestingConfiguration.getTestRepositoryLocation()
            + "/emptyimage:gradle"
            + System.nanoTime();
    Assert.assertEquals("", JibRunHelper.buildAndRun(emptyTestProject, targetImage));
    assertDockerInspect(targetImage);
    assertThat(JibRunHelper.getCreationTime(targetImage)).isEqualTo(Instant.EPOCH);
  }

  @Test
  public void testBuild_multipleTags()
      throws IOException, InterruptedException, InvalidImageReferenceException, DigestException {
    String targetImage =
        IntegrationTestingConfiguration.getTestRepositoryLocation()
            + "/multitag-image:gradle"
            + System.nanoTime();
    JibRunHelper.buildAndRunAdditionalTag(
        emptyTestProject, targetImage, "gradle-2" + System.nanoTime(), "");
    assertDockerInspect(targetImage);
  }

  @Test
  public void testDockerDaemon_empty() throws IOException, InterruptedException, DigestException {
    String targetImage = "emptyimage:gradle" + System.nanoTime();
    Assert.assertEquals(
        "", JibRunHelper.buildToDockerDaemonAndRun(emptyTestProject, targetImage, "build.gradle"));
    assertThat(JibRunHelper.getCreationTime(targetImage)).isEqualTo(Instant.EPOCH);
    assertDockerInspect(targetImage);
  }

  @Test
  public void testDockerDaemon_userNumeric()
      throws IOException, InterruptedException, DigestException {
    String targetImage = "emptyimage:gradle" + System.nanoTime();
    JibRunHelper.buildToDockerDaemon(emptyTestProject, targetImage, "build.gradle");
    Assert.assertEquals(
        "12345:54321",
        new Command("docker", "inspect", "-f", "{{.Config.User}}", targetImage).run().trim());
  }

  @Test
  public void testDockerDaemon_userNames()
      throws IOException, InterruptedException, DigestException {
    String targetImage = "brokenuserimage:gradle" + System.nanoTime();
    JibRunHelper.buildToDockerDaemon(emptyTestProject, targetImage, "build-broken-user.gradle");
    Assert.assertEquals(
        "myuser:mygroup",
        new Command("docker", "inspect", "-f", "{{.Config.User}}", targetImage).run().trim());
  }
}
