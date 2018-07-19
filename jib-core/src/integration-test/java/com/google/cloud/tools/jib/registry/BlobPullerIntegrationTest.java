/*
 * Copyright 2018 Google LLC. All rights reserved.
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

package com.google.cloud.tools.jib.registry;

import com.google.cloud.tools.jib.hash.CountingDigestOutputStream;
import com.google.cloud.tools.jib.image.DescriptorDigest;
import com.google.cloud.tools.jib.image.json.V21ManifestTemplate;
import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.io.OutputStream;
import java.security.DigestException;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mockito;

/** Integration tests for {@link BlobPuller}. */
public class BlobPullerIntegrationTest {

  @ClassRule public static LocalRegistry localRegistry = new LocalRegistry(5000);

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void testPull() throws IOException, RegistryException {
    // Pulls the busybox image.
    RegistryClient registryClient =
        RegistryClient.factory("localhost:5000", "busybox").setAllowHttp(true).newRegistryClient();
    V21ManifestTemplate manifestTemplate =
        registryClient.pullManifest("latest", V21ManifestTemplate.class);

    DescriptorDigest realDigest = manifestTemplate.getLayerDigests().get(0);

    // Pulls a layer BLOB of the busybox image.
    CountingDigestOutputStream layerOutputStream =
        new CountingDigestOutputStream(ByteStreams.nullOutputStream());
    registryClient.pullBlob(realDigest, layerOutputStream);

    Assert.assertEquals(realDigest, layerOutputStream.toBlobDescriptor().getDigest());
  }

  @Test
  public void testPull_unknownBlob() throws RegistryException, IOException, DigestException {
    DescriptorDigest nonexistentDigest =
        DescriptorDigest.fromHash(
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");

    try {
      RegistryClient registryClient =
          RegistryClient.factory("localhost:5000", "busybox")
              .setAllowHttp(true)
              .newRegistryClient();
      registryClient.pullBlob(nonexistentDigest, Mockito.mock(OutputStream.class));
      Assert.fail("Trying to pull nonexistent blob should have errored");

    } catch (RegistryErrorException ex) {
      Assert.assertThat(
          ex.getMessage(),
          CoreMatchers.containsString(
              "pull BLOB for localhost:5000/busybox with digest " + nonexistentDigest));
    }
  }
}
