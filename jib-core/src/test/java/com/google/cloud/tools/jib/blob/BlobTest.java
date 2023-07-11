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

package com.google.cloud.tools.jib.blob;

import com.google.cloud.tools.jib.api.DescriptorDigest;
import com.google.cloud.tools.jib.hash.Digests;
import com.google.cloud.tools.jib.hash.WritableContents;
import com.google.common.io.Resources;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Assert;
import org.junit.jupiter.api.Test;

/** Tests for {@link Blob}. */
class BlobTest {

  @Test
  void testFromInputStream() throws IOException {
    String expected = "crepecake";
    InputStream inputStream = new ByteArrayInputStream(expected.getBytes(StandardCharsets.UTF_8));
    verifyBlobWriteTo(expected, Blobs.from(inputStream));
  }

  @Test
  void testFromFile() throws IOException, URISyntaxException {
    Path fileA = Paths.get(Resources.getResource("core/fileA").toURI());
    String expected = new String(Files.readAllBytes(fileA), StandardCharsets.UTF_8);
    verifyBlobWriteTo(expected, Blobs.from(fileA));
  }

  @Test
  void testFromString() throws IOException {
    String expected = "crepecake";
    verifyBlobWriteTo(expected, Blobs.from(expected));
  }

  @Test
  void testFromWritableContents() throws IOException {
    String expected = "crepecake";

    WritableContents writableContents =
        outputStream -> outputStream.write(expected.getBytes(StandardCharsets.UTF_8));

    verifyBlobWriteTo(expected, Blobs.from(writableContents, false));
  }

  /** Checks that the {@link Blob} streams the expected string. */
  private void verifyBlobWriteTo(String expected, Blob blob) throws IOException {
    OutputStream outputStream = new ByteArrayOutputStream();
    BlobDescriptor blobDescriptor = blob.writeTo(outputStream);

    String output = outputStream.toString();
    Assert.assertEquals(expected, output);

    byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
    Assert.assertEquals(expectedBytes.length, blobDescriptor.getSize());

    DescriptorDigest expectedDigest =
        Digests.computeDigest(new ByteArrayInputStream(expectedBytes)).getDigest();
    Assert.assertEquals(expectedDigest, blobDescriptor.getDigest());
  }
}
