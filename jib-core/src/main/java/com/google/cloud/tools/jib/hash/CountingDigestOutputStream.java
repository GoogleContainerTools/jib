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
import com.google.common.base.Verify;
import java.io.IOException;
import java.io.OutputStream;
import java.security.DigestException;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import javax.annotation.Nullable;

/** A {@link DigestOutputStream} that also keeps track of the total number of bytes written. */
public class CountingDigestOutputStream extends DigestOutputStream {

  private static final String SHA_256_ALGORITHM = "SHA-256";

  private long bytesSoFar = 0;

  /** The total number of bytes used to compute a digest. Resets when {@link computeDigest) is called. */
  private long bytesHashed = 0;

  @Nullable private DescriptorDigest descriptorDigest;

  /**
   * Wraps the {@code outputStream}.
   *
   * @param outputStream the {@link OutputStream} to wrap.
   */
  public CountingDigestOutputStream(OutputStream outputStream) {
    super(outputStream, null);
    try {
      setMessageDigest(MessageDigest.getInstance(SHA_256_ALGORITHM));
    } catch (NoSuchAlgorithmException ex) {
      throw new RuntimeException(
          "SHA-256 algorithm implementation not found - might be a broken JVM");
    }
  }

  /**
   * Computes the hash and remembers the size of the bytes written to compute the hash. The buffer
   * resets after this method is called, so this method should only be called once per computation.
   */
  public void computeDigest() {
    try {
      byte[] hashedBytes = digest.digest();

      // Encodes each hashed byte into 2-character hexadecimal representation.
      StringBuilder stringBuilder = new StringBuilder(2 * hashedBytes.length);
      for (byte b : hashedBytes) {
        stringBuilder.append(String.format("%02x", b));
      }
      String hash = stringBuilder.toString();

      bytesHashed = bytesSoFar;
      descriptorDigest = DescriptorDigest.fromHash(hash);
      bytesSoFar = 0;

    } catch (DigestException ex) {
      throw new RuntimeException("SHA-256 algorithm produced invalid hash: " + ex.getMessage(), ex);
    }
  }

  /** @return the number of bytes written and used to compute the most recent digest */
  public long getBytesHahsed() {
    if (descriptorDigest == null) {
      computeDigest();
    }
    return bytesHashed;
  }

  /** @return the most recently computed digest hash */
  public DescriptorDigest getDigest() {
    if (descriptorDigest == null) {
      computeDigest();
    }
    return Verify.verifyNotNull(descriptorDigest);
  }

  @Override
  public void write(byte[] data, int offset, int length) throws IOException {
    super.write(data, offset, length);
    bytesSoFar += length;
  }

  @Override
  public void write(int singleByte) throws IOException {
    super.write(singleByte);
    bytesSoFar++;
  }
}
