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

package com.google.cloud.tools.jib.plugins.common.logging;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Executes methods on a single managed thread. Make sure to call {@link
 * #shutDownAndAwaitTermination} when finished.
 *
 * <p>This implementation is thread-safe.
 */
public class SingleThreadedExecutor {

  private static final Duration EXECUTOR_SHUTDOWN_WAIT = Duration.ofSeconds(1);

  private final ExecutorService executorService = Executors.newSingleThreadExecutor();

  /** Shuts down the {@link #executorService} and waits for it to terminate. */
  public void shutDownAndAwaitTermination() {
    executorService.shutdown();

    try {
      if (!executorService.awaitTermination(
          EXECUTOR_SHUTDOWN_WAIT.getSeconds(), TimeUnit.SECONDS)) {
        executorService.shutdownNow();
        if (!executorService.awaitTermination(
            EXECUTOR_SHUTDOWN_WAIT.getSeconds(), TimeUnit.SECONDS)) {
          throw new RuntimeException("Could not shut down AnsiLoggerWithFooter executor");
        }
      }

    } catch (InterruptedException ex) {
      executorService.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }

  /**
   * Executes {@code runnable} on the managed thread.
   *
   * @param runnable the {@link Runnable}
   */
  public void execute(Runnable runnable) {
    executorService.execute(runnable);
  }
}
