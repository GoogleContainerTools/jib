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

import java.io.IOException;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Generates SHA-256 hashes in hexadecimal. Can act as an {@link OutputStream} that captures the
 * bytes to hash.
 */
public class ByteHashBuilder extends OutputStream {

  private static final String SHA_256_ALGORITHM = "SHA-256";

  private final MessageDigest messageDigest = MessageDigest.getInstance(SHA_256_ALGORITHM);

  /** Keeps track of the total number of bytes appended. */
  private long totalBytes = 0;

  public ByteHashBuilder() throws NoSuchAlgorithmException {}

  /** Builds the hash in hexadecimal format. */
  public String toHash() {
    byte[] hashedBytes = messageDigest.digest();

    // Encodes each hashed byte into 2-character hexadecimal representation.
    StringBuilder stringBuilder = new StringBuilder(2 * hashedBytes.length);
    for (byte b : hashedBytes) {
      stringBuilder.append(String.format("%02x", b));
    }
    return stringBuilder.toString();
  }

  /** @return the total number of bytes that were hashed */
  public long getTotalBytes() {
    return totalBytes;
  }

  @Override
  public void write(byte[] data, int offset, int length) throws IOException {
    try {
      append(data, offset, length);
    } catch (NoSuchAlgorithmException ex) {
      throw new IOException(ex);
    }
  }

  @Override
  public void write(byte data[]) throws IOException {
    try {
      append(data, 0, data.length);
    } catch (NoSuchAlgorithmException ex) {
      throw new IOException(ex);
    }
  }

  @Override
  public void write(int singleByte) throws IOException {
    // Only write the 8 low-order bits.
    byte[] singleByteArray = {(byte) (singleByte & 0xff)};
    try {
      append(singleByteArray, 0, 1);
    } catch (NoSuchAlgorithmException ex) {
      throw new IOException(ex);
    }
  }

  /** Appends data to the bytes the hash. */
  private void append(byte[] data, int offset, int length) throws NoSuchAlgorithmException {
    messageDigest.update(data, offset, length);
    totalBytes += length;
  }
}
