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

package com.google.cloud.tools.jib.event.progress;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Testing infrastructure for running code across multiple threads. */
class MultithreadedExecutor {

  private static final Duration MULTITHREADED_TEST_TIMEOUT = Duration.ofSeconds(1);

  private final ExecutorService executorService = Executors.newFixedThreadPool(20);

  <E> List<E> invokeAll(List<Callable<E>> callables)
      throws InterruptedException, ExecutionException {
    List<Future<E>> futures =
        executorService.invokeAll(
            callables, MULTITHREADED_TEST_TIMEOUT.getSeconds(), TimeUnit.SECONDS);

    List<E> returnValues = new ArrayList<>();
    for (Future<E> future : futures) {
      returnValues.add(future.get());
    }

    return returnValues;
  }
}
