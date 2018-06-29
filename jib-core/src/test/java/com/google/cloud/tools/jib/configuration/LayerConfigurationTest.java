/*
 * Copyright 2018 Google LLC. All rights reserved.
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

package com.google.cloud.tools.jib.configuration;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

/** Tests for {@link LayerConfiguration}. */
public class LayerConfigurationTest {

  @Test
  public void testBuilder_pass() {
    List<Path> expectedSourceFiles = Collections.singletonList(Paths.get("some/path"));
    LayerConfiguration layerConfiguration =
        LayerConfiguration.builder()
            .setSourceFiles(expectedSourceFiles)
            .setDestinationOnImage("some/destination")
            .build();
    Assert.assertEquals(expectedSourceFiles, layerConfiguration.getSourceFiles());
    Assert.assertEquals("some/destination", layerConfiguration.getDestinationOnImage());
  }

  @Test
  public void testBuilder_invalidArguments() {
    try {
      LayerConfiguration.builder().setSourceFiles(Collections.singletonList(null));
      Assert.fail("Should have thrown exception");

    } catch (IllegalArgumentException ex) {
      // pass
    }
  }

  @Test
  public void testBuilder_missingRequiredParameters() {
    try {
      LayerConfiguration.builder().setDestinationOnImage("some/destination").build();
      Assert.fail("Should have thrown exception");

    } catch (IllegalStateException ex) {
      Assert.assertEquals("Required fields should not be null", ex.getMessage());
    }
  }
}
