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
import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.io.OutputStream;
import java.security.DigestException;
import java.security.NoSuchAlgorithmException;

/**
 * An empty {@link BlobStream}. This is used, for e.g., to send an HTTP request with an empty body
 * without having to pass {@code null} for the body {@link BlobStream}.
 */
class EmptyBlobStream implements BlobStream {

  private BlobDescriptor writtenBlobDescriptor;

  @Override
  public void writeTo(OutputStream outputStream)
      throws IOException, NoSuchAlgorithmException, DigestException {
    writtenBlobDescriptor =
        new CountingDigestOutputStream(ByteStreams.nullOutputStream()).toBlobDescriptor();
  }

  @Override
  public BlobDescriptor getWrittenBlobDescriptor() {
    return writtenBlobDescriptor;
  }
}
