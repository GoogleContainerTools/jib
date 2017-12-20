/*
 * Copyright 2017 Google Inc.
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

package com.google.cloud.tools.crepecake.registry;

import com.google.cloud.tools.crepecake.image.json.ManifestTemplateHolder;
import com.google.cloud.tools.crepecake.image.json.UnknownManifestFormatException;
import com.google.cloud.tools.crepecake.image.json.V21ManifestTemplate;
import com.google.cloud.tools.crepecake.image.json.V22ManifestTemplate;
import java.io.IOException;
import java.util.logging.Logger;
import org.hamcrest.CoreMatchers;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class ManifestPullerIntegrationTest {

  private static final Logger logger =
      Logger.getLogger(ManifestPullerIntegrationTest.class.getName());

  /**
   * True if the local registry is running. These tests are skipped if the local registry failed to
   * start.
   */
  private static boolean localRegistryRunning;

  @BeforeClass
  public static void startLocalRegistry() {
    try {
      String runRegistryCommand =
          "docker run -d -p 5000:5000 --restart=always --name registry registry:2";
      Runtime.getRuntime().exec(runRegistryCommand).waitFor();

      String pullImageCommand = "docker pull busybox";
      Runtime.getRuntime().exec(pullImageCommand).waitFor();

      String tagImageCommand = "docker tag busybox localhost:5000/busybox";
      Runtime.getRuntime().exec(tagImageCommand).waitFor();

      String pushImageCommand = "docker push localhost:5000/busybox";
      Runtime.getRuntime().exec(pushImageCommand).waitFor();

      localRegistryRunning = true;
    } catch (IOException | InterruptedException ex) {
      // The local registry failed to start.
      logger.info(
          "Skipping ManifestPullerIntegrationTest because failed to set up local registry: "
              + ex.getMessage());
      localRegistryRunning = false;
    }
  }

  @AfterClass
  public static void stopLocalRegistry() throws IOException, InterruptedException {
    if (!localRegistryRunning) {
      return;
    }

    String stopRegistryCommand = "docker stop registry";
    Runtime.getRuntime().exec(stopRegistryCommand).waitFor();

    String removeRegistryContainerCommand = "docker rm -v registry";
    Runtime.getRuntime().exec(removeRegistryContainerCommand).waitFor();
  }

  @Test
  public void testPull()
      throws IOException, RegistryErrorException, RegistryUnauthorizedException,
          RegistryTooManyRequestsException, UnknownManifestFormatException {
    if (!localRegistryRunning) {
      return;
    }

    ManifestPuller manifestPuller = new ManifestPuller(null, "localhost:5000", "busybox");
    ManifestTemplateHolder manifestTemplateHolder = manifestPuller.pull("latest");

    if (manifestTemplateHolder.isV21()) {
      V21ManifestTemplate manifestTemplate = manifestTemplateHolder.getV21ManifestTemplate();
      Assert.assertTrue(0 < manifestTemplate.getLayerDigests().size());
    } else if (manifestTemplateHolder.isV22()) {
      V22ManifestTemplate manifestTemplate = manifestTemplateHolder.getV22ManifestTemplate();
      Assert.assertTrue(0 < manifestTemplate.getLayers().size());
    } else {
      Assert.fail("Found unrecognizable image manifest version");
    }
  }

  @Test
  public void testPull_unknownManifest()
      throws RegistryTooManyRequestsException, RegistryUnauthorizedException, IOException,
          UnknownManifestFormatException {
    if (!localRegistryRunning) {
      return;
    }

    try {
      ManifestPuller manifestPuller = new ManifestPuller(null, "localhost:5000", "busybox");
      manifestPuller.pull("nonexistent-tag");
      Assert.fail("Trying to pull nonexistent image should have errored");
    } catch (RegistryErrorException ex) {
      Assert.assertThat(
          ex.getMessage(),
          CoreMatchers.containsString(
              "pull image manifest for localhost:5000/busybox:nonexistent-tag"));
    }
  }
}
