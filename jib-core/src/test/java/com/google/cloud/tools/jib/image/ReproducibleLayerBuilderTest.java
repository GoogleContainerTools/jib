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

package com.google.cloud.tools.jib.image;

import com.google.cloud.tools.jib.api.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.FilePermissions;
import com.google.cloud.tools.jib.api.LayerConfiguration;
import com.google.cloud.tools.jib.api.LayerEntry;
import com.google.cloud.tools.jib.blob.Blob;
import com.google.cloud.tools.jib.blob.Blobs;
import com.google.common.collect.ImmutableList;
import com.google.common.io.CharStreams;
import com.google.common.io.Resources;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Date;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests for {@link ReproducibleLayerBuilder}. */
@RunWith(MockitoJUnitRunner.class)
public class ReproducibleLayerBuilderTest {

  /**
   * Verifies the correctness of the next {@link TarArchiveEntry} in the {@link
   * TarArchiveInputStream}.
   *
   * @param tarArchiveInputStream the {@link TarArchiveInputStream} to read from
   * @param expectedExtractionPath the expected extraction path of the next entry
   * @param expectedFile the file to match against the contents of the next entry
   * @throws IOException if an I/O exception occurs
   */
  private static void verifyNextTarArchiveEntry(
      TarArchiveInputStream tarArchiveInputStream, String expectedExtractionPath, Path expectedFile)
      throws IOException {
    TarArchiveEntry header = tarArchiveInputStream.getNextTarEntry();
    Assert.assertEquals(expectedExtractionPath, header.getName());

    String expectedString = new String(Files.readAllBytes(expectedFile), StandardCharsets.UTF_8);

    String extractedString =
        CharStreams.toString(new InputStreamReader(tarArchiveInputStream, StandardCharsets.UTF_8));

    Assert.assertEquals(expectedString, extractedString);
  }

  /**
   * Verifies that the next {@link TarArchiveEntry} in the {@link TarArchiveInputStream} is a
   * directory with correct permissions.
   *
   * @param tarArchiveInputStream the {@link TarArchiveInputStream} to read from
   * @param expectedExtractionPath the expected extraction path of the next entry
   * @throws IOException if an I/O exception occurs
   */
  private static void verifyNextTarArchiveEntryIsDirectory(
      TarArchiveInputStream tarArchiveInputStream, String expectedExtractionPath)
      throws IOException {
    TarArchiveEntry extractionPathEntry = tarArchiveInputStream.getNextTarEntry();
    Assert.assertEquals(expectedExtractionPath, extractionPathEntry.getName());
    Assert.assertTrue(extractionPathEntry.isDirectory());
    Assert.assertEquals(TarArchiveEntry.DEFAULT_DIR_MODE, extractionPathEntry.getMode());
  }

  private static LayerEntry defaultLayerEntry(Path source, AbsoluteUnixPath destination) {
    return new LayerEntry(
        source,
        destination,
        LayerConfiguration.DEFAULT_FILE_PERMISSIONS_PROVIDER.apply(source, destination),
        LayerConfiguration.DEFAULT_MODIFIED_TIME);
  }

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void testBuild() throws URISyntaxException, IOException {
    Path layerDirectory = Paths.get(Resources.getResource("core/layer").toURI());
    Path blobA = Paths.get(Resources.getResource("core/blobA").toURI());

    ReproducibleLayerBuilder layerBuilder =
        new ReproducibleLayerBuilder(
            LayerConfiguration.builder()
                .addEntryRecursive(
                    layerDirectory, AbsoluteUnixPath.get("/extract/here/apple/layer"))
                .addEntry(blobA, AbsoluteUnixPath.get("/extract/here/apple/blobA"))
                .addEntry(blobA, AbsoluteUnixPath.get("/extract/here/banana/blobA"))
                .build()
                .getLayerEntries());

    // Writes the layer tar to a temporary file.
    Blob unwrittenBlob = layerBuilder.build();
    Path temporaryFile = temporaryFolder.newFile().toPath();
    try (OutputStream temporaryFileOutputStream =
        new BufferedOutputStream(Files.newOutputStream(temporaryFile))) {
      unwrittenBlob.writeTo(temporaryFileOutputStream);
    }

    // Reads the file back.
    try (TarArchiveInputStream tarArchiveInputStream =
        new TarArchiveInputStream(Files.newInputStream(temporaryFile))) {
      verifyNextTarArchiveEntryIsDirectory(tarArchiveInputStream, "extract/");
      verifyNextTarArchiveEntryIsDirectory(tarArchiveInputStream, "extract/here/");
      verifyNextTarArchiveEntryIsDirectory(tarArchiveInputStream, "extract/here/apple/");
      verifyNextTarArchiveEntry(tarArchiveInputStream, "extract/here/apple/blobA", blobA);
      verifyNextTarArchiveEntryIsDirectory(tarArchiveInputStream, "extract/here/apple/layer/");
      verifyNextTarArchiveEntryIsDirectory(tarArchiveInputStream, "extract/here/apple/layer/a/");
      verifyNextTarArchiveEntryIsDirectory(tarArchiveInputStream, "extract/here/apple/layer/a/b/");
      verifyNextTarArchiveEntry(
          tarArchiveInputStream,
          "extract/here/apple/layer/a/b/bar",
          Paths.get(Resources.getResource("core/layer/a/b/bar").toURI()));
      verifyNextTarArchiveEntryIsDirectory(tarArchiveInputStream, "extract/here/apple/layer/c/");
      verifyNextTarArchiveEntry(
          tarArchiveInputStream,
          "extract/here/apple/layer/c/cat",
          Paths.get(Resources.getResource("core/layer/c/cat").toURI()));
      verifyNextTarArchiveEntry(
          tarArchiveInputStream,
          "extract/here/apple/layer/foo",
          Paths.get(Resources.getResource("core/layer/foo").toURI()));
      verifyNextTarArchiveEntryIsDirectory(tarArchiveInputStream, "extract/here/banana/");
      verifyNextTarArchiveEntry(tarArchiveInputStream, "extract/here/banana/blobA", blobA);
    }
  }

  @Test
  public void testToBlob_reproducibility() throws IOException {
    Path testRoot = temporaryFolder.getRoot().toPath();
    Path root1 = Files.createDirectories(testRoot.resolve("files1"));
    Path root2 = Files.createDirectories(testRoot.resolve("files2"));

    // TODO: Currently this test only covers variation in order and modified time, even though
    // TODO: the code is designed to clean up userid/groupid, this test does not check that yet.
    String contentA = "abcabc";
    Path fileA1 = createFile(root1, "fileA", contentA, 10000);
    Path fileA2 = createFile(root2, "fileA", contentA, 20000);
    String contentB = "yumyum";
    Path fileB1 = createFile(root1, "fileB", contentB, 10000);
    Path fileB2 = createFile(root2, "fileB", contentB, 20000);

    // check if modified times are off
    Assert.assertNotEquals(Files.getLastModifiedTime(fileA1), Files.getLastModifiedTime(fileA2));
    Assert.assertNotEquals(Files.getLastModifiedTime(fileB1), Files.getLastModifiedTime(fileB2));

    // create layers of exact same content but ordered differently and with different timestamps
    Blob layer =
        new ReproducibleLayerBuilder(
                ImmutableList.of(
                    defaultLayerEntry(fileA1, AbsoluteUnixPath.get("/somewhere/fileA")),
                    defaultLayerEntry(fileB1, AbsoluteUnixPath.get("/somewhere/fileB"))))
            .build();
    Blob reproduced =
        new ReproducibleLayerBuilder(
                ImmutableList.of(
                    defaultLayerEntry(fileB2, AbsoluteUnixPath.get("/somewhere/fileB")),
                    defaultLayerEntry(fileA2, AbsoluteUnixPath.get("/somewhere/fileA"))))
            .build();

    byte[] layerContent = Blobs.writeToByteArray(layer);
    byte[] reproducedLayerContent = Blobs.writeToByteArray(reproduced);

    Assert.assertThat(layerContent, CoreMatchers.is(reproducedLayerContent));
  }

  @Test
  public void testBuild_parentDirBehavior() throws IOException {
    Path testRoot = temporaryFolder.getRoot().toPath();

    // the path doesn't really matter on source files, but these are structured
    Path parent = Files.createDirectories(testRoot.resolve("aaa"));
    Path fileA = Files.createFile(parent.resolve("fileA"));
    Path ignoredParent = Files.createDirectories(testRoot.resolve("bbb-ignored"));
    Path fileB = Files.createFile(ignoredParent.resolve("fileB"));
    Path fileC =
        Files.createFile(Files.createDirectories(testRoot.resolve("ccc-absent")).resolve("fileC"));

    Blob layer =
        new ReproducibleLayerBuilder(
                ImmutableList.of(
                    new LayerEntry(
                        parent,
                        AbsoluteUnixPath.get("/root/aaa"),
                        FilePermissions.fromOctalString("111"),
                        Instant.ofEpochSecond(10)),
                    new LayerEntry(
                        fileA,
                        AbsoluteUnixPath.get("/root/aaa/fileA"),
                        FilePermissions.fromOctalString("222"),
                        Instant.ofEpochSecond(20)),
                    new LayerEntry(
                        fileB,
                        AbsoluteUnixPath.get("/root/bbb-ignored/fileB"),
                        FilePermissions.fromOctalString("333"),
                        Instant.ofEpochSecond(30)),
                    new LayerEntry(
                        ignoredParent,
                        AbsoluteUnixPath.get("/root/bbb-ignored"),
                        FilePermissions.fromOctalString("444"),
                        Instant.ofEpochSecond(40)),
                    new LayerEntry(
                        fileC,
                        AbsoluteUnixPath.get("/root/ccc-absent/file3"),
                        FilePermissions.fromOctalString("555"),
                        Instant.ofEpochSecond(50))))
            .build();

    Path tarFile = temporaryFolder.newFile().toPath();
    try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(tarFile))) {
      layer.writeTo(out);
    }

    try (TarArchiveInputStream in = new TarArchiveInputStream(Files.newInputStream(tarFile))) {
      // root (default folder permissions)
      TarArchiveEntry root = in.getNextTarEntry();
      Assert.assertEquals(040755, root.getMode());
      Assert.assertEquals(Instant.ofEpochSecond(1), root.getModTime().toInstant());

      // parentAAA (custom permissions, custom timestamp)
      TarArchiveEntry rootParentAAA = in.getNextTarEntry();
      Assert.assertEquals(040111, rootParentAAA.getMode());
      Assert.assertEquals(Instant.ofEpochSecond(10), rootParentAAA.getModTime().toInstant());

      // skip over fileA
      in.getNextTarEntry();

      // parentBBB (default permissions - ignored custom permissions, since fileB added first)
      TarArchiveEntry rootParentBBB = in.getNextTarEntry();
      // TODO (#1650): we want 040444 here.
      Assert.assertEquals(040755, rootParentBBB.getMode());
      // TODO (#1650): we want Instant.ofEpochSecond(40) here.
      Assert.assertEquals(Instant.ofEpochSecond(1), root.getModTime().toInstant());

      // skip over fileB
      in.getNextTarEntry();

      // parentCCC (default permissions - no entry provided)
      TarArchiveEntry rootParentCCC = in.getNextTarEntry();
      Assert.assertEquals(040755, rootParentCCC.getMode());
      Assert.assertEquals(Instant.ofEpochSecond(1), root.getModTime().toInstant());

      // we don't care about fileC
    }
  }

  @Test
  public void testBuild_timestampDefault() throws IOException {
    Path file = createFile(temporaryFolder.getRoot().toPath(), "fileA", "some content", 54321);

    Blob blob =
        new ReproducibleLayerBuilder(
                ImmutableList.of(defaultLayerEntry(file, AbsoluteUnixPath.get("/fileA"))))
            .build();

    Path tarFile = temporaryFolder.newFile().toPath();
    try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(tarFile))) {
      blob.writeTo(out);
    }

    // Reads the file back.
    try (TarArchiveInputStream in = new TarArchiveInputStream(Files.newInputStream(tarFile))) {
      Assert.assertEquals(
          Date.from(Instant.EPOCH.plusSeconds(1)), in.getNextEntry().getLastModifiedDate());
    }
  }

  @Test
  public void testBuild_timestampNonDefault() throws IOException {
    Path file = createFile(temporaryFolder.getRoot().toPath(), "fileA", "some content", 54321);

    Blob blob =
        new ReproducibleLayerBuilder(
                ImmutableList.of(
                    new LayerEntry(
                        file,
                        AbsoluteUnixPath.get("/fileA"),
                        FilePermissions.DEFAULT_FILE_PERMISSIONS,
                        Instant.ofEpochSecond(123))))
            .build();

    Path tarFile = temporaryFolder.newFile().toPath();
    try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(tarFile))) {
      blob.writeTo(out);
    }

    // Reads the file back.
    try (TarArchiveInputStream in = new TarArchiveInputStream(Files.newInputStream(tarFile))) {
      Assert.assertEquals(
          Date.from(Instant.EPOCH.plusSeconds(123)), in.getNextEntry().getLastModifiedDate());
    }
  }

  @Test
  public void testBuild_permissions() throws IOException {
    Path testRoot = temporaryFolder.getRoot().toPath();
    Path folder = Files.createDirectories(testRoot.resolve("files1"));
    Path fileA = createFile(testRoot, "fileA", "abc", 54321);
    Path fileB = createFile(testRoot, "fileB", "def", 54321);

    Blob blob =
        new ReproducibleLayerBuilder(
                ImmutableList.of(
                    defaultLayerEntry(fileA, AbsoluteUnixPath.get("/somewhere/fileA")),
                    new LayerEntry(
                        fileB,
                        AbsoluteUnixPath.get("/somewhere/fileB"),
                        FilePermissions.fromOctalString("123"),
                        LayerConfiguration.DEFAULT_MODIFIED_TIME),
                    new LayerEntry(
                        folder,
                        AbsoluteUnixPath.get("/somewhere/folder"),
                        FilePermissions.fromOctalString("456"),
                        LayerConfiguration.DEFAULT_MODIFIED_TIME)))
            .build();

    Path tarFile = temporaryFolder.newFile().toPath();
    try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(tarFile))) {
      blob.writeTo(out);
    }

    try (TarArchiveInputStream in = new TarArchiveInputStream(Files.newInputStream(tarFile))) {
      // Root folder (default folder permissions)
      Assert.assertEquals(040755, in.getNextTarEntry().getMode());
      // fileA (default file permissions)
      Assert.assertEquals(0100644, in.getNextTarEntry().getMode());
      // fileB (custom file permissions)
      Assert.assertEquals(0100123, in.getNextTarEntry().getMode());
      // folder (custom folder permissions)
      Assert.assertEquals(040456, in.getNextTarEntry().getMode());
    }
  }

  private Path createFile(Path root, String filename, String content, long lastModifiedTime)
      throws IOException {

    Path newFile =
        Files.write(
            root.resolve(filename),
            content.getBytes(StandardCharsets.UTF_8),
            StandardOpenOption.CREATE_NEW);
    Files.setLastModifiedTime(newFile, FileTime.fromMillis(lastModifiedTime));
    return newFile;
  }
}
