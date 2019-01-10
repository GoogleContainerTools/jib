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

package com.google.cloud.tools.jib.async;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Builds a list of dependency {@link ListenableFuture}s to wait on before calling a {@link
 * Callable}.
 */
public class AsyncDependencies {

  /**
   * Initialize with a {@link ListeningExecutorService}.
   *
   * @param listeningExecutorService the {@link ListeningExecutorService}
   * @return a new {@link AsyncDependencies}
   */
  public static AsyncDependencies using(ListeningExecutorService listeningExecutorService) {
    return new AsyncDependencies(listeningExecutorService);
  }

  private final ListeningExecutorService listeningExecutorService;

  /** Stores the list of {@link ListenableFuture}s to wait on. */
  private final List<ListenableFuture<?>> futures = new ArrayList<>();

  private AsyncDependencies(ListeningExecutorService listeningExecutorService) {
    this.listeningExecutorService = listeningExecutorService;
  }

  /**
   * Adds the future of an {@link AsyncStep}.
   *
   * @param asyncStep the {@link AsyncStep}
   * @return this
   */
  public AsyncDependencies addStep(AsyncStep<?> asyncStep) {
    futures.add(asyncStep.getFuture());
    return this;
  }

  /**
   * Adds the futures of a list of {@link AsyncStep}s.
   *
   * @param asyncSteps the {@link AsyncStep}s
   * @return this
   */
  public AsyncDependencies addSteps(List<? extends AsyncStep<?>> asyncSteps) {
    asyncSteps.forEach(this::addStep);
    return this;
  }

  /**
   * Creates the {@link ListenableFuture} which will return the result of calling {@code combiner}
   * when all the added futures succeed.
   *
   * @param combiner the {@link Callable}
   * @param <C> the return type of {@code combiner}
   * @return a {@link ListenableFuture} to handle completion of the call to {@code combiner}
   */
  public <C> ListenableFuture<C> whenAllSucceed(Callable<C> combiner) {
    return Futures.whenAllSucceed(futures).call(combiner, listeningExecutorService);
  }
}
