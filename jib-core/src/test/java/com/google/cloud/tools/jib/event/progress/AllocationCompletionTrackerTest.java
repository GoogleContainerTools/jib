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

import com.google.cloud.tools.jib.MultithreadedExecutor;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import org.junit.Assert;
import org.junit.jupiter.api.Test;

/** Tests for {@link AllocationCompletionTracker}. */
class AllocationCompletionTrackerTest {

  /** {@link Allocation} tree for testing. */
  private static class AllocationTree {

    /** The root node. */
    private static final Allocation root = Allocation.newRoot("root", 2);

    /** First child of the root node. */
    private static final Allocation child1 = root.newChild("child1", 1);
    /** Child of the first child of the root node. */
    private static final Allocation child1Child = child1.newChild("child1Child", 100);

    /** Second child of the root node. */
    private static final Allocation child2 = root.newChild("child2", 200);

    private AllocationTree() {}
  }

  @Test
  void testGetUnfinishedAllocations_singleThread() {
    AllocationCompletionTracker allocationCompletionTracker = new AllocationCompletionTracker();

    Assert.assertTrue(allocationCompletionTracker.updateProgress(AllocationTree.root, 0L));
    Assert.assertEquals(
        Collections.singletonList(AllocationTree.root),
        allocationCompletionTracker.getUnfinishedAllocations());

    Assert.assertTrue(allocationCompletionTracker.updateProgress(AllocationTree.child1, 0L));
    Assert.assertEquals(
        Arrays.asList(AllocationTree.root, AllocationTree.child1),
        allocationCompletionTracker.getUnfinishedAllocations());

    Assert.assertTrue(allocationCompletionTracker.updateProgress(AllocationTree.child1Child, 0L));
    Assert.assertEquals(
        Arrays.asList(AllocationTree.root, AllocationTree.child1, AllocationTree.child1Child),
        allocationCompletionTracker.getUnfinishedAllocations());

    Assert.assertTrue(allocationCompletionTracker.updateProgress(AllocationTree.child1Child, 50L));
    Assert.assertEquals(
        Arrays.asList(AllocationTree.root, AllocationTree.child1, AllocationTree.child1Child),
        allocationCompletionTracker.getUnfinishedAllocations());

    Assert.assertTrue(allocationCompletionTracker.updateProgress(AllocationTree.child1Child, 50L));
    Assert.assertEquals(
        Collections.singletonList(AllocationTree.root),
        allocationCompletionTracker.getUnfinishedAllocations());

    Assert.assertTrue(allocationCompletionTracker.updateProgress(AllocationTree.child2, 100L));
    Assert.assertEquals(
        Arrays.asList(AllocationTree.root, AllocationTree.child2),
        allocationCompletionTracker.getUnfinishedAllocations());

    Assert.assertTrue(allocationCompletionTracker.updateProgress(AllocationTree.child2, 100L));
    Assert.assertEquals(
        Collections.emptyList(), allocationCompletionTracker.getUnfinishedAllocations());

    Assert.assertFalse(allocationCompletionTracker.updateProgress(AllocationTree.child2, 0L));
    Assert.assertEquals(
        Collections.emptyList(), allocationCompletionTracker.getUnfinishedAllocations());

    try {
      allocationCompletionTracker.updateProgress(AllocationTree.child1, 1L);
      Assert.fail();

    } catch (IllegalStateException ex) {
      Assert.assertEquals("Progress exceeds max for 'child1': 1 more beyond 1", ex.getMessage());
    }
  }

  @Test
  void testGetUnfinishedAllocations_multipleThreads()
      throws InterruptedException, ExecutionException, IOException {
    try (MultithreadedExecutor multithreadedExecutor = new MultithreadedExecutor()) {
      AllocationCompletionTracker allocationCompletionTracker = new AllocationCompletionTracker();

      // Adds root, child1, and child1Child.
      Assert.assertEquals(
          true,
          multithreadedExecutor.invoke(
              () -> allocationCompletionTracker.updateProgress(AllocationTree.root, 0L)));
      Assert.assertEquals(
          true,
          multithreadedExecutor.invoke(
              () -> allocationCompletionTracker.updateProgress(AllocationTree.child1, 0L)));
      Assert.assertEquals(
          true,
          multithreadedExecutor.invoke(
              () -> allocationCompletionTracker.updateProgress(AllocationTree.child1Child, 0L)));
      Assert.assertEquals(
          Arrays.asList(AllocationTree.root, AllocationTree.child1, AllocationTree.child1Child),
          allocationCompletionTracker.getUnfinishedAllocations());

      // Adds 50 to child1Child and 100 to child2.
      List<Callable<Boolean>> callables = new ArrayList<>(150);
      callables.addAll(
          Collections.nCopies(
              50,
              () -> allocationCompletionTracker.updateProgress(AllocationTree.child1Child, 1L)));
      callables.addAll(
          Collections.nCopies(
              100, () -> allocationCompletionTracker.updateProgress(AllocationTree.child2, 1L)));

      Assert.assertEquals(
          Collections.nCopies(150, true), multithreadedExecutor.invokeAll(callables));
      Assert.assertEquals(
          Arrays.asList(
              AllocationTree.root,
              AllocationTree.child1,
              AllocationTree.child1Child,
              AllocationTree.child2),
          allocationCompletionTracker.getUnfinishedAllocations());

      // 0 progress doesn't do anything.
      Assert.assertEquals(
          Collections.nCopies(100, false),
          multithreadedExecutor.invokeAll(
              Collections.nCopies(
                  100,
                  () -> allocationCompletionTracker.updateProgress(AllocationTree.child1, 0L))));
      Assert.assertEquals(
          Arrays.asList(
              AllocationTree.root,
              AllocationTree.child1,
              AllocationTree.child1Child,
              AllocationTree.child2),
          allocationCompletionTracker.getUnfinishedAllocations());

      // Adds 50 to child1Child and 100 to child2 to finish it up.
      Assert.assertEquals(
          Collections.nCopies(150, true), multithreadedExecutor.invokeAll(callables));
      Assert.assertEquals(
          Collections.emptyList(), allocationCompletionTracker.getUnfinishedAllocations());
    }
  }

  @Test
  void testGetUnfinishedLeafTasks() {
    AllocationCompletionTracker tracker = new AllocationCompletionTracker();
    tracker.updateProgress(AllocationTree.root, 0);
    Assert.assertEquals(Arrays.asList("root"), tracker.getUnfinishedLeafTasks());

    tracker.updateProgress(AllocationTree.child1, 0);
    Assert.assertEquals(Arrays.asList("child1"), tracker.getUnfinishedLeafTasks());

    tracker.updateProgress(AllocationTree.child2, 0);
    Assert.assertEquals(Arrays.asList("child1", "child2"), tracker.getUnfinishedLeafTasks());

    tracker.updateProgress(AllocationTree.child1Child, 0);
    Assert.assertEquals(Arrays.asList("child2", "child1Child"), tracker.getUnfinishedLeafTasks());

    tracker.updateProgress(AllocationTree.child1Child, 50);
    Assert.assertEquals(Arrays.asList("child2", "child1Child"), tracker.getUnfinishedLeafTasks());

    tracker.updateProgress(AllocationTree.child2, 100);
    Assert.assertEquals(Arrays.asList("child2", "child1Child"), tracker.getUnfinishedLeafTasks());

    tracker.updateProgress(AllocationTree.child2, 100);
    Assert.assertEquals(Arrays.asList("child1Child"), tracker.getUnfinishedLeafTasks());

    tracker.updateProgress(AllocationTree.child1Child, 50);
    Assert.assertEquals(Collections.emptyList(), tracker.getUnfinishedLeafTasks());
  }

  @Test
  void testGetUnfinishedLeafTasks_differentUpdateOrder() {
    AllocationCompletionTracker tracker = new AllocationCompletionTracker();
    tracker.updateProgress(AllocationTree.root, 0);
    Assert.assertEquals(Arrays.asList("root"), tracker.getUnfinishedLeafTasks());

    tracker.updateProgress(AllocationTree.child2, 0);
    Assert.assertEquals(Arrays.asList("child2"), tracker.getUnfinishedLeafTasks());

    tracker.updateProgress(AllocationTree.child1, 0);
    Assert.assertEquals(Arrays.asList("child2", "child1"), tracker.getUnfinishedLeafTasks());

    tracker.updateProgress(AllocationTree.child1Child, 0);
    Assert.assertEquals(Arrays.asList("child2", "child1Child"), tracker.getUnfinishedLeafTasks());

    tracker.updateProgress(AllocationTree.child1Child, 50);
    Assert.assertEquals(Arrays.asList("child2", "child1Child"), tracker.getUnfinishedLeafTasks());

    tracker.updateProgress(AllocationTree.child2, 100);
    Assert.assertEquals(Arrays.asList("child2", "child1Child"), tracker.getUnfinishedLeafTasks());

    tracker.updateProgress(AllocationTree.child1Child, 50);
    Assert.assertEquals(Arrays.asList("child2"), tracker.getUnfinishedLeafTasks());

    tracker.updateProgress(AllocationTree.child2, 100);
    Assert.assertEquals(Collections.emptyList(), tracker.getUnfinishedLeafTasks());
  }
}
