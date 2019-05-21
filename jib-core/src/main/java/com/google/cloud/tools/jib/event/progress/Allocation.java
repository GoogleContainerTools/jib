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

import java.util.Optional;
import javax.annotation.Nullable;

/**
 * Represents a Decentralized Allocation Tree (DAT) node.
 *
 * <p>A DAT node is immutable and pointers only go in the direction from child to parent. Each node
 * has a set number of allocated units, the total of which represents a single allocation unit of
 * its parent. Each node is therefore a sub-allocation of its parent node. This allows the DAT to
 * sub-allocate progress in a decentralized, asynchronous manner.
 *
 * <p>For example, thread 1 creates node A as the root node with 2 allocation units. A subtask is
 * launched on thread 1 and creates node B with 3 allocation units as a child of node A. Thread 1
 * then also launches a subtask on thread 2 that creates node C with 5 allocation units. Once the
 * first subtask finishes and reports its progress, that completion would entail completion of 3
 * allocation units of node B and 1 allocation unit of node A. The second subtask finishes and
 * reports its progress as well, indicating completion of 5 units of node C and thus 1 unit of node
 * A. Allocation A is then deemed complete as well in terms of overall progress.
 *
 * <p>Note that it is up to the user of the class to ensure that the number of sub-allocations does
 * not exceed the number of allocation units.
 */
public class Allocation {

  /**
   * Creates a new root {@link Allocation}.
   *
   * @param description user-facing description of what the allocation represents
   * @param allocationUnits number of allocation units
   * @return a new {@link Allocation}
   */
  public static Allocation newRoot(String description, long allocationUnits) {
    return new Allocation(description, allocationUnits, null);
  }

  /** The parent {@link Allocation}, or {@code null} to indicate a root node. */
  @Nullable private final Allocation parent;

  /** User-facing description of what the allocation represents. */
  private final String description;

  /** The number of allocation units this node holds. */
  private final long allocationUnits;

  /** How much of the root allocation (1.0) each allocation unit accounts for. */
  private final double fractionOfRoot;

  private Allocation(String description, long allocationUnits, @Nullable Allocation parent) {
    this.description = description;
    this.allocationUnits = allocationUnits < 0 ? 0 : allocationUnits;
    this.parent = parent;

    this.fractionOfRoot = (parent == null ? 1.0 : parent.fractionOfRoot) / allocationUnits;
  }

  /**
   * Creates a new child {@link Allocation} (sub-allocation).
   *
   * @param description user-facing description of what the sub-allocation represents
   * @param allocationUnits number of allocation units the child holds
   * @return a new {@link Allocation}
   */
  public Allocation newChild(String description, long allocationUnits) {
    return new Allocation(description, allocationUnits, this);
  }

  /**
   * Gets the parent allocation, or {@link Optional#empty} if this is a root allocation. This
   * allocation represents 1 allocation unit of the parent allocation.
   *
   * @return the parent {@link Allocation}
   */
  public Optional<Allocation> getParent() {
    return Optional.ofNullable(parent);
  }

  /**
   * Gets a user-facing description of what this allocation represents. For example, this can a
   * description of the task this allocation represents.
   *
   * @return the description
   */
  public String getDescription() {
    return description;
  }

  /**
   * Gets the allocation units this allocation holds. If this allocation is not the root, the full
   * number of units represents 1 allocation unit of the parent.
   *
   * @return the allocation units
   */
  public long getAllocationUnits() {
    return allocationUnits;
  }

  /**
   * Gets how much of the root allocation each of the allocation units of this allocation accounts
   * for. The entire root allocation is {@code 1.0}.
   *
   * @return the fraction of the root allocation this allocation's allocation units accounts for
   */
  public double getFractionOfRoot() {
    return fractionOfRoot;
  }
}
