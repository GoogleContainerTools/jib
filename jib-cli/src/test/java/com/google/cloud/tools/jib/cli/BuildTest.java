/*
 * Copyright 2020 Google LLC.
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

package com.google.cloud.tools.jib.cli;

import org.junit.Assert;
import org.junit.Test;
import picocli.CommandLine;

/** Tests for {@link Build}. */
public class BuildTest {
  @Test
  public void testIncomplete() {
    try {
      CommandLine.populateCommand(new Build());
      Assert.fail("should have errored with incomplete arguments");
    } catch (CommandLine.MissingParameterException ex) {
      Assert.assertEquals(
          "Missing required parameters: 'base-image', 'destination-image'", ex.getMessage());
    }
  }
}
