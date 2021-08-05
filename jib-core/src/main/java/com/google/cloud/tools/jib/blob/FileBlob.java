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
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** A {@link Blob} that holds a {@link Path}. */
class FileBlob implements Blob {

  private final Path file;

  FileBlob(Path file) {
    this.file = file;
  }

  @Override
  public BlobDescriptor writeTo(OutputStream outputStream) throws IOException {
    try (InputStream fileIn = new BufferedInputStream(Files.newInputStream(file))) {
      return Digests.computeDigest(fileIn, outputStream);
    }
  }

  @Override
  public boolean isRetryable() {
    return true;
  }
}
