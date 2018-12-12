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
import com.google.common.collect.ImmutableList;
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
   * Updates the progress for {@link Allocation} atomically relative to the {@code allocation}.
   *
   * <p>For any {@link Allocation}, this method <em>must</em> have been called on all of its parents
   * beforehand.
   *
   * @param allocation the {@link Allocation} to update progress for
   * @param units the units of progress
   * @return {@code true} if the map was updated; {@code false} if {@code allocation} was already
   *     present. Note that this may return {@code true} even if the map was not updated if called
   *     concurrently, but never {@code false} if the map was updated.
   */
  boolean updateProgress(Allocation allocation, long units) {
    if (units == 0L) {
      // Puts the allocation in the map if not present, with progress initialized to 0.
      boolean alreadyPresent = completionMap.containsKey(allocation);
      completionMap.computeIfAbsent(allocation, InsertionOrderUnits::new);
      return !alreadyPresent;
    }

    completionMap.compute(
        allocation,
        (ignored, insertionOrderUnits) -> {
          if (insertionOrderUnits == null) {
            insertionOrderUnits = new InsertionOrderUnits(allocation);
          }

          updateInsertionOrderUnits(insertionOrderUnits, units);

          return insertionOrderUnits;
        });
    return true;
  }

  /**
   * Gets a list of the unfinished {@link Allocation}s in the order in which those {@link
   * Allocation}s were encountered. This can be used to display, for example, currently executing
   * tasks. The order helps to keep the displayed tasks in a deterministic order (new subtasks
   * appear below older ones) and not jumbled together in some random order.
   *
   * @return a list of unfinished {@link Allocation}s
   */
  ImmutableList<Allocation> getUnfinishedAllocations() {
    Queue<InsertionOrderUnits> unfinishedInsertionOrderUnits = new PriorityQueue<>();

    for (InsertionOrderUnits insertionOrderUnits : completionMap.values()) {
      if (insertionOrderUnits.units.get() < insertionOrderUnits.allocation.getAllocationUnits()) {
        unfinishedInsertionOrderUnits.add(insertionOrderUnits);
      }
    }

    ImmutableList.Builder<Allocation> unfinishedAllocations =
        ImmutableList.builderWithExpectedSize(unfinishedInsertionOrderUnits.size());
    while (!unfinishedInsertionOrderUnits.isEmpty()) {
      unfinishedAllocations.add(unfinishedInsertionOrderUnits.remove().allocation);
    }
    return unfinishedAllocations.build();
  }

  /**
   * Helper method for {@link #updateProgress(Allocation, long)}. Adds units to {@code
   * insertionOrderUnits}. Updates {@link InsertionOrderUnits} for parent {@link Allocation}s if
   * {@code insertionOrderUnits.units} reached completion (equals {@code
   * allocation.getAllocationUnits()}. This method is <em>not</em> thread-safe for the {@code
   * insertionOrderUnits} and should be called atomically relative to the {@code
   * insertionOrderUnits}.
   *
   * @param insertionOrderUnits the {@link InsertionOrderUnits} to update progress for
   * @param units the units of progress
   */
  private void updateInsertionOrderUnits(InsertionOrderUnits insertionOrderUnits, long units) {
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
                  updateInsertionOrderUnits(
                      Preconditions.checkNotNull(completionMap.get(parentAllocation)), 1L));
    }
  }
}
