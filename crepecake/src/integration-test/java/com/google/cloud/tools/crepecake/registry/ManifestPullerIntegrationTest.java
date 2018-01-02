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

import com.google.cloud.tools.crepecake.image.json.ManifestTemplate;
import com.google.cloud.tools.crepecake.image.json.UnknownManifestFormatException;
import com.google.cloud.tools.crepecake.image.json.V21ManifestTemplate;
import com.google.cloud.tools.crepecake.image.json.V22ManifestTemplate;
import java.io.IOException;
import org.hamcrest.CoreMatchers;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class ManifestPullerIntegrationTest {

  @BeforeClass
  public static void startLocalRegistry() throws IOException, InterruptedException {
    String runRegistryCommand =
        "docker run -d -p 5000:5000 --restart=always --name registry registry:2";
    Runtime.getRuntime().exec(runRegistryCommand).waitFor();

    String pullImageCommand = "docker pull busybox";
    Runtime.getRuntime().exec(pullImageCommand).waitFor();

    String tagImageCommand = "docker tag busybox localhost:5000/busybox";
    Runtime.getRuntime().exec(tagImageCommand).waitFor();

    String pushImageCommand = "docker push localhost:5000/busybox";
    Runtime.getRuntime().exec(pushImageCommand).waitFor();
  }

  @AfterClass
  public static void stopLocalRegistry() throws IOException, InterruptedException {
    String stopRegistryCommand = "docker stop registry";
    Runtime.getRuntime().exec(stopRegistryCommand).waitFor();

    String removeRegistryContainerCommand = "docker rm -v registry";
    Runtime.getRuntime().exec(removeRegistryContainerCommand).waitFor();
  }

  @Test
  public void testPull_v21()
      throws IOException, RegistryErrorException, RegistryUnauthorizedException,
          UnknownManifestFormatException {
    ManifestPuller manifestPuller = new ManifestPuller(null, "localhost:5000", "busybox");
    ManifestTemplate manifestTemplate = manifestPuller.pull("latest");

    Assert.assertEquals(1, manifestTemplate.getSchemaVersion());
    V21ManifestTemplate v21ManifestTemplate = (V21ManifestTemplate) manifestTemplate;
    Assert.assertTrue(0 < v21ManifestTemplate.getFsLayers().size());
  }

  @Test
  public void testPull_v22()
      throws IOException, RegistryErrorException, RegistryUnauthorizedException,
          UnknownManifestFormatException {
    ManifestPuller manifestPuller = new ManifestPuller(null, "gcr.io", "distroless/java");
    ManifestTemplate manifestTemplate = manifestPuller.pull("latest");

    Assert.assertEquals(2, manifestTemplate.getSchemaVersion());
    V22ManifestTemplate v22ManifestTemplate = (V22ManifestTemplate) manifestTemplate;
    Assert.assertTrue(0 < v22ManifestTemplate.getLayers().size());
  }

  @Test
  public void testPull_unknownManifest()
      throws RegistryUnauthorizedException, IOException, UnknownManifestFormatException {
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
