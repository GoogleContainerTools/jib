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

package com.google.cloud.tools.jib.plugins.common.logging;

import com.google.cloud.tools.jib.event.progress.Allocation;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Generates a display of progress and unfinished tasks.
 *
 * <p>Example:
 *
 * <p>Executing tasks...<br>
 * [================= ] 72.5% complete<br>
 * &gt; task 1 running<br>
 * &gt; task 3 running
 */
class ProgressDisplayGenerator {

  /** Line above progress bar. */
  private static final String HEADER = "Executing tasks:";

  /** Maximum number of bars in the progress display. */
  private static final int PROGRESS_BAR_COUNT = 50;

  /**
   * Generates a progress display.
   *
   * @param progress the overall progress, with {@code 1.0} meaning fully complete
   * @param unfinishedAllocations the unfinished {@link Allocation}s
   * @return the progress display as a list of lines
   */
  static List<String> generateProgressDisplay(
      double progress, List<Allocation> unfinishedAllocations) {
    List<String> lines = new ArrayList<>();

    lines.add(HEADER);
    lines.add(generateProgressBar(progress));
    lines.addAll(generateUnfinishedTasks(unfinishedAllocations));

    return lines;
  }

  /**
   * Generates the progress bar line.
   *
   * @param progress the overall progress, with {@code 1.0} meaning fully complete
   * @return the progress bar line
   */
  private static String generateProgressBar(double progress) {
    StringBuilder progressBar = new StringBuilder();
    progressBar.append('[');

    int barsToDisplay = (int) Math.round(PROGRESS_BAR_COUNT * progress);
    for (int barIndex = 0; barIndex < PROGRESS_BAR_COUNT; barIndex++) {
      progressBar.append(barIndex < barsToDisplay ? '=' : ' ');
    }

    return progressBar
        .append(']')
        .append(String.format(" %.1f", progress * 100))
        .append("% complete")
        .toString();
  }

  /**
   * Generates the display of the unfinished tasks from a list of unfinished {@link Allocation}s
   *
   * @param unfinishedAllocations the list of unfinished {@link Allocation}s
   * @return the display of the unfinished {@link Allocation}s
   */
  private static List<String> generateUnfinishedTasks(List<Allocation> unfinishedAllocations) {
    List<String> lines = new ArrayList<>();
    for (Allocation unfinishedAllocation : getLeafAllocations(unfinishedAllocations)) {
      lines.add("> " + unfinishedAllocation.getDescription());
    }
    return lines;
  }

  /**
   * Gets a list of just the leaf {@link Allocation}s in {@code unfinishedAllocations} in the same
   * order as they appear in {@code unfinishedAllocations}.
   *
   * @param unfinishedAllocations the list of unfinished {@link Allocation}s
   * @return the list of unfinished {@link Allocation}s
   */
  // TODO: Optimization: Change AllocationCompletionTracker#getUnfinishedAllocations to only return
  // leaf Allocations so that this computation is unnecessary.
  private static List<Allocation> getLeafAllocations(List<Allocation> unfinishedAllocations) {
    // Prunes the set of all unfinished Allocations to leave just the leaves.
    Set<Allocation> leafAllocationSet = new HashSet<>(unfinishedAllocations);
    for (Allocation allocation : unfinishedAllocations) {
      Optional<Allocation> parent = allocation.getParent();

      while (parent.isPresent()) {
        leafAllocationSet.remove(parent.get());
        parent = parent.get().getParent();
      }
    }

    // Makes a list of leaf allocations in the same order as the unfinishedAllocations.
    List<Allocation> leafAllocations = new ArrayList<>();
    for (Allocation allocation : unfinishedAllocations) {
      if (leafAllocationSet.contains(allocation)) {
        leafAllocations.add(allocation);
      }
    }
    return leafAllocations;
  }

  private ProgressDisplayGenerator() {}
}
