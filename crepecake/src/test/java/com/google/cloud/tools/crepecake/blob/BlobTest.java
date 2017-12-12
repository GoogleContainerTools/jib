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

package com.google.cloud.tools.crepecake.blob;

import com.google.cloud.tools.crepecake.hash.CountingDigestOutputStream;
import com.google.cloud.tools.crepecake.image.DescriptorDigest;
import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.security.DigestException;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

/** Tests for {@link Blob}. */
public class BlobTest {

  @Test
  public void testEmpty() throws IOException, DigestException {
    verifyBlobStreamWriteTo("", Blobs.empty());
  }

  @Test
  public void testFromInputStream() throws IOException, DigestException, URISyntaxException {
    String expected = "crepecake";
    InputStream inputStream = new ByteArrayInputStream(expected.getBytes(Charsets.UTF_8));
    verifyBlobStreamWriteTo(expected, Blobs.from(inputStream));
  }

  @Test
  public void testFromFile() throws IOException, DigestException, URISyntaxException {
    File fileA = new File(Resources.getResource("fileA").toURI());
    String expected = new String(Files.readAllBytes(fileA.toPath()), Charsets.UTF_8);
    verifyBlobStreamWriteTo(expected, Blobs.from(fileA));
  }

  @Test
  public void testFromString_hashing() throws IOException, DigestException {
    String expected = "crepecake";
    verifyBlobStreamWriteTo(expected, Blobs.from(expected, true));
  }

  @Test
  public void testFromString_noHashing() throws IOException, DigestException {
    String expected = "crepecake";
    verifyBlobStreamWriteTo(expected, Blobs.from(expected, false));
  }

  @Test
  public void testFromBlobWriter() throws IOException, DigestException {
    String expected = "crepecake";

    BlobWriter writer =
        outputStream -> {
          outputStream.write(expected.getBytes(Charsets.UTF_8));
        };

    verifyBlobStreamWriteTo(expected, Blobs.from(writer));
  }

  /** Checks that the {@link Blob} streams the expected string. */
  private void verifyBlobStreamWriteTo(String expected, Blob blob)
      throws IOException, DigestException {
    OutputStream outputStream = new ByteArrayOutputStream();
    BlobDescriptor blobDescriptor = blob.writeTo(outputStream);

    String output = outputStream.toString();
    Assert.assertEquals(expected, output);

    byte[] expectedBytes = expected.getBytes(Charsets.UTF_8);
    Assert.assertEquals(expectedBytes.length, blobDescriptor.getSize());

    if (blobDescriptor.hasDigest()) {
      CountingDigestOutputStream countingDigestOutputStream =
          new CountingDigestOutputStream(Mockito.mock(OutputStream.class));
      countingDigestOutputStream.write(expectedBytes);
      DescriptorDigest expectedDigest = countingDigestOutputStream.toBlobDescriptor().getDigest();
      Assert.assertEquals(expectedDigest, blobDescriptor.getDigest());
    }
  }
}
