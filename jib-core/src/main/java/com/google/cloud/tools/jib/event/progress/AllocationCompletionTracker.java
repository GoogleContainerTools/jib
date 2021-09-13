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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Keeps track of the progress for {@link Allocation}s as well as their order in which they appear.
 *
 * <p>This implementation is thread-safe.
 */
class AllocationCompletionTracker {

  /**
   * Holds the progress units remaining along with a creation order (index starting from 0). This is
   * used as the value of the {@link #completionMap}.
   */
  private static class IndexedRemainingUnits implements Comparable<IndexedRemainingUnits> {

    /** Monotonically-increasing source for {@link #index}. */
    private static final AtomicInteger currentIndex = new AtomicInteger();

    /** The creation order that monotonically increases. */
    private final int index = currentIndex.getAndIncrement();

    /**
     * Remaining progress units until completion. This can be shared across multiple threads and
     * should be updated atomically.
     */
    private final AtomicLong remainingUnits;

    private final Allocation allocation;

    private IndexedRemainingUnits(Allocation allocation) {
      this.allocation = allocation;
      remainingUnits = new AtomicLong(allocation.getAllocationUnits());
    }

    private boolean isUnfinished() {
      return remainingUnits.get() != 0;
    }

    @Override
    public int compareTo(IndexedRemainingUnits otherIndexedRemainingUnits) {
      return index - otherIndexedRemainingUnits.index;
    }
  }

  /**
   * Maps from {@link Allocation} to 1) the number of progress units remaining in that {@link
   * Allocation}; as well as 2) the insertion order of the key.
   */
  private final ConcurrentHashMap<Allocation, IndexedRemainingUnits> completionMap =
      new ConcurrentHashMap<>();

  /**
   * Updates the progress for {@link Allocation} atomically relative to the {@code allocation}.
   *
   * <p>For any {@link Allocation}, this method <em>must</em> have been called on all of its parents
   * beforehand.
   *
   * @param allocation the {@link Allocation} to update progress for
   * @param units the units of progress
   * @return {@code true} if the map was updated
   */
  boolean updateProgress(Allocation allocation, long units) {
    AtomicBoolean mapUpdated = new AtomicBoolean(units != 0);

    completionMap.compute(
        allocation,
        (ignored, indexedRemainingUnits) -> {
          if (indexedRemainingUnits == null) {
            indexedRemainingUnits = new IndexedRemainingUnits(allocation);
            mapUpdated.set(true);
          }

          if (units != 0) {
            updateIndexedRemainingUnits(indexedRemainingUnits, units);
          }
          return indexedRemainingUnits;
        });
    return mapUpdated.get();
  }

  /**
   * Gets a list of the unfinished {@link Allocation}s in the order in which those {@link
   * Allocation}s were encountered. This can be used to display, for example, currently executing
   * tasks. The order helps to keep the displayed tasks in a deterministic order (new subtasks
   * appear below older ones) and not jumbled together in some random order.
   *
   * @return a list of unfinished {@link Allocation}s
   */
  @VisibleForTesting
  List<Allocation> getUnfinishedAllocations() {
    return completionMap.values().stream()
        .filter(IndexedRemainingUnits::isUnfinished)
        .sorted()
        .map(remainingUnits -> remainingUnits.allocation)
        .collect(Collectors.toList());
  }

  /**
   * Helper method for {@link #updateProgress(Allocation, long)}. Subtract {@code units} from {@code
   * indexedRemainingUnits}. Updates {@link IndexedRemainingUnits} for parent {@link Allocation}s if
   * remaining units becomes 0. This method is <em>not</em> thread-safe for the {@code
   * indexedRemainingUnits} and should be called atomically relative to the {@code
   * indexedRemainingUnits}.
   *
   * @param indexedRemainingUnits the {@link IndexedRemainingUnits} to update progress for
   * @param units the units of progress
   */
  private void updateIndexedRemainingUnits(
      IndexedRemainingUnits indexedRemainingUnits, long units) {
    Allocation allocation = indexedRemainingUnits.allocation;

    long newUnits = indexedRemainingUnits.remainingUnits.addAndGet(-units);
    if (newUnits < 0L) {
      throw new IllegalStateException(
          "Progress exceeds max for '"
              + allocation.getDescription()
              + "': "
              + -newUnits
              + " more beyond "
              + allocation.getAllocationUnits());
    }

    // Updates the parent allocations if this allocation completed.
    if (newUnits == 0L) {
      allocation
          .getParent()
          .ifPresent(
              parentAllocation ->
                  updateIndexedRemainingUnits(
                      Preconditions.checkNotNull(completionMap.get(parentAllocation)), 1L));
    }
  }

  ImmutableList<String> getUnfinishedLeafTasks() {
    List<Allocation> allUnfinished = getUnfinishedAllocations();
    Set<Allocation> unfinishedLeaves = new LinkedHashSet<>(allUnfinished); // preserves order

    for (Allocation allocation : allUnfinished) {
      Optional<Allocation> parent = allocation.getParent();

      while (parent.isPresent()) {
        unfinishedLeaves.remove(parent.get());
        parent = parent.get().getParent();
      }
    }

    return ImmutableList.copyOf(
        unfinishedLeaves.stream().map(Allocation::getDescription).collect(Collectors.toList()));
  }
}
