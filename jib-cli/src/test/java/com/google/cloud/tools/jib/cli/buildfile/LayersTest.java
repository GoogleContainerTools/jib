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

package com.google.cloud.tools.jib.cli.buildfile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.buildplan.FileEntriesLayer;
import com.google.cloud.tools.jib.api.buildplan.FileEntry;
import com.google.cloud.tools.jib.api.buildplan.FilePermissions;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.Assert;
import org.junit.Test;

/** Tests for {@link Layers}. */
public class LayersTest {

  private static final String LAYERS_TEST_RESOURCE_DIR = "buildfiles/layers/";

  public static List<FileEntriesLayer> parseLayers(Path testDir, int expectedLayerCount)
      throws IOException {
    Path layersSpecYaml = testDir.resolve("layers.yaml");
    List<FileEntriesLayer> layers =
        Layers.toLayers(
            layersSpecYaml.getParent(),
            new ObjectMapper(new YAMLFactory())
                .readValue(
                    Files.newBufferedReader(layersSpecYaml, Charsets.UTF_8), LayersSpec.class));

    Assert.assertEquals(expectedLayerCount, layers.size());
    return layers;
  }

  private static Path getLayersTestRoot(String testName) throws URISyntaxException {
    return Paths.get(Resources.getResource(LAYERS_TEST_RESOURCE_DIR + testName).toURI());
  }

  @Test
  public void testToLayers_properties() throws IOException, URISyntaxException {
    Path testRoot = getLayersTestRoot("propertiesTest");
    List<FileEntriesLayer> layers = parseLayers(testRoot, 4);

    checkLayer(
        layers.get(0),
        "level 0 passthrough",
        ImmutableSet.of(
            newEntry(testRoot, "dir", "/app", "700", 0, "0:0"),
            newEntry(testRoot, "dir/file.txt", "/app/file.txt", "000", 0, "0:0")));

    checkLayer(
        layers.get(1),
        "level 1 overrides",
        ImmutableSet.of(
            newEntry(testRoot, "dir", "/app", "711", 1000, "1:1"),
            newEntry(testRoot, "dir/file.txt", "/app/file.txt", "111", 1000, "1:1")));

    checkLayer(
        layers.get(2),
        "level 2 overrides",
        ImmutableSet.of(
            newEntry(testRoot, "dir", "/app", "722", 2000, "2:2"),
            newEntry(testRoot, "dir/file.txt", "/app/file.txt", "222", 2000, "2:2")));

    checkLayer(
        layers.get(3),
        "partial overrides",
        ImmutableSet.of(
            newEntry(testRoot, "dir", "/app", "711", 2000, "0:2"),
            newEntry(testRoot, "dir/file.txt", "/app/file.txt", "111", 2000, "0:2")));
  }

  @Test
  public void testToLayers_includeExcludes() throws IOException, URISyntaxException {
    Path testRoot = getLayersTestRoot("includesExcludesTest");
    List<FileEntriesLayer> layers = parseLayers(testRoot, 6);

    checkLayer(
        layers.get(0),
        "includes and excludes",
        ImmutableSet.of(
            newEntry(testRoot, "project", "/target/ie", "755", 0, "0:0"),
            newEntry(testRoot, "project/includedDir/", "/target/ie/includedDir/", "755", 0, "0:0"),
            newEntry(
                testRoot,
                "project/includedDir/include.me",
                "/target/ie/includedDir/include.me",
                "644",
                0,
                "0:0")));

    checkLayer(
        layers.get(1),
        "includes only",
        ImmutableSet.of(
            newEntry(testRoot, "project", "/target/io", "755", 0, "0:0"),
            newEntry(testRoot, "project/includedDir/", "/target/io/includedDir/", "755", 0, "0:0"),
            newEntry(
                testRoot,
                "project/includedDir/include.me",
                "/target/io/includedDir/include.me",
                "644",
                0,
                "0:0")));

    checkLayer(
        layers.get(2),
        "excludes only",
        ImmutableSet.of(
            newEntry(testRoot, "project", "/target/eo", "755", 0, "0:0"),
            newEntry(testRoot, "project/excludedDir", "/target/eo/excludedDir", "755", 0, "0:0"),
            newEntry(testRoot, "project/includedDir", "/target/eo/includedDir", "755", 0, "0:0"),
            newEntry(
                testRoot,
                "project/includedDir/include.me",
                "/target/eo/includedDir/include.me",
                "644",
                0,
                "0:0"),
            newEntry(testRoot, "project/wild.card", "/target/eo/wild.card", "644", 0, "0:0")));

    checkLayer(
        layers.get(3),
        "excludes only shortcut",
        ImmutableSet.of(
            newEntry(testRoot, "project", "/target/eo", "755", 0, "0:0"),
            newEntry(testRoot, "project/excludedDir", "/target/eo/excludedDir", "755", 0, "0:0"),
            newEntry(testRoot, "project/includedDir", "/target/eo/includedDir", "755", 0, "0:0"),
            newEntry(
                testRoot,
                "project/includedDir/include.me",
                "/target/eo/includedDir/include.me",
                "644",
                0,
                "0:0"),
            newEntry(testRoot, "project/wild.card", "/target/eo/wild.card", "644", 0, "0:0")));

    checkLayer(
        layers.get(4),
        "exclude dir and contents",
        ImmutableSet.of(
            newEntry(testRoot, "project", "/target/edac", "755", 0, "0:0"),
            newEntry(
                testRoot, "project/includedDir/", "/target/edac/includedDir/", "755", 0, "0:0"),
            newEntry(
                testRoot,
                "project/includedDir/include.me",
                "/target/edac/includedDir/include.me",
                "644",
                0,
                "0:0"),
            newEntry(testRoot, "project/wild.card", "/target/edac/wild.card", "644", 0, "0:0")));

    checkLayer(
        layers.get(5),
        "excludes only wrong",
        ImmutableSet.of(
            newEntry(testRoot, "project", "/target/eo", "755", 0, "0:0"),
            newEntry(testRoot, "project/excludedDir", "/target/eo/excludedDir", "755", 0, "0:0"),
            newEntry(
                testRoot,
                "project/excludedDir/exclude.me",
                "/target/eo/excludedDir/exclude.me",
                "644",
                0,
                "0:0"),
            newEntry(testRoot, "project/includedDir", "/target/eo/includedDir", "755", 0, "0:0"),
            newEntry(
                testRoot,
                "project/includedDir/include.me",
                "/target/eo/includedDir/include.me",
                "644",
                0,
                "0:0"),
            newEntry(testRoot, "project/wild.card", "/target/eo/wild.card", "644", 0, "0:0")));
  }

  @Test
  public void testToLayers_file() throws IOException, URISyntaxException {
    Path testRoot = getLayersTestRoot("fileTest/default");
    List<FileEntriesLayer> layers = parseLayers(testRoot, 1);

    checkLayer(
        layers.get(0),
        "default",
        ImmutableSet.of(
            newEntry(testRoot, "toFile.txt", "/target/toFile.txt", "644", 0, "0:0"),
            newEntry(testRoot, "toDir.txt", "/target/dir/toDir.txt", "644", 0, "0:0")));
  }

  @Test
  public void testToLayers_fileWithIncludes() throws IOException, URISyntaxException {
    Path testRoot = getLayersTestRoot("fileTest/failWithIncludes");
    try {
      parseLayers(testRoot, 0);
      Assert.fail();
    } catch (UnsupportedOperationException uoe) {
      Assert.assertEquals(
          "Cannot apply includes/excludes on single file copy directives.", uoe.getMessage());
    }
  }

  @Test
  public void testToLayers_fileWithExcludes() throws IOException, URISyntaxException {
    Path testRoot = getLayersTestRoot("fileTest/failWithExcludes");
    try {
      parseLayers(testRoot, 0);
      Assert.fail();
    } catch (UnsupportedOperationException uoe) {
      Assert.assertEquals(
          "Cannot apply includes/excludes on single file copy directives.", uoe.getMessage());
    }
  }

  private static FileEntry newEntry(
      Path testRoot,
      String src,
      String dest,
      String octalPermissions,
      int millis,
      String ownership) {
    return new FileEntry(
        testRoot.resolve(src),
        AbsoluteUnixPath.get(dest),
        FilePermissions.fromOctalString(octalPermissions),
        Instant.ofEpochMilli(millis),
        ownership);
  }

  private static void checkLayer(
      FileEntriesLayer layer, String expectedName, Set<FileEntry> expectedLayerEntries) {
    Assert.assertEquals(expectedName, layer.getName());

    try {
      Assert.assertEquals(expectedLayerEntries, ImmutableSet.copyOf(layer.getEntries()));
    } catch (AssertionError ae) {
      System.out.println("ACTUAL");
      layer
          .getEntries()
          .forEach(
              entry -> {
                System.out.println("src: " + entry.getSourceFile());
                System.out.println("dest: " + entry.getExtractionPath());
                System.out.println("permission: " + entry.getPermissions().toOctalString());
                System.out.println("time: " + entry.getModificationTime());
                System.out.println("ownership: " + entry.getOwnership());
              });
      System.out.println("EXCPECTED");
      expectedLayerEntries.forEach(
          entry -> {
            System.out.println("src: " + entry.getSourceFile());
            System.out.println("dest: " + entry.getExtractionPath());
            System.out.println("permission: " + entry.getPermissions().toOctalString());
            System.out.println("time: " + entry.getModificationTime());
            System.out.println("ownership: " + entry.getOwnership());
          });
      throw ae;
    }
  }

  @Test
  public void testToLayers_pathDoesNotExist() throws IOException, URISyntaxException {
    Path testRoot = getLayersTestRoot("pathDoesNotExist");
    try {
      parseLayers(testRoot, 0);
      Assert.fail();
    } catch (UnsupportedOperationException uoe) {
      Assert.assertEquals(
          "Cannot create FileLayers from non-file, non-directory: "
              + testRoot.resolve("something-that-does-not-exist").toString(),
          uoe.getMessage());
    }
  }

  @Test
  public void testToLayers_archiveLayersNotSupported() throws URISyntaxException, IOException {
    Path testRoot = getLayersTestRoot("archiveLayerTest");
    try {
      parseLayers(testRoot, 0);
      Assert.fail();
    } catch (UnsupportedOperationException uoe) {
      Assert.assertEquals("Only FileLayers are supported at this time.", uoe.getMessage());
    }
  }

  @Test
  public void testToLayers_writeToRoot() throws IOException, URISyntaxException {
    // this test defines the current behavior of writing to root, perhaps we should ignore
    // root at this level or we should ignore it at the builder level
    Path testRoot = getLayersTestRoot("writeToRoot");
    List<FileEntriesLayer> layers = parseLayers(testRoot, 2);

    checkLayer(
        layers.get(0),
        "root writer",
        ImmutableSet.of(
            newEntry(testRoot, "dir", "/", "755", 1000, ""),
            newEntry(testRoot, "dir/file.txt", "/file.txt", "644", 1000, "")));

    checkLayer(
        layers.get(1),
        "root parent fill",
        ImmutableSet.of(
            newEntry(testRoot, ".", "/", "755", 1000, ""),
            newEntry(testRoot, "./dir", "/dir", "755", 1000, ""),
            newEntry(testRoot, "./dir/file.txt", "/dir/file.txt", "644", 1000, "")));
  }
}
