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

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

public class AsyncDependencies {

  public static AsyncDependencies using(ListeningExecutorService listeningExecutorService) {
    return new AsyncDependencies(listeningExecutorService);
  }

  private final ListeningExecutorService listeningExecutorService;
  private final List<ListenableFuture<?>> futures = new ArrayList<>();

  private AsyncDependencies(ListeningExecutorService listeningExecutorService) {
    this.listeningExecutorService = listeningExecutorService;
  }

  public AsyncDependencies addStep(AsyncStep<?> asyncStep) {
    futures.add(asyncStep.getFuture());
    return this;
  }

  public AsyncDependencies addStepOfStep(AsyncStep<? extends AsyncStep<?>> asyncStepOfAsyncStep)
      throws ExecutionException {
    return addStep(NonBlockingSteps.get(asyncStepOfAsyncStep));
  }

  public AsyncDependencies addStepOfStepOfStep(
      AsyncStep<? extends AsyncStep<? extends AsyncStep<?>>> asyncStepOfAsyncStepOfAsyncStep)
      throws ExecutionException {
    return addStepOfStep(NonBlockingSteps.get(asyncStepOfAsyncStepOfAsyncStep));
  }

  public AsyncDependencies addListOfSteps(
      AsyncStep<? extends List<? extends AsyncStep<?>>> asyncStepOfAsyncSteps)
      throws ExecutionException {
    for (AsyncStep<?> asyncStep : NonBlockingSteps.get(asyncStepOfAsyncSteps)) {
      addStep(asyncStep);
    }
    return this;
  }

  public AsyncDependencies addListOfStepOfSteps(
      AsyncStep<? extends ImmutableList<? extends AsyncStep<? extends AsyncStep<?>>>>
          asyncStepOfAsyncStepsOfAsyncStep)
      throws ExecutionException {
    for (AsyncStep<? extends AsyncStep<?>> asyncStepOfAsyncStep :
        NonBlockingSteps.get(asyncStepOfAsyncStepsOfAsyncStep)) {
      addStepOfStep(asyncStepOfAsyncStep);
    }
    return this;
  }

  public <C> ListenableFuture<C> whenAllSucceed(Callable<C> combiner) {
    return Futures.whenAllSucceed(futures).call(combiner, listeningExecutorService);
  }
}
