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

import java.io.*;
import org.junit.Assert;
import org.junit.Test;

/** Tests for {@link BlobStream} */
public class BlobStreamTest {

  @Test
  public void testEmpty() throws IOException {
    OutputStream outputStream = new ByteArrayOutputStream();

    BlobStream blobStream = new BlobStream();
    blobStream.writeTo(outputStream);

    String output = outputStream.toString();

    Assert.assertEquals("", output);
  }

  @Test
  public void testFromInputStream() throws IOException {
    String expected = "crepecake";

    InputStream inputStream = new ByteArrayInputStream(expected.getBytes());
    OutputStream outputStream = new ByteArrayOutputStream();

    BlobStream blobStream = new BlobStream(inputStream);
    blobStream.writeTo(outputStream);

    String output = outputStream.toString();

    Assert.assertEquals(expected, output);
  }

  @Test
  public void testFromString() throws IOException {
    String expected = "crepecake";

    OutputStream outputStream = new ByteArrayOutputStream();

    BlobStream blobStream = new BlobStream(expected);
    blobStream.writeTo(outputStream);

    String output = outputStream.toString();

    Assert.assertEquals(expected, output);
  }
}
