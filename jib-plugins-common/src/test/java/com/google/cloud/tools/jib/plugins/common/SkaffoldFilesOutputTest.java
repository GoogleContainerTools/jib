/*
 * Copyright 2019 Google LLC.
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

package com.google.cloud.tools.jib.plugins.common;

import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Paths;
import org.junit.Assert;
import org.junit.jupiter.api.Test;

/** Tests for {@link SkaffoldFilesOutput}. */
class SkaffoldFilesOutputTest {

  private static final String TEST_JSON =
      "{\"build\":[\"buildFile1\",\"buildFile2\"],\"inputs\":[\"input1\",\"input2\"],\"ignore\":[\"ignore1\",\"ignore2\"]}";

  @Test
  void testGetJsonString() throws IOException {
    SkaffoldFilesOutput skaffoldFilesOutput = new SkaffoldFilesOutput();
    skaffoldFilesOutput.addBuild(Paths.get("buildFile1"));
    skaffoldFilesOutput.addBuild(Paths.get("buildFile2").toFile());
    skaffoldFilesOutput.addInput(Paths.get("input1"));
    skaffoldFilesOutput.addInput(Paths.get("input2").toFile());
    skaffoldFilesOutput.addIgnore(Paths.get("ignore1"));
    skaffoldFilesOutput.addIgnore(Paths.get("ignore2").toFile());
    Assert.assertEquals(TEST_JSON, skaffoldFilesOutput.getJsonString());
  }

  @Test
  void testGetJsonString_empty() throws IOException {
    SkaffoldFilesOutput skaffoldFilesOutput = new SkaffoldFilesOutput();
    Assert.assertEquals(
        "{\"build\":[],\"inputs\":[],\"ignore\":[]}", skaffoldFilesOutput.getJsonString());
  }

  @Test
  void testConstructor_json() throws IOException {
    SkaffoldFilesOutput skaffoldFilesOutput = new SkaffoldFilesOutput(TEST_JSON);
    Assert.assertEquals(
        ImmutableList.of("buildFile1", "buildFile2"), skaffoldFilesOutput.getBuild());
    Assert.assertEquals(ImmutableList.of("input1", "input2"), skaffoldFilesOutput.getInputs());
    Assert.assertEquals(ImmutableList.of("ignore1", "ignore2"), skaffoldFilesOutput.getIgnore());
  }
}
