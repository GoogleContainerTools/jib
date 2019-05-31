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

package com.google.cloud.tools.jib.api;

import com.google.common.collect.ImmutableSet;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.function.BiFunction;
import org.junit.Assert;
import org.junit.Test;

/** Tests for {@link LayerConfiguration}. */
public class LayerConfigurationTest {

  private static LayerEntry defaultLayerEntry(Path source, AbsoluteUnixPath destination) {
    return new LayerEntry(
        source,
        destination,
        LayerConfiguration.DEFAULT_FILE_PERMISSIONS_PROVIDER.apply(source, destination),
        LayerConfiguration.DEFAULT_MODIFIED_TIME);
  }

  @Test
  public void testAddEntryRecursive_defaults() throws IOException, URISyntaxException {
    Path testDirectory = Paths.get(Resources.getResource("core/layer").toURI()).toAbsolutePath();
    Path testFile = Paths.get(Resources.getResource("core/fileA").toURI());

    LayerConfiguration layerConfiguration =
        LayerConfiguration.builder()
            .addEntryRecursive(testDirectory, AbsoluteUnixPath.get("/app/layer/"))
            .addEntryRecursive(testFile, AbsoluteUnixPath.get("/app/fileA"))
            .build();

    ImmutableSet<LayerEntry> expectedLayerEntries =
        ImmutableSet.of(
            defaultLayerEntry(testDirectory, AbsoluteUnixPath.get("/app/layer/")),
            defaultLayerEntry(testDirectory.resolve("a"), AbsoluteUnixPath.get("/app/layer/a/")),
            defaultLayerEntry(
                testDirectory.resolve("a/b"), AbsoluteUnixPath.get("/app/layer/a/b/")),
            defaultLayerEntry(
                testDirectory.resolve("a/b/bar"), AbsoluteUnixPath.get("/app/layer/a/b/bar/")),
            defaultLayerEntry(testDirectory.resolve("c/"), AbsoluteUnixPath.get("/app/layer/c")),
            defaultLayerEntry(
                testDirectory.resolve("c/cat/"), AbsoluteUnixPath.get("/app/layer/c/cat")),
            defaultLayerEntry(testDirectory.resolve("foo"), AbsoluteUnixPath.get("/app/layer/foo")),
            defaultLayerEntry(testFile, AbsoluteUnixPath.get("/app/fileA")));

    Assert.assertEquals(
        expectedLayerEntries, ImmutableSet.copyOf(layerConfiguration.getLayerEntries()));
  }

  @Test
  public void testAddEntryRecursive_permissionsAndTimestamps()
      throws IOException, URISyntaxException {
    Path testDirectory = Paths.get(Resources.getResource("core/layer").toURI()).toAbsolutePath();
    Path testFile = Paths.get(Resources.getResource("core/fileA").toURI());

    FilePermissions permissions1 = FilePermissions.fromOctalString("111");
    FilePermissions permissions2 = FilePermissions.fromOctalString("777");
    Instant timestamp1 = Instant.ofEpochSecond(123);
    Instant timestamp2 = Instant.ofEpochSecond(987);

    BiFunction<Path, AbsoluteUnixPath, FilePermissions> permissionsProvider =
        (source, destination) ->
            destination.toString().startsWith("/app/layer/a") ? permissions1 : permissions2;
    BiFunction<Path, AbsoluteUnixPath, Instant> timestampProvider =
        (source, destination) ->
            destination.toString().startsWith("/app/layer/a") ? timestamp1 : timestamp2;

    LayerConfiguration layerConfiguration =
        LayerConfiguration.builder()
            .addEntryRecursive(
                testDirectory,
                AbsoluteUnixPath.get("/app/layer/"),
                permissionsProvider,
                timestampProvider)
            .addEntryRecursive(
                testFile,
                AbsoluteUnixPath.get("/app/fileA"),
                permissionsProvider,
                timestampProvider)
            .build();

    ImmutableSet<LayerEntry> expectedLayerEntries =
        ImmutableSet.of(
            new LayerEntry(
                testDirectory, AbsoluteUnixPath.get("/app/layer/"), permissions2, timestamp2),
            new LayerEntry(
                testDirectory.resolve("a"),
                AbsoluteUnixPath.get("/app/layer/a/"),
                permissions1,
                timestamp1),
            new LayerEntry(
                testDirectory.resolve("a/b"),
                AbsoluteUnixPath.get("/app/layer/a/b/"),
                permissions1,
                timestamp1),
            new LayerEntry(
                testDirectory.resolve("a/b/bar"),
                AbsoluteUnixPath.get("/app/layer/a/b/bar/"),
                permissions1,
                timestamp1),
            new LayerEntry(
                testDirectory.resolve("c/"),
                AbsoluteUnixPath.get("/app/layer/c"),
                permissions2,
                timestamp2),
            new LayerEntry(
                testDirectory.resolve("c/cat/"),
                AbsoluteUnixPath.get("/app/layer/c/cat"),
                permissions2,
                timestamp2),
            new LayerEntry(
                testDirectory.resolve("foo"),
                AbsoluteUnixPath.get("/app/layer/foo"),
                permissions2,
                timestamp2),
            new LayerEntry(testFile, AbsoluteUnixPath.get("/app/fileA"), permissions2, timestamp2));

    Assert.assertEquals(
        expectedLayerEntries, ImmutableSet.copyOf(layerConfiguration.getLayerEntries()));
  }
}
