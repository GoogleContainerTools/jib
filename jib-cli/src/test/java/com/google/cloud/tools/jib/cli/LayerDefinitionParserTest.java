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

package com.google.cloud.tools.jib.cli;

import com.google.cloud.tools.jib.api.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.FilePermissions;
import com.google.cloud.tools.jib.api.LayerConfiguration;
import com.google.cloud.tools.jib.api.LayerEntry;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import picocli.CommandLine;

/** Tests for {@link LayerDefinitionParser}. */
public class LayerDefinitionParserTest {
  private LayerDefinitionParser fixture = new LayerDefinitionParser();

  @Rule public final TemporaryFolder temporaryFolder = new org.junit.rules.TemporaryFolder();

  @Test
  public void testSource() throws Exception {
    LayerConfiguration result = fixture.convert("foo");
    Assert.assertEquals("", result.getName());
    Assert.assertEquals(1, result.getLayerEntries().size());
    LayerEntry layerEntry = result.getLayerEntries().get(0);
    Assert.assertEquals(Paths.get("foo"), layerEntry.getSourceFile());
    Assert.assertEquals(AbsoluteUnixPath.get("/"), layerEntry.getExtractionPath());
    Assert.assertEquals(
        Instant.EPOCH.plus(Duration.ofSeconds(1)), layerEntry.getModificationTime());
    Assert.assertEquals(FilePermissions.DEFAULT_FILE_PERMISSIONS, layerEntry.getPermissions());
  }

  @Test
  public void testSourceDestination() throws Exception {
    LayerConfiguration result = fixture.convert("foo:/dest");
    Assert.assertEquals("", result.getName());
    Assert.assertEquals(1, result.getLayerEntries().size());
    LayerEntry layerEntry = result.getLayerEntries().get(0);
    Assert.assertEquals(Paths.get("foo"), layerEntry.getSourceFile());
    Assert.assertEquals(AbsoluteUnixPath.get("/dest"), layerEntry.getExtractionPath());
    Assert.assertEquals(
        Instant.EPOCH.plus(Duration.ofSeconds(1)), layerEntry.getModificationTime());
    Assert.assertEquals(FilePermissions.DEFAULT_FILE_PERMISSIONS, layerEntry.getPermissions());
  }

  @Test
  public void testSourceDestinationWithInvalidDirective() throws Exception {
    try {
      fixture.convert("foo:/dest:baz=bop");
      Assert.fail("Should have errored on invalid attribute");
    } catch (CommandLine.TypeConversionException ex) {
      Assert.assertEquals("unknown layer configuration directive: baz", ex.getMessage());
    }
  }

  @Test
  public void testSourceDestinationName() throws Exception {
    LayerConfiguration result = fixture.convert("foo:/dest:name=name=name");
    Assert.assertEquals("name=name", result.getName());
    Assert.assertEquals(1, result.getLayerEntries().size());
    LayerEntry layerEntry = result.getLayerEntries().get(0);
    Assert.assertEquals(Paths.get("foo"), layerEntry.getSourceFile());
    Assert.assertEquals(AbsoluteUnixPath.get("/dest"), layerEntry.getExtractionPath());
    Assert.assertEquals(
        Instant.EPOCH.plus(Duration.ofSeconds(1)), layerEntry.getModificationTime());
    Assert.assertEquals(FilePermissions.DEFAULT_FILE_PERMISSIONS, layerEntry.getPermissions());
  }

  @Test
  public void testSourceDestinationPermissions() throws Exception {
    File root = temporaryFolder.getRoot();
    File subdir = new File(root, "sub");
    Assert.assertTrue(subdir.mkdir());
    File file = new File(subdir, "file.txt");
    Files.copy(new ByteArrayInputStream("foo".getBytes(StandardCharsets.UTF_8)), file.toPath());

    LayerConfiguration result = fixture.convert(root.toString() + ":/dest:permissions=111/222");
    Assert.assertEquals(3, result.getLayerEntries().size());

    LayerEntry layerEntry = result.getLayerEntries().get(0);
    Assert.assertEquals(root.toPath(), layerEntry.getSourceFile());
    Assert.assertEquals(AbsoluteUnixPath.get("/dest"), layerEntry.getExtractionPath());
    Assert.assertEquals(
        Instant.EPOCH.plus(Duration.ofSeconds(1)), layerEntry.getModificationTime());
    Assert.assertEquals(FilePermissions.fromOctalString("222"), layerEntry.getPermissions());

    layerEntry = result.getLayerEntries().get(1);
    Assert.assertEquals(subdir.toPath(), layerEntry.getSourceFile());
    Assert.assertEquals(AbsoluteUnixPath.get("/dest/sub"), layerEntry.getExtractionPath());
    Assert.assertEquals(
        Instant.EPOCH.plus(Duration.ofSeconds(1)), layerEntry.getModificationTime());
    Assert.assertEquals(FilePermissions.fromOctalString("222"), layerEntry.getPermissions());

    layerEntry = result.getLayerEntries().get(2);
    Assert.assertEquals(file.toPath(), layerEntry.getSourceFile());
    Assert.assertEquals(AbsoluteUnixPath.get("/dest/sub/file.txt"), layerEntry.getExtractionPath());
    Assert.assertEquals(
        Instant.EPOCH.plus(Duration.ofSeconds(1)), layerEntry.getModificationTime());
    Assert.assertEquals(FilePermissions.fromOctalString("111"), layerEntry.getPermissions());
  }

  @Test
  public void testSourceDestinationTimestamps() throws Exception {
    File root = temporaryFolder.getRoot();
    File subdir = new File(root, "sub");
    Assert.assertTrue(subdir.mkdir());
    File file = new File(subdir, "file.txt");
    Files.copy(new ByteArrayInputStream("foo".getBytes(StandardCharsets.UTF_8)), file.toPath());

    LayerConfiguration result = fixture.convert(root.toString() + ":/dest:timestamps=actual");
    Assert.assertEquals(3, result.getLayerEntries().size());

    LayerEntry layerEntry = result.getLayerEntries().get(0);
    Assert.assertEquals(root.toPath(), layerEntry.getSourceFile());
    Assert.assertEquals(AbsoluteUnixPath.get("/dest"), layerEntry.getExtractionPath());
    Assert.assertEquals(root.lastModified(), layerEntry.getModificationTime().toEpochMilli());

    layerEntry = result.getLayerEntries().get(1);
    Assert.assertEquals(subdir.toPath(), layerEntry.getSourceFile());
    Assert.assertEquals(AbsoluteUnixPath.get("/dest/sub"), layerEntry.getExtractionPath());
    Assert.assertEquals(subdir.lastModified(), layerEntry.getModificationTime().toEpochMilli());

    layerEntry = result.getLayerEntries().get(2);
    Assert.assertEquals(file.toPath(), layerEntry.getSourceFile());
    Assert.assertEquals(AbsoluteUnixPath.get("/dest/sub/file.txt"), layerEntry.getExtractionPath());
    Assert.assertEquals(file.lastModified(), layerEntry.getModificationTime().toEpochMilli());
  }
}
