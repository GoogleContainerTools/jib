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
import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.function.Consumer;

/**
 * Handles {@link ProgressEvent}s by accumulating an overall progress and keeping track of which
 * {@link Allocation}s are complete.
 *
 * <p>This implementation is thread-safe.
 */
class ProgressEventHandler implements Consumer<ProgressEvent> {

  /**
   * Maps from {@link Allocation} to the number of progress units completed in that {@link
   * Allocation}, while keeping track of the insertion order of the {@link Allocation}s.
   */
  private static class AllocationCompletionMap {

    /** Holds the progress units completed along with a creation order. */
    private static class InsertionOrderUnits implements Comparable<InsertionOrderUnits> {

      /** Monotonically-increasing source for {@link #index}. */
      private static final AtomicInteger currentIndex = new AtomicInteger();

      /** The creation order that monotonically increases. */
      private final int index = currentIndex.getAndIncrement();

      /**
       * Progress units completed. This can be shared across multiple threads and should be updated
       * atomically.
       */
      private final AtomicLong units = new AtomicLong();

      /** The {@link Allocation} this is for. */
      private final Allocation allocation;

      private InsertionOrderUnits(Allocation allocation) {
        this.allocation = allocation;
      }

      @Override
      public int compareTo(InsertionOrderUnits otherInsertionOrderUnits) {
        return index - otherInsertionOrderUnits.index;
      }
    }

    private final ConcurrentHashMap<Allocation, InsertionOrderUnits> completionMap =
        new ConcurrentHashMap<>();

    /**
     * Puts an entry for {@code allocation} in the map if not present, with progress initialized to
     * {@code 0}.
     *
     * @param allocation the {@link Allocation}
     * @return {@code true} if the map was updated; {@code false} if {@code allocation} was already
     *     present
     */
    private boolean putIfAbsent(Allocation allocation) {
      // Note: Could have false positives.
      boolean alreadyPresent = completionMap.containsKey(allocation);
      completionMap.computeIfAbsent(allocation, InsertionOrderUnits::new);
      return alreadyPresent;
    }

    /**
     * Updates the progress for {@link Allocation} atomically relative to the {@code allocation}.
     *
     * @param allocation the {@link Allocation} to update progress for
     * @param units the units of progress
     */
    private void updateProgress(Allocation allocation, long units) {
      completionMap.compute(
          allocation,
          (ignored, insertionOrderUnits) -> {
            if (insertionOrderUnits == null) {
              insertionOrderUnits = new InsertionOrderUnits(allocation);
            }

            updateProgress(insertionOrderUnits, units);

            return insertionOrderUnits;
          });
    }

    /**
     * Helper method for {@link #updateProgress(Allocation, long)}. This method is <em>not</em>
     * thread-safe for the {@code insertionOrderUnits} and should be called atomically relative to
     * the {@code insertionOrderUnits}.
     *
     * @param insertionOrderUnits the {@link InsertionOrderUnits} to update progress for
     * @param units the units of progress
     */
    private void updateProgress(InsertionOrderUnits insertionOrderUnits, long units) {
      Allocation allocation = insertionOrderUnits.allocation;

      long newUnits = insertionOrderUnits.units.addAndGet(units);
      if (newUnits > allocation.getAllocationUnits()) {
        throw new IllegalStateException(
            "Progress exceeds max for '"
                + allocation.getDescription()
                + "': "
                + newUnits
                + " > "
                + allocation.getAllocationUnits());
      }

      // Updates the parent allocations if this allocation completed.
      if (newUnits == allocation.getAllocationUnits()) {
        allocation
            .getParent()
            .ifPresent(
                parentAllocation ->
                    updateProgress(
                        Preconditions.checkNotNull(completionMap.get(parentAllocation)), 1L));
      }
    }
  }

  /** Keeps track of the progress for each {@link Allocation} encountered. */
  private final AllocationCompletionMap completionMap = new AllocationCompletionMap();

  /** Accumulates an overall progress, with {@code 1.0} indicating full completion. */
  private final DoubleAdder progress = new DoubleAdder();

  /**
   * A callback to notify that {@link #progress} or {@link #completionMap} could have changed. Note
   * that every change will be reported, but there could be false positives.
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
      completionMap.putIfAbsent(allocation);
      return;
    }

    progress.add(progressUnits * allocationFraction / allocationUnits);

    completionMap.updateProgress(allocation, progressUnits);

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
    Queue<AllocationCompletionMap.InsertionOrderUnits> unfinishedInsertionOrderUnits =
        new PriorityQueue<>();

    for (AllocationCompletionMap.InsertionOrderUnits insertionOrderUnits :
        completionMap.completionMap.values()) {
      if (insertionOrderUnits.units.get() == insertionOrderUnits.allocation.getAllocationUnits()) {
        unfinishedInsertionOrderUnits.add(insertionOrderUnits);
      }
    }

    List<Allocation> unfinishedAllocations = new ArrayList<>();
    for (AllocationCompletionMap.InsertionOrderUnits insertionOrderUnits :
        unfinishedInsertionOrderUnits) {
      unfinishedAllocations.add(insertionOrderUnits.allocation);
    }
    return unfinishedAllocations;
  }
}
