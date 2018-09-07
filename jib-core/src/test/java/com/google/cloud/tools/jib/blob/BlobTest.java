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

import com.google.cloud.tools.jib.hash.CountingDigestOutputStream;
import com.google.cloud.tools.jib.image.DescriptorDigest;
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
import java.nio.file.StandardOpenOption;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

/** Tests for {@link Blob}. */
public class BlobTest {

  @Test
  public void testFromInputStream() throws IOException {
    String expected = "crepecake";
    InputStream inputStream = new ByteArrayInputStream(expected.getBytes(StandardCharsets.UTF_8));
    verifyBlobWriteTo(expected, Blobs.from(inputStream));
  }

  @Test
  public void testFromFile() throws IOException, URISyntaxException {
    Path fileA = Paths.get(Resources.getResource("fileA").toURI());
    String expected = new String(Files.readAllBytes(fileA), StandardCharsets.UTF_8);
    verifyBlobWriteTo(expected, Blobs.from(fileA));
  }

  @Test
  public void testFromString() throws IOException {
    String expected = "crepecake";
    verifyBlobWriteTo(expected, Blobs.from(expected));
  }

  @Test
  public void testFromBlobWriter() throws IOException {
    String expected = "crepecake";

    BlobWriter writer =
        outputStream -> outputStream.write(expected.getBytes(StandardCharsets.UTF_8));

    verifyBlobWriteTo(expected, Blobs.from(writer));
  }

  @Test
  public void testWriteToFileWithLock_newFile() throws IOException {
    String expected = "crepecake";
    Path blobFile = Files.createTempFile("blob", "bin");
    Assert.assertTrue(Files.deleteIfExists(blobFile)); // ensure it doesn't exist

    Blobs.writeToFileWithLock(Blobs.from(expected), blobFile);

    Assert.assertTrue(Files.exists(blobFile));
    verifyBlobWriteTo(expected, Blobs.from(blobFile));
  }

  @Test
  public void testWriteToFileWithLock_existingFile() throws IOException {
    Path blobFile = Files.createTempFile("blob", "bin");
    // write out more bytes to ensure properly truncated
    byte[] dataBytes = new byte[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
    Files.write(blobFile, dataBytes, StandardOpenOption.WRITE);
    Assert.assertTrue(Files.exists(blobFile));
    Assert.assertEquals(10, Files.size(blobFile));

    String expected = "crepecake";
    Blobs.writeToFileWithLock(Blobs.from(expected), blobFile);

    Assert.assertTrue(Files.exists(blobFile));
    Assert.assertEquals(9, Files.size(blobFile));
    verifyBlobWriteTo(expected, Blobs.from(blobFile));
  }

  /** Checks that the {@link Blob} streams the expected string. */
  private void verifyBlobWriteTo(String expected, Blob blob) throws IOException {
    OutputStream outputStream = new ByteArrayOutputStream();
    BlobDescriptor blobDescriptor = blob.writeTo(outputStream);

    String output = outputStream.toString();
    Assert.assertEquals(expected, output);

    byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
    Assert.assertEquals(expectedBytes.length, blobDescriptor.getSize());

    CountingDigestOutputStream countingDigestOutputStream =
        new CountingDigestOutputStream(Mockito.mock(OutputStream.class));
    countingDigestOutputStream.write(expectedBytes);
    DescriptorDigest expectedDigest = countingDigestOutputStream.toBlobDescriptor().getDigest();
    Assert.assertEquals(expectedDigest, blobDescriptor.getDigest());
  }
}
