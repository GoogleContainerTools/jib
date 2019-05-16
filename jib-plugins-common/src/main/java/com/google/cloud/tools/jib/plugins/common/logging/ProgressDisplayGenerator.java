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

import java.util.ArrayList;
import java.util.List;

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
public class ProgressDisplayGenerator {

  /** Line above progress bar. */
  private static final String HEADER = "Executing tasks:";

  /** Maximum number of bars in the progress display. */
  private static final int PROGRESS_BAR_COUNT = 30;

  /**
   * Generates a progress display.
   *
   * @param progress the overall progress, with {@code 1.0} meaning fully complete
   * @param unfinishedLeafTasks the unfinished leaf tasks
   * @return the progress display as a list of lines
   */
  public static List<String> generateProgressDisplay(
      double progress, List<String> unfinishedLeafTasks) {
    List<String> lines = new ArrayList<>();

    lines.add(HEADER);
    lines.add(generateProgressBar(progress));
    for (String task : unfinishedLeafTasks) {
      lines.add("> " + task);
    }

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

  private ProgressDisplayGenerator() {}
}
