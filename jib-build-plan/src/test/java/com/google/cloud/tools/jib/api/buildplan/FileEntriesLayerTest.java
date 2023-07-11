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

package com.google.cloud.tools.jib.api.buildplan;

import com.google.common.collect.ImmutableSet;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import org.junit.Assert;
import org.junit.jupiter.api.Test;

/** Tests for {@link FileEntriesLayer}. */
class FileEntriesLayerTest {

  private static FileEntry defaultFileEntry(Path source, AbsoluteUnixPath destination) {
    return new FileEntry(
        source,
        destination,
        FileEntriesLayer.DEFAULT_FILE_PERMISSIONS_PROVIDER.get(source, destination),
        FileEntriesLayer.DEFAULT_MODIFICATION_TIME);
  }

  @Test
  void testAddEntryRecursive_defaults() throws IOException, URISyntaxException {
    Path testDirectory = Paths.get(Resources.getResource("core/layer").toURI()).toAbsolutePath();
    Path testFile = Paths.get(Resources.getResource("core/fileA").toURI());

    FileEntriesLayer layer =
        FileEntriesLayer.builder()
            .addEntryRecursive(testDirectory, AbsoluteUnixPath.get("/app/layer/"))
            .addEntryRecursive(testFile, AbsoluteUnixPath.get("/app/fileA"))
            .build();

    ImmutableSet<FileEntry> expectedLayerEntries =
        ImmutableSet.of(
            defaultFileEntry(testDirectory, AbsoluteUnixPath.get("/app/layer/")),
            defaultFileEntry(testDirectory.resolve("a"), AbsoluteUnixPath.get("/app/layer/a/")),
            defaultFileEntry(testDirectory.resolve("a/b"), AbsoluteUnixPath.get("/app/layer/a/b/")),
            defaultFileEntry(
                testDirectory.resolve("a/b/bar"), AbsoluteUnixPath.get("/app/layer/a/b/bar/")),
            defaultFileEntry(testDirectory.resolve("c/"), AbsoluteUnixPath.get("/app/layer/c")),
            defaultFileEntry(
                testDirectory.resolve("c/cat/"), AbsoluteUnixPath.get("/app/layer/c/cat")),
            defaultFileEntry(testDirectory.resolve("foo"), AbsoluteUnixPath.get("/app/layer/foo")),
            defaultFileEntry(testFile, AbsoluteUnixPath.get("/app/fileA")));

    Assert.assertEquals(expectedLayerEntries, ImmutableSet.copyOf(layer.getEntries()));
  }

  @Test
  void testAddEntryRecursive_otherFileEntryProperties() throws IOException, URISyntaxException {
    Path testDirectory = Paths.get(Resources.getResource("core/layer").toURI()).toAbsolutePath();
    Path testFile = Paths.get(Resources.getResource("core/fileA").toURI());

    FilePermissions permissions1 = FilePermissions.fromOctalString("111");
    FilePermissions permissions2 = FilePermissions.fromOctalString("777");
    Instant timestamp1 = Instant.ofEpochSecond(123);
    Instant timestamp2 = Instant.ofEpochSecond(987);
    String ownership1 = "root";
    String ownership2 = "nobody:65432";

    FilePermissionsProvider permissionsProvider =
        (source, destination) ->
            destination.toString().startsWith("/app/layer/a") ? permissions1 : permissions2;
    ModificationTimeProvider timestampProvider =
        (source, destination) ->
            destination.toString().startsWith("/app/layer/a") ? timestamp1 : timestamp2;
    OwnershipProvider ownershipProvider =
        (source, destination) ->
            destination.toString().startsWith("/app/layer/a") ? ownership1 : ownership2;

    FileEntriesLayer layer =
        FileEntriesLayer.builder()
            .addEntry(
                new FileEntry(
                    Paths.get("foo"), AbsoluteUnixPath.get("/foo"), permissions1, timestamp1))
            .addEntryRecursive(
                testDirectory,
                AbsoluteUnixPath.get("/app/layer/"),
                permissionsProvider,
                timestampProvider,
                ownershipProvider)
            .addEntryRecursive(
                testFile,
                AbsoluteUnixPath.get("/app/fileA"),
                permissionsProvider,
                timestampProvider,
                ownershipProvider)
            .build();

    ImmutableSet<FileEntry> expectedLayerEntries =
        ImmutableSet.of(
            new FileEntry(
                Paths.get("foo"), AbsoluteUnixPath.get("/foo"), permissions1, timestamp1, ""),
            new FileEntry(
                testDirectory,
                AbsoluteUnixPath.get("/app/layer/"),
                permissions2,
                timestamp2,
                ownership2),
            new FileEntry(
                testDirectory.resolve("a"),
                AbsoluteUnixPath.get("/app/layer/a/"),
                permissions1,
                timestamp1,
                ownership1),
            new FileEntry(
                testDirectory.resolve("a/b"),
                AbsoluteUnixPath.get("/app/layer/a/b/"),
                permissions1,
                timestamp1,
                ownership1),
            new FileEntry(
                testDirectory.resolve("a/b/bar"),
                AbsoluteUnixPath.get("/app/layer/a/b/bar/"),
                permissions1,
                timestamp1,
                ownership1),
            new FileEntry(
                testDirectory.resolve("c/"),
                AbsoluteUnixPath.get("/app/layer/c"),
                permissions2,
                timestamp2,
                ownership2),
            new FileEntry(
                testDirectory.resolve("c/cat/"),
                AbsoluteUnixPath.get("/app/layer/c/cat"),
                permissions2,
                timestamp2,
                ownership2),
            new FileEntry(
                testDirectory.resolve("foo"),
                AbsoluteUnixPath.get("/app/layer/foo"),
                permissions2,
                timestamp2,
                ownership2),
            new FileEntry(
                testFile,
                AbsoluteUnixPath.get("/app/fileA"),
                permissions2,
                timestamp2,
                ownership2));

    Assert.assertEquals(expectedLayerEntries, ImmutableSet.copyOf(layer.getEntries()));
  }
}
