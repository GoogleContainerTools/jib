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

import com.google.cloud.tools.jib.builder.ProgressEventDispatcher;
import com.google.cloud.tools.jib.event.progress.ThrottledAccumulatingConsumer;
import com.google.common.base.Preconditions;
import java.io.Closeable;
import javax.annotation.Nullable;

/**
 * Contains a {@link ProgressEventDispatcher} and throttles dispatching progress events with the
 * default delay used by {@link ThrottledConsumer}. This class is mutable and should only be used
 * within a local context.
 *
 * <p>This class is necessary because the total BLOb size (allocation units) is not known until the
 * response headers are received, only after which can the {@link ProgressEventDispatcher} be
 * created.
 */
class ThrottledProgressEventDispatcherWrapper implements Closeable {

  private final ProgressEventDispatcher.Factory progressEventDispatcherFactory;
  private final String description;
  @Nullable private ProgressEventDispatcher progressEventDispatcher;
  @Nullable private ThrottledAccumulatingConsumer throttledDispatcher;

  ThrottledProgressEventDispatcherWrapper(
      ProgressEventDispatcher.Factory progressEventDispatcherFactory, String description) {
    this.progressEventDispatcherFactory = progressEventDispatcherFactory;
    this.description = description;
  }

  public void dispatchProgress(Long progressUnits) {
    Preconditions.checkNotNull(throttledDispatcher);
    throttledDispatcher.accept(progressUnits);
  }

  @Override
  public void close() {
    Preconditions.checkNotNull(progressEventDispatcher);
    Preconditions.checkNotNull(throttledDispatcher);
    throttledDispatcher.close();
    progressEventDispatcher.close();
  }

  void setProgressTarget(long allocationUnits) {
    Preconditions.checkState(progressEventDispatcher == null);
    progressEventDispatcher = progressEventDispatcherFactory.create(description, allocationUnits);
    throttledDispatcher =
        new ThrottledAccumulatingConsumer(progressEventDispatcher::dispatchProgress);
  }
}
