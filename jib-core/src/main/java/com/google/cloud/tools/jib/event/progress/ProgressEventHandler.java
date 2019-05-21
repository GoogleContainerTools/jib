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
import com.google.common.collect.ImmutableList;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.function.Consumer;

/**
 * Handles {@link ProgressEvent}s by accumulating an overall progress and keeping track of which
 * {@link Allocation}s are complete.
 *
 * <p>This implementation is thread-safe.
 */
public class ProgressEventHandler implements Consumer<ProgressEvent> {

  /** Contains the accumulated progress and which {@link Allocation}s are not yet complete. */
  public static class Update {

    private final double progress;
    private final ImmutableList<Allocation> unfinishedAllocations;

    private Update(double progress, ImmutableList<Allocation> unfinishedAllocations) {
      this.progress = progress;
      this.unfinishedAllocations = unfinishedAllocations;
    }

    /**
     * Gets the overall progress, with {@code 1.0} meaning fully complete.
     *
     * @return the overall progress
     */
    public double getProgress() {
      return progress;
    }

    /**
     * Gets a list of the unfinished {@link Allocation}s in the order in which those {@link
     * Allocation}s were encountered.
     *
     * @return a list of unfinished {@link Allocation}s
     */
    public ImmutableList<Allocation> getUnfinishedAllocations() {
      return unfinishedAllocations;
    }
  }

  /** Keeps track of the progress for each {@link Allocation} encountered. */
  private final AllocationCompletionTracker completionTracker = new AllocationCompletionTracker();

  /** Accumulates an overall progress, with {@code 1.0} indicating full completion. */
  private final DoubleAdder progress = new DoubleAdder();

  /**
   * A callback to notify that {@link #progress} or {@link #completionTracker} could have changed.
   * Note that every change will be reported (though multiple could be reported together), and there
   * could be false positives.
   */
  private final Consumer<Update> updateNotifier;

  public ProgressEventHandler(Consumer<Update> updateNotifier) {
    this.updateNotifier = updateNotifier;
  }

  @Override
  public void accept(ProgressEvent progressEvent) {
    Allocation allocation = progressEvent.getAllocation();
    long progressUnits = progressEvent.getUnits();
    double allocationFraction = allocation.getFractionOfRoot();

    if (progressUnits != 0) {
      progress.add(progressUnits * allocationFraction);
    }

    if (completionTracker.updateProgress(allocation, progressUnits)) {
      // Note: Could produce false positives.
      updateNotifier.accept(
          new Update(progress.sum(), completionTracker.getUnfinishedAllocations()));
    }
  }
}
