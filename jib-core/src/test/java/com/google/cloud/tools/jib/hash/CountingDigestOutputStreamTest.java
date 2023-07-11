/*
 * Copyright 2017 Google LLC.
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

package com.google.cloud.tools.jib.hash;

import com.google.cloud.tools.jib.api.DescriptorDigest;
import com.google.cloud.tools.jib.blob.BlobDescriptor;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteStreams;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.DigestException;
import java.util.Map;
import org.junit.Assert;
import org.junit.jupiter.api.Test;

/** Tests for {@link CountingDigestOutputStream}. */
class CountingDigestOutputStreamTest {

  private static final ImmutableMap<String, String> KNOWN_SHA256_HASHES =
      ImmutableMap.of(
          "crepecake",
          "52a9e4d4ba4333ce593707f98564fee1e6d898db0d3602408c0b2a6a424d357c",
          "12345",
          "5994471abb01112afcc18159f6cc74b4f511b99806da59b3caf5a9c173cacfc5",
          "",
          "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");

  @Test
  void test_smokeTest() throws IOException, DigestException {
    for (Map.Entry<String, String> knownHash : KNOWN_SHA256_HASHES.entrySet()) {
      String toHash = knownHash.getKey();
      String expectedHash = knownHash.getValue();

      OutputStream underlyingOutputStream = new ByteArrayOutputStream();
      CountingDigestOutputStream countingDigestOutputStream =
          new CountingDigestOutputStream(underlyingOutputStream);

      byte[] bytesToHash = toHash.getBytes(StandardCharsets.UTF_8);
      InputStream toHashInputStream = new ByteArrayInputStream(bytesToHash);
      ByteStreams.copy(toHashInputStream, countingDigestOutputStream);

      BlobDescriptor blobDescriptor = countingDigestOutputStream.computeDigest();
      Assert.assertEquals(DescriptorDigest.fromHash(expectedHash), blobDescriptor.getDigest());
      Assert.assertEquals(bytesToHash.length, blobDescriptor.getSize());
    }
  }
}
