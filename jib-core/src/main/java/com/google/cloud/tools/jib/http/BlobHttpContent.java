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

package com.google.cloud.tools.jib.http;

import com.google.api.client.http.HttpContent;
import com.google.cloud.tools.jib.blob.Blob;
import java.io.IOException;
import java.io.OutputStream;
import java.util.function.Consumer;

/** {@link Blob}-backed {@link HttpContent}. */
public class BlobHttpContent implements HttpContent {

  private final Blob blob;
  private final String contentType;
  private final Consumer<Long> writtenByteCountListener;

  public BlobHttpContent(Blob blob, String contentType) {
    this(blob, contentType, ignored -> {});
  }

  /**
   * Create a new BlobHttpClient.
   *
   * @param blob a blob to wrap
   * @param contentType the http contentType descriptor
   * @param writtenByteCountListener to listen for written byte feedback
   */
  public BlobHttpContent(Blob blob, String contentType, Consumer<Long> writtenByteCountListener) {
    this.blob = blob;
    this.contentType = contentType;
    this.writtenByteCountListener = writtenByteCountListener;
  }

  @Override
  public long getLength() {
    // Returns negative value for unknown length.
    return -1;
  }

  @Override
  public String getType() {
    return contentType;
  }

  @Override
  public boolean retrySupported() {
    return blob.isRetryable();
  }

  @Override
  public void writeTo(OutputStream outputStream) throws IOException {
    blob.writeTo(new NotifyingOutputStream(outputStream, writtenByteCountListener));
    outputStream.flush();
  }
}
