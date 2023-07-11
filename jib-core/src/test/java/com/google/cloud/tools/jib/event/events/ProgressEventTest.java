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

package com.google.cloud.tools.jib.event.events;

import com.google.cloud.tools.jib.event.EventHandlers;
import com.google.cloud.tools.jib.event.progress.Allocation;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.Assert;
import org.junit.jupiter.api.Test;

/** Tests for {@link ProgressEvent}. */
class ProgressEventTest {

  /** {@link Allocation} tree for testing. */
  private static class AllocationTree {

    /** The root node. */
    private static final Allocation root = Allocation.newRoot("ignored", 2);

    /** First child of the root node. */
    private static final Allocation child1 = root.newChild("ignored", 1);
    /** Child of the first child of the root node. */
    private static final Allocation child1Child = child1.newChild("ignored", 100);

    /** Second child of the root node. */
    private static final Allocation child2 = root.newChild("ignored", 200);

    private AllocationTree() {}
  }

  private static EventHandlers makeEventHandlers(Consumer<ProgressEvent> progressEventConsumer) {
    return EventHandlers.builder().add(ProgressEvent.class, progressEventConsumer).build();
  }

  private static final double DOUBLE_ERROR_MARGIN = 1e-10;

  private final Map<Allocation, Long> allocationCompletionMap = new HashMap<>();

  private double progress = 0.0;

  @Test
  void testAccumulateProgress() {
    Consumer<ProgressEvent> progressEventConsumer =
        progressEvent -> {
          double fractionOfRoot = progressEvent.getAllocation().getFractionOfRoot();
          long units = progressEvent.getUnits();

          progress += units * fractionOfRoot;
        };

    EventHandlers eventHandlers = makeEventHandlers(progressEventConsumer);

    eventHandlers.dispatch(new ProgressEvent(AllocationTree.child1Child, 50));
    Assert.assertEquals(1.0 / 2 / 100 * 50, progress, DOUBLE_ERROR_MARGIN);

    eventHandlers.dispatch(new ProgressEvent(AllocationTree.child1Child, 50));
    Assert.assertEquals(1.0 / 2, progress, DOUBLE_ERROR_MARGIN);

    eventHandlers.dispatch(new ProgressEvent(AllocationTree.child2, 10));
    Assert.assertEquals(1.0 / 2 + 1.0 / 2 / 200 * 10, progress, DOUBLE_ERROR_MARGIN);

    eventHandlers.dispatch(new ProgressEvent(AllocationTree.child2, 190));
    Assert.assertEquals(1.0, progress, DOUBLE_ERROR_MARGIN);
  }

  @Test
  void testSmoke() {
    Consumer<ProgressEvent> progressEventConsumer =
        progressEvent -> {
          Allocation allocation = progressEvent.getAllocation();
          long units = progressEvent.getUnits();

          updateCompletionMap(allocation, units);
        };

    EventHandlers eventHandlers = makeEventHandlers(progressEventConsumer);

    eventHandlers.dispatch(new ProgressEvent(AllocationTree.child1Child, 50));

    Assert.assertEquals(1, allocationCompletionMap.size());
    Assert.assertEquals(50, allocationCompletionMap.get(AllocationTree.child1Child).longValue());

    eventHandlers.dispatch(new ProgressEvent(AllocationTree.child1Child, 50));

    Assert.assertEquals(3, allocationCompletionMap.size());
    Assert.assertEquals(100, allocationCompletionMap.get(AllocationTree.child1Child).longValue());
    Assert.assertEquals(1, allocationCompletionMap.get(AllocationTree.child1).longValue());
    Assert.assertEquals(1, allocationCompletionMap.get(AllocationTree.root).longValue());

    eventHandlers.dispatch(new ProgressEvent(AllocationTree.child2, 200));

    Assert.assertEquals(4, allocationCompletionMap.size());
    Assert.assertEquals(100, allocationCompletionMap.get(AllocationTree.child1Child).longValue());
    Assert.assertEquals(1, allocationCompletionMap.get(AllocationTree.child1).longValue());
    Assert.assertEquals(200, allocationCompletionMap.get(AllocationTree.child2).longValue());
    Assert.assertEquals(2, allocationCompletionMap.get(AllocationTree.root).longValue());
  }

  @Test
  void testType() {
    // Used to test whether or not progress event was consumed
    boolean[] called = new boolean[] {false};
    Consumer<ProgressEvent> buildImageConsumer =
        progressEvent -> {
          called[0] = true;
        };

    EventHandlers eventHandlers = makeEventHandlers(buildImageConsumer);
    eventHandlers.dispatch(new ProgressEvent(AllocationTree.child1, 50));
    Assert.assertTrue(called[0]);
  }

  /**
   * Updates the {@link #allocationCompletionMap} with {@code units} more progress for {@code
   * allocation}. This also updates {@link Allocation} parents if {@code allocation} is complete in
   * terms of progress.
   *
   * @param allocation the allocation the progress is made on
   * @param units the progress units
   */
  private void updateCompletionMap(Allocation allocation, long units) {
    if (allocationCompletionMap.containsKey(allocation)) {
      units += allocationCompletionMap.get(allocation);
    }
    allocationCompletionMap.put(allocation, units);

    if (allocation.getAllocationUnits() == units) {
      allocation
          .getParent()
          .ifPresent(parentAllocation -> updateCompletionMap(parentAllocation, 1));
    }
  }
}
