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

import com.google.cloud.tools.jib.api.DescriptorDigest;
import com.google.cloud.tools.jib.api.RegistryException;
import com.google.cloud.tools.jib.blob.Blob;
import com.google.cloud.tools.jib.event.EventHandlers;
import com.google.cloud.tools.jib.http.FailoverHttpClient;
import com.google.cloud.tools.jib.image.json.V22ManifestTemplate;
import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.security.DigestException;
import java.util.concurrent.atomic.LongAdder;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.Assert;
import org.junit.jupiter.api.Test;

/** Integration tests for {@link BlobPuller}. */
public class BlobPullerIntegrationTest {

  private final FailoverHttpClient httpClient = new FailoverHttpClient(true, false, ignored -> {});

  @Test
  public void testPull() throws IOException, RegistryException {
    RegistryClient registryClient =
        RegistryClient.factory(EventHandlers.NONE, "gcr.io", "distroless/base", httpClient)
            .newRegistryClient();
    V22ManifestTemplate manifestTemplate =
        registryClient
            .pullManifest(
                ManifestPullerIntegrationTest.KNOWN_MANIFEST_V22_SHA, V22ManifestTemplate.class)
            .getManifest();

    DescriptorDigest realDigest = manifestTemplate.getLayers().get(0).getDigest();

    // Pulls a layer BLOB of the distroless/base image.
    LongAdder totalByteCount = new LongAdder();
    LongAdder expectedSize = new LongAdder();
    Blob pulledBlob =
        registryClient.pullBlob(
            realDigest,
            size -> {
              Assert.assertEquals(0, expectedSize.sum());
              expectedSize.add(size);
            },
            totalByteCount::add);
    Assert.assertEquals(realDigest, pulledBlob.writeTo(ByteStreams.nullOutputStream()).getDigest());
    Assert.assertTrue(expectedSize.sum() > 0);
    Assert.assertEquals(expectedSize.sum(), totalByteCount.sum());
  }

  @Test
  public void testPull_unknownBlob() throws IOException, DigestException {
    DescriptorDigest nonexistentDigest =
        DescriptorDigest.fromHash(
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");

    RegistryClient registryClient =
        RegistryClient.factory(EventHandlers.NONE, "gcr.io", "distroless/base", httpClient)
            .newRegistryClient();

    try {
      registryClient
          .pullBlob(nonexistentDigest, ignored -> {}, ignored -> {})
          .writeTo(ByteStreams.nullOutputStream());
      Assert.fail("Trying to pull nonexistent blob should have errored");

    } catch (IOException ex) {
      if (!(ex.getCause() instanceof RegistryErrorException)) {
        throw ex;
      }
      MatcherAssert.assertThat(
          ex.getMessage(),
          CoreMatchers.containsString(
              "pull BLOB for gcr.io/distroless/base with digest " + nonexistentDigest));
    }
  }
}
