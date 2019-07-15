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

package com.google.cloud.tools.jib;

import java.io.Closeable;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.Assert;

/** Testing infrastructure for running code across multiple threads. */
public class MultithreadedExecutor implements Closeable {

  private static final Duration MULTITHREADED_TEST_TIMEOUT = Duration.ofSeconds(3);
  private static final int THREAD_COUNT = 20;

  private final ExecutorService executorService = Executors.newFixedThreadPool(THREAD_COUNT);

  public <E> E invoke(Callable<E> callable) throws ExecutionException, InterruptedException {
    List<E> returnValue = invokeAll(Collections.singletonList(callable));
    return returnValue.get(0);
  }

  public <E> List<E> invokeAll(List<Callable<E>> callables)
      throws InterruptedException, ExecutionException {
    List<Future<E>> futures =
        executorService.invokeAll(
            callables, MULTITHREADED_TEST_TIMEOUT.getSeconds(), TimeUnit.SECONDS);

    List<E> returnValues = new ArrayList<>();
    for (Future<E> future : futures) {
      Assert.assertTrue(future.isDone());
      returnValues.add(future.get());
    }

    return returnValues;
  }

  @Override
  public void close() throws IOException {
    executorService.shutdown();
    try {
      executorService.awaitTermination(MULTITHREADED_TEST_TIMEOUT.getSeconds(), TimeUnit.SECONDS);

    } catch (InterruptedException ex) {
      throw new IOException(ex);
    }
  }
}
