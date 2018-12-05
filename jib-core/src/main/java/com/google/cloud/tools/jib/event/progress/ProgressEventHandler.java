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

import com.google.cloud.tools.jib.event.events.ProgressEvent;
import java.util.List;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.function.Consumer;

/**
 * Handles {@link ProgressEvent}s by accumulating an overall progress and keeping track of which
 * {@link Allocation}s are complete.
 *
 * <p>This implementation is thread-safe.
 */
class ProgressEventHandler implements Consumer<ProgressEvent> {

  /** Keeps track of the progress for each {@link Allocation} encountered. */
  private final AllocationCompletionTracker completionTracker = new AllocationCompletionTracker();

  /** Accumulates an overall progress, with {@code 1.0} indicating full completion. */
  private final DoubleAdder progress = new DoubleAdder();

  /**
   * A callback to notify that {@link #progress} or {@link #completionTracker} could have changed.
   * Note that every change will be reported, but there could be false positives.
   */
  private final Runnable updateNotifier;

  ProgressEventHandler(Runnable updateNotifier) {
    this.updateNotifier = updateNotifier;
  }

  @Override
  public void accept(ProgressEvent progressEvent) {
    Allocation allocation = progressEvent.getAllocation();
    long progressUnits = progressEvent.getUnits();
    double allocationFraction = allocation.getFractionOfRoot();
    long allocationUnits = allocation.getAllocationUnits();

    if (progressUnits == 0) {
      completionTracker.putIfAbsent(allocation);
      return;
    }

    progress.add(progressUnits * allocationFraction / allocationUnits);

    completionTracker.updateProgress(allocation, progressUnits);

    updateNotifier.run();
  }

  /**
   * Gets the overall progress, with {@code 1.0} meaning fully complete.
   *
   * @return the overall progress
   */
  double getProgress() {
    return progress.sum();
  }

  /**
   * Gets a list of the unfinished {@link Allocation}s in the order in which those {@link
   * Allocation}s were encountered.
   *
   * @return a list of unfinished {@link Allocation}s
   */
  // TODO: Change this to do every time update notifier is called, so this is not called many times
  // per update.
  List<Allocation> getUnfinishedAllocations() {
    return completionTracker.getUnfinishedAllocations();
  }
}
