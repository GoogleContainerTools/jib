/*
 * Copyright 2018 Google Inc.
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

import com.google.cloud.tools.crepecake.blob.Blob;
import com.google.cloud.tools.crepecake.blob.BlobDescriptor;
import com.google.cloud.tools.crepecake.image.DescriptorDigest;
import com.google.cloud.tools.crepecake.image.json.ManifestTemplate;
import com.google.cloud.tools.crepecake.image.json.V21ManifestTemplate;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestException;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mockito;

/** Integration tests for {@link BlobPuller}. */
public class BlobPullerIntegrationTest {

  @ClassRule public static LocalRegistry localRegistry = new LocalRegistry(5000);

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @BeforeClass
  public static void setUpLocalRegistry() throws IOException, InterruptedException {
    localRegistry.pullBusybox();
  }

  @Test
  public void testPull() throws IOException, RegistryException, DigestException {
    // Pulls the busybox image.
    RegistryClient registryClient = new RegistryClient(null, "localhost:5000", "busybox");
    ManifestTemplate manifestTemplate = registryClient.pullManifest("latest");

    V21ManifestTemplate v21ManifestTemplate = (V21ManifestTemplate) manifestTemplate;
    DescriptorDigest realDigest = v21ManifestTemplate.getLayerDigests().get(0);

    // Pulls a layer BLOB of the busybox image.
    Path destFile = temporaryFolder.newFile().toPath();
    Path checkBlobFile = temporaryFolder.newFile().toPath();

    Blob blob = registryClient.pullBlob(realDigest, destFile);

    try (OutputStream outputStream =
        new BufferedOutputStream(Files.newOutputStream(checkBlobFile))) {
      BlobDescriptor blobDescriptor = blob.writeTo(outputStream);
      Assert.assertEquals(realDigest, blobDescriptor.getDigest());
    }

    Assert.assertArrayEquals(Files.readAllBytes(destFile), Files.readAllBytes(checkBlobFile));
  }

  @Test
  public void testPull_unknownBlob() throws RegistryException, IOException, DigestException {
    DescriptorDigest nonexistentDigest =
        DescriptorDigest.fromHash(
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");

    try {
      RegistryClient registryClient = new RegistryClient(null, "localhost:5000", "busybox");
      registryClient.pullBlob(nonexistentDigest, Mockito.mock(Path.class));
      Assert.fail("Trying to pull nonexistent blob should have errored");

    } catch (RegistryErrorException ex) {
      Assert.assertThat(
          ex.getMessage(),
          CoreMatchers.containsString(
              "pull BLOB for localhost:5000/busybox with digest " + nonexistentDigest));
    }
  }
}
