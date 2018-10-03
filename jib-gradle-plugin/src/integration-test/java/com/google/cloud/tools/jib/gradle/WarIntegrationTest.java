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
import com.google.common.io.ByteStreams;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;

/** Integration tests for building WAR images. */
public class WarIntegrationTest {

  @ClassRule
  public static final TestProject jettyServlet25Project = new TestProject("war_jetty_servlet25");

  private static String getContent(URL url) throws InterruptedException {
    for (int i = 0; i < 40; i++) {
      Thread.sleep(500);
      try {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        int code = connection.getResponseCode();
        if (code == HttpURLConnection.HTTP_OK) {
          ByteArrayOutputStream out = new ByteArrayOutputStream();
          ByteStreams.copy(connection.getInputStream(), out);
          return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
      } catch (IOException ex) {
      }
    }
    return null;
  }

  @Test
  public void testBuild_jettyServlet25Project() throws IOException, InterruptedException {
    String targetImage =
        "gcr.io/"
            + IntegrationTestingConfiguration.getGCPProject()
            + "/war_jetty_servlet25:gradle"
            + System.nanoTime();

    String containerName =
        JibRunHelper.buildAndRun(jettyServlet25Project, targetImage, "--detach", "-p8080:8080");
    try {
      String content = getContent(new URL("http://localhost:8080/hello"));
      Assert.assertEquals("Hello world", content);
    } finally {
      new Command("docker", "stop", containerName.trim()).run();
    }
  }
}
