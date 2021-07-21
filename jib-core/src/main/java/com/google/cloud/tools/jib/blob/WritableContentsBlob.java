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
import com.google.cloud.tools.jib.hash.WritableContents;
import java.io.IOException;
import java.io.OutputStream;

/** A {@link Blob} that holds {@link WritableContents}. */
class WritableContentsBlob implements Blob {

  private final WritableContents writableContents;

  WritableContentsBlob(WritableContents writableContents) {
    this.writableContents = writableContents;
  }

  @Override
  public BlobDescriptor writeTo(OutputStream outputStream) throws IOException {
    return Digests.computeDigest(writableContents, outputStream);
  }

  // in general it is since the underlying data is accessed in the lambda
  @Override
  public boolean isRetryable() {
    return true;
  }
}
