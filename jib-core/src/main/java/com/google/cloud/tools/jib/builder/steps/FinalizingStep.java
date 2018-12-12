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

package com.google.cloud.tools.jib.builder.steps;

import com.google.cloud.tools.jib.async.AsyncDependencies;
import com.google.cloud.tools.jib.async.AsyncStep;
import com.google.cloud.tools.jib.async.NonBlockingSteps;
import com.google.cloud.tools.jib.configuration.BuildConfiguration;
import com.google.cloud.tools.jib.event.events.LogEvent;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/** Logs the message before finalizing an image build. */
class FinalizingStep implements AsyncStep<Void>, Callable<Void> {

  private final BuildConfiguration buildConfiguration;
  private final List<AsyncStep<? extends ImmutableList<? extends AsyncStep<?>>>>
      futureDependencyLists;

  private final ListeningExecutorService listeningExecutorService;
  private final ListenableFuture<Void> listenableFuture;

  /**
   * @param wrappedDependencyLists {@link AsyncStep}s that must be unwrapped for additional {@link
   *     AsyncStep}s to depend on
   * @param dependencyList list of additional {@link AsyncStep}s to depend on directly
   */
  FinalizingStep(
      ListeningExecutorService listeningExecutorService,
      BuildConfiguration buildConfiguration,
      List<AsyncStep<? extends ImmutableList<? extends AsyncStep<?>>>> wrappedDependencyLists,
      List<? extends AsyncStep<?>> dependencyList) {
    this.listeningExecutorService = listeningExecutorService;
    this.buildConfiguration = buildConfiguration;
    this.futureDependencyLists = wrappedDependencyLists;

    listenableFuture =
        AsyncDependencies.using(listeningExecutorService)
            .addSteps(wrappedDependencyLists)
            .addSteps(dependencyList)
            .whenAllSucceed(this);
  }

  @Override
  public ListenableFuture<Void> getFuture() {
    return listenableFuture;
  }

  @Override
  public Void call() throws ExecutionException {
    AsyncDependencies asyncDependencies = AsyncDependencies.using(listeningExecutorService);

    // Unwrap the wrapped dependencies.
    for (AsyncStep<? extends ImmutableList<? extends AsyncStep<?>>> wrappedDependency :
        futureDependencyLists) {
      asyncDependencies.addSteps(NonBlockingSteps.get(wrappedDependency));
    }

    // This suppresses any exceptions of this future.
    Future<Void> ignored =
        asyncDependencies.whenAllSucceed(
            () -> {
              buildConfiguration.getEventDispatcher().dispatch(LogEvent.lifecycle("Finalizing..."));
              return null;
            });

    return null;
  }
}
