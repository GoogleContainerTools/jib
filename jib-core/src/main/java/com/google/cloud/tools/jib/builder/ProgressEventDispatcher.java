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

import com.google.cloud.tools.jib.event.EventDispatcher;
import com.google.cloud.tools.jib.event.events.ProgressEvent;
import com.google.cloud.tools.jib.event.progress.Allocation;
import com.google.common.base.Preconditions;
import com.google.common.base.Verify;
import java.io.Closeable;

/**
 * Dispatches {@link ProgressEvent}s associated with a managed {@link Allocation}. Keeps track of
 * the allocation units that are remaining so that it can emits the remaining progress units upon
 * {@link #close}.
 *
 * <p>This class is <em>not</em> thread-safe. Only use a single instance per thread and create child
 * instances with {@link #newChildProducer}.
 */
public class ProgressEventDispatcher implements Closeable {

  /**
   * Creates a new {@link ProgressEventDispatcher} based off an existing {@link
   * ProgressEventDispatcher}.
   *
   * <p>Implementations should be thread-safe.
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
   * @param eventDispatcher the {@link EventDispatcher}
   * @param description user-facing description of what the allocation represents
   * @param allocationUnits number of allocation units
   * @return a new {@link ProgressEventDispatcher}
   */
  public static ProgressEventDispatcher newRoot(
      EventDispatcher eventDispatcher, String description, long allocationUnits) {
    return newProgressEventDispatcher(
        eventDispatcher, Allocation.newRoot(description, allocationUnits));
  }

  /**
   * Creates a new {@link ProgressEventDispatcher} and dispatches a new {@link ProgressEvent} with
   * progress 0 for {@code allocation}.
   *
   * @param eventDispatcher the {@link EventDispatcher}
   * @param allocation the {@link Allocation} to manage
   * @return a new {@link ProgressEventDispatcher}
   */
  private static ProgressEventDispatcher newProgressEventDispatcher(
      EventDispatcher eventDispatcher, Allocation allocation) {
    ProgressEventDispatcher progressEventDispatcher =
        new ProgressEventDispatcher(eventDispatcher, allocation);
    progressEventDispatcher.dispatchProgress(0);
    return progressEventDispatcher;
  }

  private final EventDispatcher eventDispatcher;
  private final Allocation allocation;

  private long remainingAllocationUnits;
  private boolean closed = false;

  private ProgressEventDispatcher(EventDispatcher eventDispatcher, Allocation allocation) {
    this.eventDispatcher = eventDispatcher;
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
    return (description, allocationUnits) ->
        newProgressEventDispatcher(
            eventDispatcher, allocation.newChild(description, allocationUnits));
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
    decrementRemainingAllocationUnits(progressUnits);
    eventDispatcher.dispatch(new ProgressEvent(allocation, progressUnits));
  }

  private void decrementRemainingAllocationUnits(long units) {
    Preconditions.checkState(!closed);

    remainingAllocationUnits -= units;
    Verify.verify(
        remainingAllocationUnits >= 0,
        "Remaining allocation units less than 0 for '%s': %s",
        allocation.getDescription(),
        remainingAllocationUnits);
  }
}
