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
import com.google.cloud.tools.jib.event.EventHandlers;
import com.google.cloud.tools.jib.event.events.ProgressEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.DoubleAccumulator;
import org.junit.Assert;
import org.junit.jupiter.api.Test;

/** Tests for {@link ProgressEventHandler}. */
class ProgressEventHandlerTest {

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

  private static final double DOUBLE_ERROR_MARGIN = 1e-10;

  @Test
  void testAccept() throws ExecutionException, InterruptedException, IOException {
    try (MultithreadedExecutor multithreadedExecutor = new MultithreadedExecutor()) {
      DoubleAccumulator maxProgress = new DoubleAccumulator(Double::max, 0);

      ProgressEventHandler progressEventHandler =
          new ProgressEventHandler(update -> maxProgress.accumulate(update.getProgress()));
      EventHandlers eventHandlers =
          EventHandlers.builder().add(ProgressEvent.class, progressEventHandler).build();

      // Adds root, child1, and child1Child.
      multithreadedExecutor.invoke(
          () -> {
            eventHandlers.dispatch(new ProgressEvent(AllocationTree.root, 0L));
            return null;
          });
      multithreadedExecutor.invoke(
          () -> {
            eventHandlers.dispatch(new ProgressEvent(AllocationTree.child1, 0L));
            return null;
          });
      multithreadedExecutor.invoke(
          () -> {
            eventHandlers.dispatch(new ProgressEvent(AllocationTree.child1Child, 0L));
            return null;
          });
      Assert.assertEquals(0.0, maxProgress.get(), DOUBLE_ERROR_MARGIN);

      // Adds 50 to child1Child and 100 to child2.
      List<Callable<Void>> callables = new ArrayList<>(150);
      callables.addAll(
          Collections.nCopies(
              50,
              () -> {
                eventHandlers.dispatch(new ProgressEvent(AllocationTree.child1Child, 1L));
                return null;
              }));
      callables.addAll(
          Collections.nCopies(
              100,
              () -> {
                eventHandlers.dispatch(new ProgressEvent(AllocationTree.child2, 1L));
                return null;
              }));

      multithreadedExecutor.invokeAll(callables);

      Assert.assertEquals(
          1.0 / 2 / 100 * 50 + 1.0 / 2 / 200 * 100, maxProgress.get(), DOUBLE_ERROR_MARGIN);

      // 0 progress doesn't do anything.
      multithreadedExecutor.invokeAll(
          Collections.nCopies(
              100,
              () -> {
                eventHandlers.dispatch(new ProgressEvent(AllocationTree.child1, 0L));
                return null;
              }));
      Assert.assertEquals(
          1.0 / 2 / 100 * 50 + 1.0 / 2 / 200 * 100, maxProgress.get(), DOUBLE_ERROR_MARGIN);

      // Adds 50 to child1Child and 100 to child2 to finish it up.
      multithreadedExecutor.invokeAll(callables);
      Assert.assertEquals(1.0, maxProgress.get(), DOUBLE_ERROR_MARGIN);
    }
  }
}
