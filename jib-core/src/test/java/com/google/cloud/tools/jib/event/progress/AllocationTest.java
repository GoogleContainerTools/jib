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

import org.junit.Assert;
import org.junit.Test;

/** Tests for {@link Allocation}. */
public class AllocationTest {

  /** Error margin for checking equality of two doubles. */
  private static final double DOUBLE_ERROR_MARGIN = 1e-10;

  /**
   * Checks that the {@code allocations} add up to a full {@code 1.0}.
   *
   * @param allocations the leaf allocations
   */
  private static void checkAllocationComplete(Allocation... allocations) {
    double total = 0.0;
    for (Allocation allocation : allocations) {
      total += allocation.getFractionOfRoot();
    }
    Assert.assertEquals(1.0, total, DOUBLE_ERROR_MARGIN);
  }

  @Test
  public void testSmoke_linear() {
    Allocation root = Allocation.newRoot("root").allocate(1);
    Allocation node1 = root.newChild("node1").allocate(2);
    Allocation node2 = node1.newChild("node2").allocate(3);

    Assert.assertEquals("node2", node2.getDescription());
    Assert.assertEquals(3, node2.getAllocationUnits());
    Assert.assertEquals(1.0 / 2, node2.getFractionOfRoot(), DOUBLE_ERROR_MARGIN);
    Assert.assertTrue(node2.getParent().isPresent());
    Assert.assertEquals(node1, node2.getParent().get());

    Assert.assertEquals("node1", node1.getDescription());
    Assert.assertEquals(2, node1.getAllocationUnits());
    Assert.assertTrue(node1.getParent().isPresent());
    Assert.assertEquals(root, node1.getParent().get());
    Assert.assertEquals(1.0, node1.getFractionOfRoot(), DOUBLE_ERROR_MARGIN);

    Assert.assertEquals("root", root.getDescription());
    Assert.assertEquals(1, root.getAllocationUnits());
    Assert.assertFalse(root.getParent().isPresent());
    Assert.assertEquals(1.0, root.getFractionOfRoot(), DOUBLE_ERROR_MARGIN);
  }

  @Test
  public void testFractionOfRoot_tree_partial() {
    Allocation root = Allocation.newRoot("ignored").allocate(10);
    Allocation left = root.newChild("ignored").allocate(2);
    Allocation right = root.newChild("ignored").allocate(4);
    Allocation leftDown = left.newChild("ignored").allocate(20);
    Allocation rightLeft = right.newChild("ignored").allocate(20);
    Allocation rightRight = right.newChild("ignored").allocate(100);
    Allocation rightRightDown = rightRight.newChild("ignored").allocate(200);

    Assert.assertEquals(1.0, root.getFractionOfRoot(), DOUBLE_ERROR_MARGIN);
    Assert.assertEquals(1.0 / 10, left.getFractionOfRoot(), DOUBLE_ERROR_MARGIN);
    Assert.assertEquals(1.0 / 10, right.getFractionOfRoot(), DOUBLE_ERROR_MARGIN);
    Assert.assertEquals(1.0 / 10 / 2, leftDown.getFractionOfRoot(), DOUBLE_ERROR_MARGIN);
    Assert.assertEquals(1.0 / 10 / 4, rightLeft.getFractionOfRoot(), DOUBLE_ERROR_MARGIN);
    Assert.assertEquals(1.0 / 10 / 4, rightRight.getFractionOfRoot(), DOUBLE_ERROR_MARGIN);
    Assert.assertEquals(
        1.0 / 10 / 4 / 100, rightRightDown.getFractionOfRoot(), DOUBLE_ERROR_MARGIN);
  }

  @Test
  public void testFractionOfRoot_tree_complete() {
    Allocation root = Allocation.newRoot("ignored").allocate(2);

    Allocation left = root.newChild("ignored").allocate(3);
    Allocation leftLeft = left.newChild("ignored").allocate(1);
    Allocation leftLeftDown = leftLeft.newChild("ignored").allocate(100);
    Allocation leftMiddle = left.newChild("ignored").allocate(100);
    Allocation leftRight = left.newChild("ignored").allocate(100);

    Allocation right = root.newChild("ignored").allocate(1);
    Allocation rightDown = right.newChild("ignored").allocate(100);

    checkAllocationComplete(leftLeftDown, leftMiddle, leftRight, rightDown);
  }
}
