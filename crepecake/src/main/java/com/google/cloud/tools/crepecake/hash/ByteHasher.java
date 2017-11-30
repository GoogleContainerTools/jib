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

/** Static utility class for generating SHA-256 hashes in hexadecimal. */
public class ByteHasher {

  private static final String SHA_256_ALGORITHM = "SHA-256";

  public static String hash(byte[] data) throws NoSuchAlgorithmException {
    // Hashes the bytes with SHA-256 algorithm.
    MessageDigest messageDigest = MessageDigest.getInstance(SHA_256_ALGORITHM);
    messageDigest.update(data);
    byte[] hashedBytes = messageDigest.digest();

    // Encodes each hashed byte into 2-character hexadecimal representation.
    StringBuffer stringBuffer = new StringBuffer(2 * hashedBytes.length);
    for (byte b : hashedBytes) {
      stringBuffer.append(String.format("%02x", b));
    }
    return stringBuffer.toString();
  }

  private ByteHasher() {}
}
