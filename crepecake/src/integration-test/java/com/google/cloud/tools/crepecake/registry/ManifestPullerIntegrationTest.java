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
import com.google.cloud.tools.crepecake.image.json.V21ManifestTemplate;
import com.google.cloud.tools.crepecake.image.json.V22ManifestTemplate;
import java.io.IOException;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;

public class ManifestPullerIntegrationTest {

  @ClassRule public static LocalRegistry localRegistry = new LocalRegistry(5000);

  @Test
  public void testPull_v21() throws IOException, RegistryException {
    RegistryClient registryClient = new RegistryClient(null, "localhost:5000", "busybox");
    ManifestTemplate manifestTemplate = registryClient.pullManifest("latest");

    Assert.assertEquals(1, manifestTemplate.getSchemaVersion());
    V21ManifestTemplate v21ManifestTemplate = (V21ManifestTemplate) manifestTemplate;
    Assert.assertTrue(0 < v21ManifestTemplate.getFsLayers().size());
  }

  @Test
  public void testPull_v22() throws IOException, RegistryException {
    RegistryClient registryClient = new RegistryClient(null, "gcr.io", "distroless/java");
    ManifestTemplate manifestTemplate = registryClient.pullManifest("latest");

    Assert.assertEquals(2, manifestTemplate.getSchemaVersion());
    V22ManifestTemplate v22ManifestTemplate = (V22ManifestTemplate) manifestTemplate;
    Assert.assertTrue(0 < v22ManifestTemplate.getLayers().size());
  }

  @Test
  public void testPull_unknownManifest() throws RegistryException, IOException {
    try {
      RegistryClient registryClient = new RegistryClient(null, "localhost:5000", "busybox");
      registryClient.pullManifest("nonexistent-tag");
      Assert.fail("Trying to pull nonexistent image should have errored");

    } catch (RegistryErrorException ex) {
      Assert.assertThat(
          ex.getMessage(),
          CoreMatchers.containsString(
              "pull image manifest for localhost:5000/busybox:nonexistent-tag"));
    }
  }
}
