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

package com.google.cloud.tools.jib.configuration;

import com.google.cloud.tools.jib.image.LayerEntry;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Assert;
import org.junit.Test;

/** Tests for {@link LayerConfiguration}. */
public class LayerConfigurationTest {

  @Test
  public void testAddEntryRecursive() throws IOException, URISyntaxException {
    Path testDirectory = Paths.get(Resources.getResource("layer").toURI()).toAbsolutePath();
    Path testFile = Paths.get(Resources.getResource("fileA").toURI());

    LayerConfiguration layerConfiguration =
        LayerConfiguration.builder()
            .addEntryRecursive(testDirectory, Paths.get("/app/layer/"))
            .addEntryRecursive(testFile, Paths.get("/app/fileA"))
            .build();

    ImmutableSet<LayerEntry> expectedLayerEntries =
        ImmutableSet.of(
            new LayerEntry(testDirectory, Paths.get("/app/layer/")),
            new LayerEntry(testDirectory.resolve("a"), Paths.get("/app/layer/a/")),
            new LayerEntry(testDirectory.resolve("a/b"), Paths.get("/app/layer/a/b/")),
            new LayerEntry(testDirectory.resolve("a/b/bar"), Paths.get("/app/layer/a/b/bar/")),
            new LayerEntry(testDirectory.resolve("c/"), Paths.get("/app/layer/c")),
            new LayerEntry(testDirectory.resolve("c/cat/"), Paths.get("/app/layer/c/cat")),
            new LayerEntry(testDirectory.resolve("foo"), Paths.get("/app/layer/foo")),
            new LayerEntry(testFile, Paths.get("/app/fileA")));

    Assert.assertEquals(
        expectedLayerEntries, ImmutableSet.copyOf(layerConfiguration.getLayerEntries()));
  }
}
