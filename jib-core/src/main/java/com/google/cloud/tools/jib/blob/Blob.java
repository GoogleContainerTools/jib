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

import java.io.IOException;
import java.io.OutputStream;

/** Holds a BLOB source for writing to an {@link OutputStream}. */
public interface Blob {

  /**
   * Writes the BLOB to an {@link OutputStream}. Does not close the {@code outputStream}.
   *
   * @param outputStream the {@link OutputStream} to write to
   * @return the {@link BlobDescriptor} of the written BLOB
   * @throws IOException if writing the BLOB fails
   */
  BlobDescriptor writeTo(OutputStream outputStream) throws IOException;

  /**
   * Enables to notify if the underlying request can be retried (useful in the context of a
   * retryable HTTP request for ex).
   *
   * @return {@code true} if {@link #writeTo(OutputStream)} can be called multiple times, {@code
   *     false} otherwise.
   */
  boolean isRetryable();
}
