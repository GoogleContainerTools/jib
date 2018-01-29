/*
 * Copyright 2018 Google Inc.
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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;

class NonBlockingListenableFuture<T> implements ListenableFuture<T> {

  private final ListenableFuture<T> listenableFuture;
  private boolean isGetEnabled = false;

  NonBlockingListenableFuture(ListenableFuture<T> listenableFuture) {
    this.listenableFuture = listenableFuture;
  }

  @Override
  public void addListener(@Nonnull Runnable listener, @Nonnull Executor executor) {
    listenableFuture.addListener(
        () -> {
          isGetEnabled = true;
          listener.run();
        },
        executor);
  }

  @Override
  public boolean cancel(boolean mayInterruptIfRunning) {
    return listenableFuture.cancel(mayInterruptIfRunning);
  }

  @Override
  public boolean isCancelled() {
    return listenableFuture.isCancelled();
  }

  @Override
  public boolean isDone() {
    return listenableFuture.isDone();
  }

  @Override
  public T get() throws InterruptedException, ExecutionException {
    if (!isGetEnabled) {
      throw new IllegalStateException("get() is not yet enabled");
    }
    return listenableFuture.get();
  }

  @Override
  public T get(long timeout, @Nonnull TimeUnit unit) {
    throw new UnsupportedOperationException();
  }

  /** Enables blocking {@code #get} for testing. */
  @VisibleForTesting
  void allowBlocking() {
    isGetEnabled = true;
  }
}
