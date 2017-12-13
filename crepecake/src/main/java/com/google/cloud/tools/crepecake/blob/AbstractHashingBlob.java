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
import java.io.IOException;
import java.io.OutputStream;
import java.security.DigestException;

/** Abstract parent for {@link Blob}s that hash the BLOB when written out. */
abstract class AbstractHashingBlob implements Blob {

  /**
   * Writes to a {@link CountingDigestOutputStream}.
   *
   * @param outputStream the {@link CountingDigestOutputStream} to write to
   */
  abstract void writeToWithHashing(CountingDigestOutputStream outputStream) throws IOException;

  @Override
  public final BlobDescriptor writeTo(OutputStream outputStream) throws IOException {
    CountingDigestOutputStream hashingOutputStream = new CountingDigestOutputStream(outputStream);

    writeToWithHashing(hashingOutputStream);
    hashingOutputStream.flush();

    try {
      return hashingOutputStream.toBlobDescriptor();
    } catch (DigestException ex) {
      throw new IOException("BLOB hashing failed: " + ex.getMessage(), ex);
    }
  }
}
