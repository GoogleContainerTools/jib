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

import com.google.cloud.tools.jib.event.DefaultEventDispatcher;
import com.google.cloud.tools.jib.event.EventDispatcher;
import com.google.cloud.tools.jib.event.EventHandlers;
import com.google.cloud.tools.jib.event.JibEventType;
import com.google.cloud.tools.jib.event.progress.Allocation;
import java.util.HashMap;
import java.util.Map;
import org.junit.Assert;
import org.junit.Test;

/** Tests for {@link ProgressEvent}. */
public class ProgressEventTest {

  private final Map<Allocation, Long> allocationCompletionMap = new HashMap<>();

  private final EventDispatcher eventDispatcher =
      new DefaultEventDispatcher(
          new EventHandlers().add(JibEventType.PROGRESS, this::handleProgressEvent));

  @Test
  public void testSmoke() {
    Allocation root = Allocation.newRoot("ignored", 2);

    Allocation child1 = root.newChild("ignored", 1);
    Allocation child1Child = child1.newChild("ignored", 100);

    Allocation child2 = root.newChild("ignored", 200);

    eventDispatcher.dispatch(new ProgressEvent(child1Child, 50));

    Assert.assertEquals(1, allocationCompletionMap.size());
    Assert.assertEquals(50, allocationCompletionMap.get(child1Child).longValue());

    eventDispatcher.dispatch(new ProgressEvent(child1Child, 50));

    Assert.assertEquals(3, allocationCompletionMap.size());
    Assert.assertEquals(100, allocationCompletionMap.get(child1Child).longValue());
    Assert.assertEquals(1, allocationCompletionMap.get(child1).longValue());
    Assert.assertEquals(1, allocationCompletionMap.get(root).longValue());

    eventDispatcher.dispatch(new ProgressEvent(child2, 200));

    Assert.assertEquals(4, allocationCompletionMap.size());
    Assert.assertEquals(100, allocationCompletionMap.get(child1Child).longValue());
    Assert.assertEquals(1, allocationCompletionMap.get(child1).longValue());
    Assert.assertEquals(200, allocationCompletionMap.get(child2).longValue());
    Assert.assertEquals(2, allocationCompletionMap.get(root).longValue());
  }

  private void handleProgressEvent(ProgressEvent progressEvent) {
    Allocation allocation = progressEvent.getAllocation();
    long units = progressEvent.getUnits();

    updateCompletionMap(allocation, units);
  }

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
