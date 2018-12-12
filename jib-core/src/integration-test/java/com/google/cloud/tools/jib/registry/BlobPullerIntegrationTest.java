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

package com.google.cloud.tools.jib.registry;

import com.google.cloud.tools.jib.event.EventDispatcher;
import com.google.cloud.tools.jib.hash.CountingDigestOutputStream;
import com.google.cloud.tools.jib.http.TestBlobProgressListener;
import com.google.cloud.tools.jib.image.DescriptorDigest;
import com.google.cloud.tools.jib.image.json.V21ManifestTemplate;
import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.security.DigestException;
import java.util.concurrent.atomic.LongAdder;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Integration tests for {@link BlobPuller}. */
public class BlobPullerIntegrationTest {

  @ClassRule public static LocalRegistry localRegistry = new LocalRegistry(5000);
  private static final EventDispatcher EVENT_DISPATCHER = jibEvent -> {};

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void testPull() throws IOException, RegistryException, InterruptedException {
    // Pulls the busybox image.
    localRegistry.pullAndPushToLocal("busybox", "busybox");
    RegistryClient registryClient =
        RegistryClient.factory(EVENT_DISPATCHER, "localhost:5000", "busybox")
            .setAllowInsecureRegistries(true)
            .newRegistryClient();
    V21ManifestTemplate manifestTemplate =
        registryClient.pullManifest("latest", V21ManifestTemplate.class);

    DescriptorDigest realDigest = manifestTemplate.getLayerDigests().get(0);

    // Pulls a layer BLOB of the busybox image.
    CountingDigestOutputStream layerOutputStream =
        new CountingDigestOutputStream(ByteStreams.nullOutputStream());
    LongAdder totalByteCount = new LongAdder();
    LongAdder expectedSize = new LongAdder();
    registryClient
        .pullBlob(
            realDigest,
            size -> {
              Assert.assertEquals(0, expectedSize.sum());
              expectedSize.add(size);
            },
            new TestBlobProgressListener(totalByteCount::add))
        .writeTo(layerOutputStream);
    Assert.assertTrue(expectedSize.sum() > 0);
    Assert.assertEquals(expectedSize.sum(), totalByteCount.sum());

    Assert.assertEquals(realDigest, layerOutputStream.toBlobDescriptor().getDigest());
  }

  @Test
  public void testPull_unknownBlob() throws IOException, DigestException, InterruptedException {
    localRegistry.pullAndPushToLocal("busybox", "busybox");
    DescriptorDigest nonexistentDigest =
        DescriptorDigest.fromHash(
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");

    RegistryClient registryClient =
        RegistryClient.factory(EVENT_DISPATCHER, "localhost:5000", "busybox")
            .setAllowInsecureRegistries(true)
            .newRegistryClient();

    try {
      registryClient
          .pullBlob(nonexistentDigest, ignored -> {}, new TestBlobProgressListener(ignored -> {}))
          .writeTo(ByteStreams.nullOutputStream());
      Assert.fail("Trying to pull nonexistent blob should have errored");

    } catch (IOException ex) {
      if (!(ex.getCause() instanceof RegistryErrorException)) {
        throw ex;
      }
      Assert.assertThat(
          ex.getMessage(),
          CoreMatchers.containsString(
              "pull BLOB for localhost:5000/busybox with digest " + nonexistentDigest));
    }
  }
}
