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

import com.google.cloud.tools.jib.blob.Blobs;
import com.google.cloud.tools.jib.filesystem.AbsoluteUnixPath;
import com.google.cloud.tools.jib.image.DescriptorDigest;
import com.google.cloud.tools.jib.image.LayerEntry;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mockito;

/** Tests for {@link LastModifiedTimeMetadata}. */
public class LastModifiedTimeMetadataTest {

  private static LayerEntry copyFile(Path source, Path destination, FileTime newLastModifiedTime)
      throws IOException {
    Files.createDirectories(destination.getParent());
    Files.copy(source, destination);
    Files.setLastModifiedTime(destination, newLastModifiedTime);
    return new LayerEntry(destination, AbsoluteUnixPath.get("/ignored"));
  }

  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  private List<LayerEntry> layerEntries = new ArrayList<>();

  @Before
  public void setUp() throws IOException, URISyntaxException {
    Path originalDirectory = Paths.get(Resources.getResource("layer").toURI());
    Path directory = temporaryFolder.newFolder().toPath();

    layerEntries.add(
        copyFile(
            originalDirectory.resolve("a/b/bar"),
            directory.resolve("a/b/bar"),
            FileTime.fromMillis(1000)));
    layerEntries.add(
        copyFile(
            originalDirectory.resolve("c/cat"),
            directory.resolve("c/cat"),
            FileTime.fromMillis(2000)));
    layerEntries.add(
        copyFile(
            originalDirectory.resolve("foo"), directory.resolve("foo"), FileTime.fromMillis(1500)));
  }

  @Test
  public void testGetLastModifiedTime_layerEntries() throws IOException {
    Assert.assertEquals(
        FileTime.fromMillis(2000),
        LastModifiedTimeMetadata.getLastModifiedTime(ImmutableList.copyOf(layerEntries)));
  }

  @Test
  public void testGetLastModifiedTime_noEntries() throws IOException {
    Assert.assertEquals(
        FileTime.from(Instant.MIN),
        LastModifiedTimeMetadata.getLastModifiedTime(ImmutableList.of()));
  }

  @Test
  public void testGetLastModifiedTime_cacheEntry() throws IOException {
    DescriptorDigest ignored = Mockito.mock(DescriptorDigest.class);
    CacheEntry cacheEntry =
        DefaultCacheEntry.builder()
            .setLayerDigest(ignored)
            .setLayerDiffId(ignored)
            .setLayerSize(0)
            .setLayerBlob(Blobs.from("ignored"))
            .setMetadataBlob(Blobs.from(Instant.ofEpochMilli(1000).toString()))
            .build();
    Assert.assertEquals(
        FileTime.from(Instant.ofEpochMilli(1000)),
        LastModifiedTimeMetadata.getLastModifiedTime(cacheEntry).orElseThrow(AssertionError::new));
  }

  @Test
  public void testGetLastModifiedTime_cacheEntry_noMetadata() throws IOException {
    DescriptorDigest ignored = Mockito.mock(DescriptorDigest.class);
    CacheEntry cacheEntry =
        DefaultCacheEntry.builder()
            .setLayerDigest(ignored)
            .setLayerDiffId(ignored)
            .setLayerSize(0)
            .setLayerBlob(Blobs.from("ignored"))
            .build();
    Assert.assertFalse(LastModifiedTimeMetadata.getLastModifiedTime(cacheEntry).isPresent());
  }

  @Test
  public void testGenerateMetadata() throws IOException {
    Assert.assertEquals(
        Instant.ofEpochMilli(2000).toString(),
        Blobs.writeToString(
            LastModifiedTimeMetadata.generateMetadata(ImmutableList.copyOf(layerEntries))));
  }
}
