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

import java.io.IOException;
import java.io.OutputStream;
import java.security.DigestException;
import java.security.NoSuchAlgorithmException;
import javax.annotation.Nonnull;

/** A stream for BLOBs. */
public interface BlobStream {

  /**
   * Writes the BLOB to an {@link OutputStream}.
   *
   * @param outputStream the {@link OutputStream} to write to
   */
  void writeTo(OutputStream outputStream)
      throws IOException, NoSuchAlgorithmException, DigestException;

  /**
   * This is only valid <b>after</b> {@code writeTo} is called.
   *
   * @return the {@link BlobDescriptor} of the written BLOB
   * @throws IllegalStateException if {@code writeTo} has not been called
   */
  @Nonnull
  default BlobDescriptor getWrittenBlobDescriptor() throws IllegalStateException {
    throw new IllegalStateException(
        "Written BlobDescriptor not available - must call writeTo first");
  }
}
