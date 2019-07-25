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

package com.google.cloud.tools.jib.cache;

import com.google.cloud.tools.jib.api.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.DescriptorDigest;
import com.google.cloud.tools.jib.api.FilePermissions;
import com.google.cloud.tools.jib.api.LayerConfiguration;
import com.google.cloud.tools.jib.api.LayerEntry;
import com.google.cloud.tools.jib.cache.LayerEntriesSelector.LayerEntryTemplate;
import com.google.cloud.tools.jib.hash.Digests;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Tests for {@link LayerEntriesSelector}. */
public class LayerEntriesSelectorTest {

  private static LayerEntry defaultLayerEntry(Path source, AbsoluteUnixPath destination) {
    return new LayerEntry(
        source,
        destination,
        LayerConfiguration.DEFAULT_FILE_PERMISSIONS_PROVIDER.apply(source, destination),
        LayerConfiguration.DEFAULT_MODIFICATION_TIME);
  }

  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();
  private ImmutableList<LayerEntry> outOfOrderLayerEntries;
  private ImmutableList<LayerEntry> inOrderLayerEntries;

  private static ImmutableList<LayerEntryTemplate> toLayerEntryTemplates(
      ImmutableList<LayerEntry> layerEntries) throws IOException {
    ImmutableList.Builder<LayerEntryTemplate> builder = ImmutableList.builder();
    for (LayerEntry layerEntry : layerEntries) {
      builder.add(new LayerEntryTemplate(layerEntry));
    }
    return builder.build();
  }

  @Before
  public void setUp() throws IOException {
    Path folder = temporaryFolder.newFolder().toPath();
    Path file1 = Files.createDirectory(folder.resolve("files"));
    Path file2 = Files.createFile(folder.resolve("files").resolve("two"));
    Path file3 = Files.createFile(folder.resolve("gile"));

    LayerEntry testLayerEntry1 = defaultLayerEntry(file1, AbsoluteUnixPath.get("/extraction/path"));
    LayerEntry testLayerEntry2 = defaultLayerEntry(file2, AbsoluteUnixPath.get("/extraction/path"));
    LayerEntry testLayerEntry3 = defaultLayerEntry(file3, AbsoluteUnixPath.get("/extraction/path"));
    LayerEntry testLayerEntry4 =
        new LayerEntry(
            file3,
            AbsoluteUnixPath.get("/extraction/path"),
            FilePermissions.fromOctalString("755"),
            LayerConfiguration.DEFAULT_MODIFICATION_TIME);
    LayerEntry testLayerEntry5 =
        defaultLayerEntry(file3, AbsoluteUnixPath.get("/extraction/patha"));
    LayerEntry testLayerEntry6 =
        new LayerEntry(
            file3,
            AbsoluteUnixPath.get("/extraction/patha"),
            FilePermissions.fromOctalString("755"),
            LayerConfiguration.DEFAULT_MODIFICATION_TIME);

    outOfOrderLayerEntries =
        ImmutableList.of(
            testLayerEntry4,
            testLayerEntry2,
            testLayerEntry6,
            testLayerEntry3,
            testLayerEntry1,
            testLayerEntry5);
    inOrderLayerEntries =
        ImmutableList.of(
            testLayerEntry1,
            testLayerEntry2,
            testLayerEntry3,
            testLayerEntry4,
            testLayerEntry5,
            testLayerEntry6);
  }

  @Test
  public void testLayerEntryTemplate_compareTo() throws IOException {
    Assert.assertEquals(
        toLayerEntryTemplates(inOrderLayerEntries),
        ImmutableList.sortedCopyOf(toLayerEntryTemplates(outOfOrderLayerEntries)));
  }

  @Test
  public void testToSortedJsonTemplates() throws IOException {
    Assert.assertEquals(
        toLayerEntryTemplates(inOrderLayerEntries),
        LayerEntriesSelector.toSortedJsonTemplates(outOfOrderLayerEntries));
  }

  @Test
  public void testGenerateSelector_empty() throws IOException {
    DescriptorDigest expectedSelector = Digests.computeJsonDigest(ImmutableList.of());
    Assert.assertEquals(
        expectedSelector, LayerEntriesSelector.generateSelector(ImmutableList.of()));
  }

  @Test
  public void testGenerateSelector() throws IOException {
    DescriptorDigest expectedSelector =
        Digests.computeJsonDigest(toLayerEntryTemplates(inOrderLayerEntries));
    Assert.assertEquals(
        expectedSelector, LayerEntriesSelector.generateSelector(outOfOrderLayerEntries));
  }

  @Test
  public void testGenerateSelector_sourceModificationTimeChanged() throws IOException {
    Path layerFile = temporaryFolder.newFolder("testFolder").toPath().resolve("file");
    Files.setLastModifiedTime(layerFile, FileTime.from(Instant.EPOCH));
    LayerEntry layerEntry = defaultLayerEntry(layerFile, AbsoluteUnixPath.get("/extraction/path"));
    DescriptorDigest expectedSelector =
        LayerEntriesSelector.generateSelector(ImmutableList.of(layerEntry));

    // Verify that changing source modification time generates a different selector
    Files.setLastModifiedTime(layerFile, FileTime.from(Instant.ofEpochSecond(1)));
    Assert.assertNotEquals(
        expectedSelector, LayerEntriesSelector.generateSelector(ImmutableList.of(layerEntry)));

    // Verify that changing source modification time back generates same selector
    Files.setLastModifiedTime(layerFile, FileTime.from(Instant.EPOCH));
    Assert.assertEquals(
        expectedSelector, LayerEntriesSelector.generateSelector(ImmutableList.of(layerEntry)));
  }

  @Test
  public void testGenerateSelector_targetModificationTimeChanged() throws IOException {
    Path layerFile = temporaryFolder.newFile().toPath();
    AbsoluteUnixPath pathInContainer = AbsoluteUnixPath.get("/bar");
    FilePermissions permissions = FilePermissions.fromOctalString("111");

    LayerEntry layerEntry1 = new LayerEntry(layerFile, pathInContainer, permissions, Instant.now());
    LayerEntry layerEntry2 = new LayerEntry(layerFile, pathInContainer, permissions, Instant.EPOCH);

    // Verify that different target modification times generate different selectors
    Assert.assertNotEquals(
        LayerEntriesSelector.generateSelector(ImmutableList.of(layerEntry1)),
        LayerEntriesSelector.generateSelector(ImmutableList.of(layerEntry2)));
  }

  @Test
  public void testGenerateSelector_permissionsModified() throws IOException {
    Path layerFile = temporaryFolder.newFolder("testFolder").toPath().resolve("file");
    Files.write(layerFile, "hello".getBytes(StandardCharsets.UTF_8));
    LayerEntry layerEntry111 =
        new LayerEntry(
            layerFile,
            AbsoluteUnixPath.get("/extraction/path"),
            FilePermissions.fromOctalString("111"),
            LayerConfiguration.DEFAULT_MODIFICATION_TIME);
    LayerEntry layerEntry222 =
        new LayerEntry(
            layerFile,
            AbsoluteUnixPath.get("/extraction/path"),
            FilePermissions.fromOctalString("222"),
            LayerConfiguration.DEFAULT_MODIFICATION_TIME);

    // Verify that changing permissions generates a different selector
    Assert.assertNotEquals(
        LayerEntriesSelector.generateSelector(ImmutableList.of(layerEntry111)),
        LayerEntriesSelector.generateSelector(ImmutableList.of(layerEntry222)));
  }
}
