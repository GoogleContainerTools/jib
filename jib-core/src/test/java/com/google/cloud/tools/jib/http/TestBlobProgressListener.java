/*
 * Copyright 2018 Google LLC.
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

import java.time.Duration;
import java.util.function.Consumer;

/**
 * Test implementation of {@link BlobProgressListener} that always forwards to a {@link Consumer
 * <Long>}.
 */
public class TestBlobProgressListener implements BlobProgressListener, Consumer<Long> {

  private final Consumer<Long> byteCountConsumer;

  public TestBlobProgressListener(Consumer<Long> byteCountConsumer) {
    this.byteCountConsumer = byteCountConsumer;
  }

  @Override
  public void handleByteCount(long byteCount) {
    byteCountConsumer.accept(byteCount);
  }

  @Override
  public Duration getDelayBetweenCallbacks() {
    return Duration.ofSeconds(-1);
  }

  @Override
  public void accept(Long byteCount) {
    handleByteCount(byteCount);
  }
}
