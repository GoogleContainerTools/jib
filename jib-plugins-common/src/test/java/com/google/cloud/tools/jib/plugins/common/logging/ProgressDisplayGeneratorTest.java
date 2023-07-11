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

import java.util.Arrays;
import java.util.Collections;
import org.junit.Assert;
import org.junit.jupiter.api.Test;

/** Tests for {@link ProgressDisplayGenerator}. */
class ProgressDisplayGeneratorTest {

  private static String getBar(String bar, double value) {
    return String.format("%s %.1f%% complete", bar, value);
  }

  @Test
  void testGenerateProgressDisplay_progressBar_0() {
    Assert.assertEquals(
        Arrays.asList("Executing tasks:", getBar("[                              ]", 0.0)),
        ProgressDisplayGenerator.generateProgressDisplay(0, Collections.emptyList()));
  }

  @Test
  void testGenerateProgressDisplay_progressBar_50() {
    Assert.assertEquals(
        Arrays.asList("Executing tasks:", getBar("[===============               ]", 50.0)),
        ProgressDisplayGenerator.generateProgressDisplay(0.5, Collections.emptyList()));
  }

  @Test
  void testGenerateProgressDisplay_progressBar_100() {
    Assert.assertEquals(
        Arrays.asList("Executing tasks:", getBar("[==============================]", 100.0)),
        ProgressDisplayGenerator.generateProgressDisplay(1, Collections.emptyList()));
  }

  @Test
  void testGenerateProgressDisplay_unfinishedTasks() {
    Assert.assertEquals(
        Arrays.asList(
            "Executing tasks:",
            getBar("[===============               ]", 50.0),
            "> unfinished task",
            "> another task in progress",
            "> stalled"),
        ProgressDisplayGenerator.generateProgressDisplay(
            0.5, Arrays.asList("unfinished task", "another task in progress", "stalled")));
  }
}
