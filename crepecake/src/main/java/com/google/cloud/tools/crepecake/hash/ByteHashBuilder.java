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

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Generates SHA-256 hashes in hexadecimal. */
public class ByteHashBuilder {

  private static final String SHA_256_ALGORITHM = "SHA-256";

  private final MessageDigest messageDigest = MessageDigest.getInstance(SHA_256_ALGORITHM);

  public ByteHashBuilder() throws NoSuchAlgorithmException {}

  /** Appends data to the bytes the hash. */
  public void append(byte[] data, int offset, int length) throws NoSuchAlgorithmException {
    messageDigest.update(data, offset, length);
  }

  /** Builds the hash in hexadecimal format. */
  public String buildHash() {
    byte[] hashedBytes = messageDigest.digest();

    // Encodes each hashed byte into 2-character hexadecimal representation.
    StringBuilder stringBuilder = new StringBuilder(2 * hashedBytes.length);
    for (byte b : hashedBytes) {
      stringBuilder.append(String.format("%02x", b));
    }
    return stringBuilder.toString();
  }
}
