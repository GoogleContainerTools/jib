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

import com.google.cloud.tools.jib.hash.Digests;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** A {@link Blob} that holds an {@link InputStream}. */
class InputStreamBlob implements Blob {

  private final InputStream inputStream;

  /** Indicates if the {@link Blob} has already been written or not. */
  private boolean isWritten = false;

  InputStreamBlob(InputStream inputStream) {
    this.inputStream = inputStream;
  }

  @Override
  public BlobDescriptor writeTo(OutputStream outputStream) throws IOException {
    // Cannot rewrite.
    if (isWritten) {
      throw new IllegalStateException("Cannot rewrite Blob backed by an InputStream");
    }
    try (InputStream inputStream = this.inputStream) {
      return Digests.computeDigest(inputStream, outputStream);

    } finally {
      isWritten = true;
    }
  }

  @Override
  public boolean isRetryable() {
    return false;
  }
}
