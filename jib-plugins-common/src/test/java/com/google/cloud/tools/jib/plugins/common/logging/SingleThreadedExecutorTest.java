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

import com.google.cloud.tools.jib.MultithreadedExecutor;
import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.junit.Assert;
import org.junit.jupiter.api.Test;

/** Tests for {@link SingleThreadedExecutor}. */
class SingleThreadedExecutorTest {

  private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(3);

  @SuppressWarnings("ThreadPriorityCheck") // use of Thread.yield()
  @Test
  void testExecute_mutualExclusion() throws IOException, ExecutionException, InterruptedException {
    SingleThreadedExecutor singleThreadedExecutor = new SingleThreadedExecutor();
    Lock lock = new ReentrantLock();

    try (MultithreadedExecutor multithreadedExecutor = new MultithreadedExecutor()) {
      multithreadedExecutor.invokeAll(
          Collections.nCopies(
              100,
              () -> {
                singleThreadedExecutor.execute(
                    () -> {
                      Assert.assertTrue(lock.tryLock());
                      Thread.yield();
                      lock.unlock();
                    });
                return null;
              }));
    }

    singleThreadedExecutor.shutDownAndAwaitTermination(SHUTDOWN_TIMEOUT);
  }
}
