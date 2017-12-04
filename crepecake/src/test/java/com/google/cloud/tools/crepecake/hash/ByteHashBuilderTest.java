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

package com.google.cloud.tools.crepecake.hash;

import com.google.common.base.Charsets;
import com.google.common.io.ByteStreams;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class ByteHashBuilderTest {

  private Map<String, String> knownSha256Hashes;

  @Before
  public void setUp() {
    knownSha256Hashes =
        Collections.unmodifiableMap(
            new HashMap<String, String>() {
              {
                put(
                    "crepecake",
                    "52a9e4d4ba4333ce593707f98564fee1e6d898db0d3602408c0b2a6a424d357c");
                put("12345", "5994471abb01112afcc18159f6cc74b4f511b99806da59b3caf5a9c173cacfc5");
                put("", "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
              }
            });
  }

  @Test
  public void testHash() throws NoSuchAlgorithmException, IOException {
    // Creates a buffer to hold bytes to append to the hash builder.
    byte[] bytesToAppend = new byte[3];

    for (Map.Entry<String, String> knownHash : knownSha256Hashes.entrySet()) {
      String toHash = knownHash.getKey();
      String expectedHash = knownHash.getValue();

      ByteHashBuilder byteHashBuilder = new ByteHashBuilder();

      // Reads the bytes to hash piecewise and appends to the builder.
      byte[] bytesToHash = toHash.getBytes(Charsets.UTF_8);
      ByteArrayInputStream bytesToHashStream = new ByteArrayInputStream(bytesToHash);
      int bytesRead;
      while ((bytesRead = bytesToHashStream.read(bytesToAppend)) != -1) {
        byteHashBuilder.write(bytesToAppend, 0, bytesRead);
      }

      Assert.assertEquals(expectedHash, byteHashBuilder.toHash());
      Assert.assertEquals(bytesToHash.length, byteHashBuilder.getTotalBytes());
    }
  }

  @Test
  public void testHash_asOutputStream() throws NoSuchAlgorithmException, IOException {
    for (Map.Entry<String, String> knownHash : knownSha256Hashes.entrySet()) {
      String toHash = knownHash.getKey();
      String expectedHash = knownHash.getValue();

      ByteHashBuilder byteHashBuilder = new ByteHashBuilder();

      InputStream toHashInputStream = new ByteArrayInputStream(toHash.getBytes(Charsets.UTF_8));
      ByteStreams.copy(toHashInputStream, byteHashBuilder);

      Assert.assertEquals(expectedHash, byteHashBuilder.toHash());
      Assert.assertEquals(toHash.getBytes(Charsets.UTF_8).length, byteHashBuilder.getTotalBytes());
    }
  }
}
