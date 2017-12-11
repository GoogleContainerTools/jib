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

/** A stream for BLOBs. */
public interface BlobStream {

  /**
   * Writes the BLOB to an {@link OutputStream}.
   *
   * @param outputStream the {@link OutputStream} to write to
   * @return the {@link BlobDescriptor} of the written BLOB
   * @throws DigestException if the written BLOB digest failed to generate
   */
  BlobDescriptor writeTo(OutputStream outputStream) throws IOException, DigestException;
}
