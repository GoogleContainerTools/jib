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

import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Keeps track of the progress for {@link Allocation}s as well as their order in which they appear.
 *
 * <p>This implementation is thread-safe.
 */
class AllocationCompletionTracker {

  /**
   * Holds the progress units completed along with a creation order. This is used as the value of
   * the {@link #completionMap}.
   */
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

  /**
   * Maps from {@link Allocation} to the number of progress units completed in that {@link
   * Allocation}, while keeping track of the insertion order of the {@link Allocation}s with {@link
   * InsertionOrderUnits}.
   */
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
  boolean putIfAbsent(Allocation allocation) {
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
  void updateProgress(Allocation allocation, long units) {
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
   * Gets a list of the unfinished {@link Allocation}s in the order in which those {@link
   * Allocation}s were encountered.
   *
   * @return a list of unfinished {@link Allocation}s
   */
  List<Allocation> getUnfinishedAllocations() {
    Queue<InsertionOrderUnits> unfinishedInsertionOrderUnits = new PriorityQueue<>();

    for (InsertionOrderUnits insertionOrderUnits : completionMap.values()) {
      if (insertionOrderUnits.units.get() == insertionOrderUnits.allocation.getAllocationUnits()) {
        unfinishedInsertionOrderUnits.add(insertionOrderUnits);
      }
    }

    List<Allocation> unfinishedAllocations = new ArrayList<>();
    for (InsertionOrderUnits insertionOrderUnits : unfinishedInsertionOrderUnits) {
      unfinishedAllocations.add(insertionOrderUnits.allocation);
    }
    return unfinishedAllocations;
  }

  /**
   * Helper method for {@link #updateProgress(Allocation, long)}. This method is <em>not</em>
   * thread-safe for the {@code insertionOrderUnits} and should be called atomically relative to the
   * {@code insertionOrderUnits}.
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
