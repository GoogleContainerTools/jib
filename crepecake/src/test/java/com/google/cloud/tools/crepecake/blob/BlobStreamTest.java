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

import com.google.cloud.tools.crepecake.hash.ByteHashBuilder;
import com.google.cloud.tools.crepecake.image.DescriptorDigest;
import com.google.cloud.tools.crepecake.image.DigestException;
import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.security.NoSuchAlgorithmException;
import org.junit.Assert;
import org.junit.Test;

/** Tests for {@link BlobStream} */
public class BlobStreamTest {

  @Test
  public void testEmpty() throws IOException, DigestException, NoSuchAlgorithmException {
    verifyBlobStreamWriteTo("", BlobStreams.empty());
  }

  @Test
  public void testFromFile()
      throws IOException, DigestException, NoSuchAlgorithmException, URISyntaxException {
    File fileA = new File(Resources.getResource("fileA").toURI());
    String expected = new String(Files.readAllBytes(fileA.toPath()), Charsets.UTF_8);
    verifyBlobStreamWriteTo(expected, BlobStreams.from(fileA));
  }

  @Test
  public void testFromString() throws IOException, DigestException, NoSuchAlgorithmException {
    String expected = "crepecake";
    verifyBlobStreamWriteTo(expected, BlobStreams.from(expected));
  }

  @Test
  public void testFromBlobWriter() throws NoSuchAlgorithmException, IOException, DigestException {
    String expected = "crepecake";

    BlobStreamWriter writer =
        outputStream -> {
          outputStream.write(expected.getBytes(Charsets.UTF_8));
        };

    verifyBlobStreamWriteTo(expected, BlobStreams.from(writer));
  }

  /** Checks that the {@link BlobStream} streams the expected string. */
  private void verifyBlobStreamWriteTo(String expected, BlobStream blobStream)
      throws NoSuchAlgorithmException, IOException, DigestException {
    OutputStream outputStream = new ByteArrayOutputStream();
    BlobDescriptor blobDescriptor = blobStream.writeTo(outputStream);

    String output = outputStream.toString();
    Assert.assertEquals(expected, output);

    byte[] expectedBytes = expected.getBytes(Charsets.UTF_8);

    ByteHashBuilder byteHashBuilder = new ByteHashBuilder();
    byteHashBuilder.write(expectedBytes);
    DescriptorDigest expectedDigest = DescriptorDigest.fromHash(byteHashBuilder.toHash());

    Assert.assertEquals(expectedBytes.length, blobDescriptor.getSize());
    Assert.assertEquals(expectedDigest, blobDescriptor.getDigest());
  }
}
