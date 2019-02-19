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

import java.io.IOException;
import java.nio.file.Paths;
import org.junit.Assert;
import org.junit.Test;

/** Tests for {@link SkaffoldFilesOutput}. */
public class SkaffoldFilesOutputTest {

  @Test
  public void testGetJsonString() throws IOException {
    SkaffoldFilesOutput skaffoldFilesOutput = new SkaffoldFilesOutput();
    skaffoldFilesOutput.addBuildFile(Paths.get("buildFile1"));
    skaffoldFilesOutput.addBuildFile(Paths.get("buildFile2"));
    skaffoldFilesOutput.addInput(Paths.get("input1"));
    skaffoldFilesOutput.addInput(Paths.get("input2"));
    skaffoldFilesOutput.addIgnore(Paths.get("ignore1"));
    skaffoldFilesOutput.addIgnore(Paths.get("ignore2"));
    Assert.assertEquals(
        "{\"buildFiles\":[\"buildFile1\",\"buildFile2\"],\"inputs\":[\"input1\",\"input2\"],\"ignore\":[\"ignore1\",\"ignore2\"]}",
        skaffoldFilesOutput.getJsonString());
  }
}
