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
import java.time.Duration;
import java.util.function.Consumer;

/** {@link Blob}-backed {@link HttpContent}. */
public class BlobHttpContent implements HttpContent {

  private final Blob blob;
  private final String contentType;
  // TODO: Refactor into BlobPushMonitor or something.
  private final Consumer<Long> sentByteCountConsumer;
  private final Duration delayBetweenCallbacks = Duration.ofMillis(100);

  public BlobHttpContent(Blob blob, String contentType, Consumer<Long> sentByteCountConsumer) {
    this.blob = blob;
    this.contentType = contentType;
    this.sentByteCountConsumer = sentByteCountConsumer;
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
    return false;
  }

  @Override
  public void writeTo(OutputStream outputStream) throws IOException {
    ListenableCountingOutputStream listenableCountingOutputStream =
        new ListenableCountingOutputStream(
            outputStream, sentByteCountConsumer, delayBetweenCallbacks);
    blob.writeTo(listenableCountingOutputStream);
    listenableCountingOutputStream.flush();
  }
}
