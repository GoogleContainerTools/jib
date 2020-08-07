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

import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.buildplan.FileEntriesLayer;
import com.google.cloud.tools.jib.api.buildplan.FileEntry;
import com.google.cloud.tools.jib.api.buildplan.FilePermissions;
import com.google.cloud.tools.jib.api.buildplan.FilePermissionsProvider;
import com.google.cloud.tools.jib.api.buildplan.ModificationTimeProvider;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import picocli.CommandLine;

/** Tests for {@link LayerDefinitionParser}. */
public class LayerDefinitionParserTest {
  private LayerDefinitionParser fixture = new LayerDefinitionParser();

  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void testParseTimestampsDirective_actual() {
    MatcherAssert.assertThat(
        LayerDefinitionParser.parseTimestampsDirective("actual"),
        CoreMatchers.instanceOf(ActualTimestampProvider.class));
  }

  @Test
  public void testParseTimestampsDirective_secondsSinceEpoch() {
    ModificationTimeProvider provider = LayerDefinitionParser.parseTimestampsDirective("1");
    MatcherAssert.assertThat(provider, CoreMatchers.instanceOf(FixedTimestampProvider.class));
    Assert.assertEquals(Instant.ofEpochSecond(1), ((FixedTimestampProvider) provider).fixed);
  }

  @Test
  public void testParseTimestampsDirective_is8601Date() {
    ModificationTimeProvider provider =
        LayerDefinitionParser.parseTimestampsDirective("1970-01-01T00:00:01.000Z");
    MatcherAssert.assertThat(provider, CoreMatchers.instanceOf(FixedTimestampProvider.class));
    Assert.assertEquals(Instant.ofEpochSecond(1), ((FixedTimestampProvider) provider).fixed);
  }

  @Test
  public void testParseTimestampsDirective_invalid() {
    try {
      LayerDefinitionParser.parseTimestampsDirective("invalid");
      Assert.fail();
    } catch (RuntimeException ex) {
      MatcherAssert.assertThat(ex, CoreMatchers.instanceOf(DateTimeParseException.class));
    }
  }

  @Test
  public void testParsePermissionsDirective_actual() {
    MatcherAssert.assertThat(
        LayerDefinitionParser.parsePermissionsDirective("actual"),
        CoreMatchers.instanceOf(ActualPermissionsProvider.class));
  }

  @Test
  public void testParsePermissionsDirective_fileOnly() {
    FilePermissionsProvider provider = LayerDefinitionParser.parsePermissionsDirective("555");
    MatcherAssert.assertThat(provider, CoreMatchers.instanceOf(FixedPermissionsProvider.class));
    Assert.assertEquals(
        0555, ((FixedPermissionsProvider) provider).filePermissions.getPermissionBits());
    Assert.assertSame(
        FilePermissions.DEFAULT_FOLDER_PERMISSIONS,
        ((FixedPermissionsProvider) provider).directoryPermissions);
  }

  @Test
  public void testParsePermissionsDirective_fileOAndDirectory() {
    FilePermissionsProvider provider = LayerDefinitionParser.parsePermissionsDirective("555:666");
    MatcherAssert.assertThat(provider, CoreMatchers.instanceOf(FixedPermissionsProvider.class));
    Assert.assertEquals(
        0555, ((FixedPermissionsProvider) provider).filePermissions.getPermissionBits());
    Assert.assertEquals(
        0666, ((FixedPermissionsProvider) provider).directoryPermissions.getPermissionBits());
  }

  @Test
  public void testParsePermissionsDirective_nonOctal() {
    try {
      LayerDefinitionParser.parsePermissionsDirective("811:922");
      Assert.fail();
    } catch (RuntimeException ex) {
      MatcherAssert.assertThat(ex, CoreMatchers.instanceOf(IllegalArgumentException.class));
    }
  }

  @Test
  public void testParsePermissionsDirective_invalid() {
    try {
      LayerDefinitionParser.parsePermissionsDirective("invalid");
      Assert.fail();
    } catch (RuntimeException ex) {
      MatcherAssert.assertThat(ex, CoreMatchers.instanceOf(IllegalArgumentException.class));
    }
  }

  @Test
  public void testConvert_sourceAndName() throws Exception {
    FileEntriesLayer result = fixture.convert("foo");
    Assert.assertEquals("", result.getName());
    Assert.assertEquals(1, result.getEntries().size());
    FileEntry layerEntry = result.getEntries().get(0);
    Assert.assertEquals(Paths.get("foo"), layerEntry.getSourceFile());
    Assert.assertEquals(AbsoluteUnixPath.get("/"), layerEntry.getExtractionPath());
    Assert.assertEquals(
        Instant.EPOCH.plus(Duration.ofSeconds(1)), layerEntry.getModificationTime());
    Assert.assertEquals(FilePermissions.DEFAULT_FILE_PERMISSIONS, layerEntry.getPermissions());
  }

  @Test
  public void testConvert_sourceDestination() throws Exception {
    FileEntriesLayer result = fixture.convert("foo,/dest");
    Assert.assertEquals("", result.getName());
    Assert.assertEquals(1, result.getEntries().size());
    FileEntry layerEntry = result.getEntries().get(0);
    Assert.assertEquals(Paths.get("foo"), layerEntry.getSourceFile());
    Assert.assertEquals(AbsoluteUnixPath.get("/dest"), layerEntry.getExtractionPath());
    Assert.assertEquals(
        Instant.EPOCH.plus(Duration.ofSeconds(1)), layerEntry.getModificationTime());
    Assert.assertEquals(FilePermissions.DEFAULT_FILE_PERMISSIONS, layerEntry.getPermissions());
  }

  @Test
  public void testConvert_sourceDestinationWithInvalidDirective() throws Exception {
    try {
      fixture.convert("foo,/dest,baz=bop");
      Assert.fail("Should have errored on invalid attribute");
    } catch (CommandLine.TypeConversionException ex) {
      Assert.assertEquals("unknown layer configuration directive: baz", ex.getMessage());
    }
  }

  @Test
  public void testConvert_sourceDestinationName() throws Exception {
    FileEntriesLayer result = fixture.convert("foo,/dest,name=name=name");
    Assert.assertEquals("name=name", result.getName());
    Assert.assertEquals(1, result.getEntries().size());
    FileEntry layerEntry = result.getEntries().get(0);
    Assert.assertEquals(Paths.get("foo"), layerEntry.getSourceFile());
    Assert.assertEquals(AbsoluteUnixPath.get("/dest"), layerEntry.getExtractionPath());
    Assert.assertEquals(
        Instant.EPOCH.plus(Duration.ofSeconds(1)), layerEntry.getModificationTime());
    Assert.assertEquals(FilePermissions.DEFAULT_FILE_PERMISSIONS, layerEntry.getPermissions());
  }

  @Test
  public void testConvert_sourceDestinationPermissions() throws Exception {
    File root = temporaryFolder.getRoot();
    File subdir = new File(root, "sub");
    Assert.assertTrue(subdir.mkdir());
    File file = new File(subdir, "file.txt");
    Files.copy(new ByteArrayInputStream("foo".getBytes(StandardCharsets.UTF_8)), file.toPath());

    FileEntriesLayer result = fixture.convert(root.toString() + ",/dest,permissions=111:222");
    Assert.assertEquals(3, result.getEntries().size());

    FileEntry layerEntry = result.getEntries().get(0);
    Assert.assertEquals(root.toPath(), layerEntry.getSourceFile());
    Assert.assertEquals(AbsoluteUnixPath.get("/dest"), layerEntry.getExtractionPath());
    Assert.assertEquals(
        Instant.EPOCH.plus(Duration.ofSeconds(1)), layerEntry.getModificationTime());
    Assert.assertEquals(FilePermissions.fromOctalString("222"), layerEntry.getPermissions());

    layerEntry = result.getEntries().get(1);
    Assert.assertEquals(subdir.toPath(), layerEntry.getSourceFile());
    Assert.assertEquals(AbsoluteUnixPath.get("/dest/sub"), layerEntry.getExtractionPath());
    Assert.assertEquals(
        Instant.EPOCH.plus(Duration.ofSeconds(1)), layerEntry.getModificationTime());
    Assert.assertEquals(FilePermissions.fromOctalString("222"), layerEntry.getPermissions());

    layerEntry = result.getEntries().get(2);
    Assert.assertEquals(file.toPath(), layerEntry.getSourceFile());
    Assert.assertEquals(AbsoluteUnixPath.get("/dest/sub/file.txt"), layerEntry.getExtractionPath());
    Assert.assertEquals(
        Instant.EPOCH.plus(Duration.ofSeconds(1)), layerEntry.getModificationTime());
    Assert.assertEquals(FilePermissions.fromOctalString("111"), layerEntry.getPermissions());
  }

  @Test
  public void testConvert_sourceDestinationTimestamps() throws Exception {
    Path root = temporaryFolder.getRoot().toPath();
    Path subdir = root.resolve("sub");
    Files.createDirectory(subdir);
    Path file = subdir.resolve("file.txt");
    Files.copy(new ByteArrayInputStream("foo".getBytes(StandardCharsets.UTF_8)), file);

    FileEntriesLayer result = fixture.convert(root.toString() + ",/dest,timestamps=actual");
    Assert.assertEquals(3, result.getEntries().size());

    FileEntry layerEntry = result.getEntries().get(0);
    Assert.assertEquals(root, layerEntry.getSourceFile());
    Assert.assertEquals(AbsoluteUnixPath.get("/dest"), layerEntry.getExtractionPath());
    Assert.assertEquals(
        Files.getLastModifiedTime(root).toMillis(),
        layerEntry.getModificationTime().toEpochMilli());

    layerEntry = result.getEntries().get(1);
    Assert.assertEquals(subdir, layerEntry.getSourceFile());
    Assert.assertEquals(AbsoluteUnixPath.get("/dest/sub"), layerEntry.getExtractionPath());
    Assert.assertEquals(
        Files.getLastModifiedTime(subdir).toMillis(),
        layerEntry.getModificationTime().toEpochMilli());

    layerEntry = result.getEntries().get(2);
    Assert.assertEquals(file, layerEntry.getSourceFile());
    Assert.assertEquals(AbsoluteUnixPath.get("/dest/sub/file.txt"), layerEntry.getExtractionPath());
    Assert.assertEquals(
        Files.getLastModifiedTime(file).toMillis(),
        layerEntry.getModificationTime().toEpochMilli());
  }
}
