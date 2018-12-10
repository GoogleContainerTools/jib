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
 * Emits the remaining progress units upon {@link #close}.
 *
 * <p>This class is <em>not</em> thread-safe. Only use a single instance per thread and create child
 * instances with {@link #newChildProducer}.
 */
public class ProgressEventDispatcher implements Closeable {

  @FunctionalInterface
  public interface Factory {

    ProgressEventDispatcher create(String description, long allocationUnits);
  }

  /**
   * @param eventDispatcher
   * @param description
   * @param allocationUnits
   * @return
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
   * @param eventDispatcher
   * @param allocation
   * @return
   */
  private static ProgressEventDispatcher newProgressEventDispatcher(
      EventDispatcher eventDispatcher, Allocation allocation) {
    return new ProgressEventDispatcher(eventDispatcher, allocation).dispatchProgress(0);
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

  public Factory newChildProducer() {
    remainingAllocationUnits--;
    return (description, allocationUnits) ->
        newProgressEventDispatcher(
            eventDispatcher, allocation.newChild(description, allocationUnits));
  }

  public ProgressEventDispatcher dispatchProgress(long progressUnits) {
    eventDispatcher.dispatch(new ProgressEvent(allocation, progressUnits));
    remainingAllocationUnits -= progressUnits;
    Verify.verify(
        remainingAllocationUnits > 0,
        "Remaining allocation units less than 0 for '%s': %s",
        allocation.getDescription(),
        remainingAllocationUnits);
    return this;
  }

  @Override
  public void close() {
    Preconditions.checkState(!closed);
    closed = true;
    if (remainingAllocationUnits > 0) {
      dispatchProgress(remainingAllocationUnits);
    }
  }
}
