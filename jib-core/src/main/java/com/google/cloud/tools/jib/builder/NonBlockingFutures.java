/*
 * Copyright 2018 Google LLC. All rights reserved.
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

package com.google.cloud.tools.jib.builder;

import com.google.common.util.concurrent.ListenableFuture;
import java.util.concurrent.ExecutionException;

/** Static utility for ensuring {@link ListenableFuture#get} does not block. */
class NonBlockingFutures {

  static <T> T get(ListenableFuture<T> listenableFuture)
      throws ExecutionException, InterruptedException {
    if (!listenableFuture.isDone()) {
      throw new IllegalStateException("get() called before done");
    }
    return listenableFuture.get();
  }

  private NonBlockingFutures() {}
}
