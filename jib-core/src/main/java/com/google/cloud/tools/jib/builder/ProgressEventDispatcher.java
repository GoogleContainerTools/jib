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

package com.google.cloud.tools.jib.builder;

import com.google.cloud.tools.jib.event.EventHandlers;
import com.google.cloud.tools.jib.event.events.ProgressEvent;
import com.google.cloud.tools.jib.event.progress.Allocation;
import com.google.common.base.Preconditions;
import java.io.Closeable;

/**
 * Dispatches {@link ProgressEvent}s associated with a managed {@link Allocation}. Keeps track of
 * the allocation units that are remaining so that it can emit the remaining progress units upon
 * {@link #close}.
 *
 * <p>This class is <em>not</em> thread-safe. Only use a single instance per thread and create child
 * instances with {@link #newChildProducer}.
 */
public class ProgressEventDispatcher implements Closeable {

  /**
   * Creates a new {@link ProgressEventDispatcher} based off an existing {@link
   * ProgressEventDispatcher}. {@link #create} should only be called once.
   */
  @FunctionalInterface
  public interface Factory {

    /**
     * Creates the {@link ProgressEventDispatcher} with an associated {@link Allocation}.
     *
     * @param description user-facing description of what the allocation represents
     * @param allocationUnits number of allocation units
     * @return the new {@link ProgressEventDispatcher}
     */
    ProgressEventDispatcher create(String description, long allocationUnits);
  }

  /**
   * Creates a new {@link ProgressEventDispatcher} with a root {@link Allocation}.
   *
   * @param eventHandlers the {@link EventHandlers}
   * @param description user-facing description of what the allocation represents
   * @param allocationUnits number of allocation units
   * @return a new {@link ProgressEventDispatcher}
   */
  public static ProgressEventDispatcher newRoot(
      EventHandlers eventHandlers, String description, long allocationUnits) {
    return newProgressEventDispatcher(
        eventHandlers, Allocation.newRoot(description, allocationUnits));
  }

  /**
   * Creates a new {@link ProgressEventDispatcher} and dispatches a new {@link ProgressEvent} with
   * progress 0 for {@code allocation}.
   *
   * @param eventHandlers the {@link EventHandlers}
   * @param allocation the {@link Allocation} to manage
   * @return a new {@link ProgressEventDispatcher}
   */
  private static ProgressEventDispatcher newProgressEventDispatcher(
      EventHandlers eventHandlers, Allocation allocation) {
    ProgressEventDispatcher progressEventDispatcher =
        new ProgressEventDispatcher(eventHandlers, allocation);
    progressEventDispatcher.dispatchProgress(0);
    return progressEventDispatcher;
  }

  private final EventHandlers eventHandlers;
  private final Allocation allocation;

  private long remainingAllocationUnits;
  private boolean closed = false;

  private ProgressEventDispatcher(EventHandlers eventHandlers, Allocation allocation) {
    this.eventHandlers = eventHandlers;
    this.allocation = allocation;

    remainingAllocationUnits = allocation.getAllocationUnits();
  }

  /**
   * Creates a new {@link Factory} for a {@link ProgressEventDispatcher} that manages a child {@link
   * Allocation}. Since each child {@link Allocation} accounts for 1 allocation unit of its parent,
   * this method decrements the {@link #remainingAllocationUnits} by {@code 1}.
   *
   * @return a new {@link Factory}
   */
  public Factory newChildProducer() {
    decrementRemainingAllocationUnits(1);

    return new Factory() {

      private boolean used = false;

      @Override
      public ProgressEventDispatcher create(String description, long allocationUnits) {
        Preconditions.checkState(!used);
        used = true;
        return newProgressEventDispatcher(
            eventHandlers, allocation.newChild(description, allocationUnits));
      }
    };
  }

  /** Emits the remaining allocation units as progress units in a {@link ProgressEvent}. */
  @Override
  public void close() {
    if (remainingAllocationUnits > 0) {
      dispatchProgress(remainingAllocationUnits);
    }
    closed = true;
  }

  /**
   * Dispatches a {@link ProgressEvent} representing {@code progressUnits} of progress on the
   * managed {@link #allocation}.
   *
   * @param progressUnits units of progress
   */
  public void dispatchProgress(long progressUnits) {
    long unitsDecremented = decrementRemainingAllocationUnits(progressUnits);
    eventHandlers.dispatch(new ProgressEvent(allocation, unitsDecremented));
  }

  /**
   * Decrements remaining allocation units by {@code units} but no more than the remaining
   * allocation units (which may be 0). Returns the actual units decremented, which never exceeds
   * {@code units}.
   *
   * @param units units to decrement
   * @return units actually decremented
   */
  private long decrementRemainingAllocationUnits(long units) {
    Preconditions.checkState(!closed);

    if (remainingAllocationUnits > units) {
      remainingAllocationUnits -= units;
      return units;
    }

    long actualDecrement = remainingAllocationUnits;
    remainingAllocationUnits = 0;
    return actualDecrement;
  }
}
