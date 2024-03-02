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

import static com.google.common.truth.Truth.assertThat;

import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.buildplan.FileEntriesLayer;
import com.google.cloud.tools.jib.api.buildplan.FileEntry;
import com.google.cloud.tools.jib.api.buildplan.FilePermissions;
import com.google.cloud.tools.jib.blob.Blob;
import com.google.cloud.tools.jib.blob.Blobs;
import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteStreams;
import com.google.common.io.Resources;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Tests for {@link ReproducibleLayerBuilder}. */
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
    assertThat(header.getName()).isEqualTo(expectedExtractionPath);

    byte[] expectedBytes = Files.readAllBytes(expectedFile);
    byte[] extractedBytes = ByteStreams.toByteArray(tarArchiveInputStream);
    assertThat(extractedBytes).isEqualTo(expectedBytes);
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
    assertThat(extractionPathEntry.getName()).isEqualTo(expectedExtractionPath);
    assertThat(extractionPathEntry.isDirectory()).isTrue();
    assertThat(extractionPathEntry.getMode()).isEqualTo(TarArchiveEntry.DEFAULT_DIR_MODE);
  }

  /**
   * Verifies that the modification time has been reset in the PAX headers.
   *
   * @param entry The archive entry in the layer.
   */
  private static void verifyThatModificationTimeIsReset(ArchiveEntry entry) {
    assertThat(entry.getLastModifiedDate().toInstant())
        .isEqualTo(FileEntriesLayer.DEFAULT_MODIFICATION_TIME);
  }

  private static FileEntry layerEntry(
      Path source, AbsoluteUnixPath destination, FilePermissions permissions, String ownership)
      throws IOException {
    return new FileEntry(
        source,
        destination,
        permissions,
        // Here we make sure to use a default modification time
        // since JIB will set modification time for all files to
        // the one set in JIB settings by a user. In case user
        // hasn't set it, JIB will use default modification time,
        // i.e. 1 second past January 1st, 1970 (1 second past epoch).
        FileEntriesLayer.DEFAULT_MODIFICATION_TIME,
        ownership);
  }

  private static FileEntry layerEntry(
      Path source, AbsoluteUnixPath destination, FilePermissions permissions) throws IOException {
    return layerEntry(source, destination, permissions, "");
  }

  private static FileEntry layerEntry(Path source, AbsoluteUnixPath destination)
      throws IOException {
    return layerEntry(
        source,
        destination,
        FileEntriesLayer.DEFAULT_FILE_PERMISSIONS_PROVIDER.get(source, destination));
  }

  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void testBuild() throws URISyntaxException, IOException {
    Path layerDirectory = Paths.get(Resources.getResource("core/layer").toURI());
    Path blobA = Paths.get(Resources.getResource("core/blobA").toURI());

    ReproducibleLayerBuilder layerBuilder =
        new ReproducibleLayerBuilder(
            ImmutableList.copyOf(
                FileEntriesLayer.builder()
                    .addEntryRecursive(
                        layerDirectory, AbsoluteUnixPath.get("/extract/here/apple/layer"))
                    .addEntry(blobA, AbsoluteUnixPath.get("/extract/here/apple/blobA"))
                    .addEntry(blobA, AbsoluteUnixPath.get("/extract/here/banana/blobA"))
                    .build()
                    .getEntries()));

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

    // TODO: Currently this test only covers variation in order and modification time, even though
    // TODO: the code is designed to clean up userid/groupid, this test does not check that yet.
    String contentA = "abcabc";
    Path fileA1 = createFile(root1, "fileA", contentA, 10000);
    Path fileA2 = createFile(root2, "fileA", contentA, 20000);
    String contentB = "yumyum";
    Path fileB1 = createFile(root1, "fileB", contentB, 10000);
    Path fileB2 = createFile(root2, "fileB", contentB, 20000);

    // check if modification times are off
    assertThat(Files.getLastModifiedTime(fileA2)).isNotEqualTo(Files.getLastModifiedTime(fileA1));
    assertThat(Files.getLastModifiedTime(fileB2)).isNotEqualTo(Files.getLastModifiedTime(fileB1));

    // create layers of exact same content but ordered differently and with different timestamps
    Blob layer =
        new ReproducibleLayerBuilder(
                ImmutableList.of(
                    layerEntry(fileA1, AbsoluteUnixPath.get("/somewhere/fileA")),
                    layerEntry(fileB1, AbsoluteUnixPath.get("/somewhere/fileB"))))
            .build();
    Blob reproduced =
        new ReproducibleLayerBuilder(
                ImmutableList.of(
                    layerEntry(fileB2, AbsoluteUnixPath.get("/somewhere/fileB")),
                    layerEntry(fileA2, AbsoluteUnixPath.get("/somewhere/fileA"))))
            .build();

    byte[] layerContent = Blobs.writeToByteArray(layer);
    byte[] reproducedLayerContent = Blobs.writeToByteArray(reproduced);

    assertThat(layerContent).isEqualTo(reproducedLayerContent);
  }

  @Test
  public void testBuild_parentDirBehavior() throws IOException {
    Path testRoot = temporaryFolder.getRoot().toPath();

    // the path doesn't really matter on source files, but these are structured
    Path parent = Files.createDirectories(testRoot.resolve("dirA"));
    Path fileA = Files.createFile(parent.resolve("fileA"));
    Path ignoredParent = Files.createDirectories(testRoot.resolve("dirB-ignored"));
    Path fileB = Files.createFile(ignoredParent.resolve("fileB"));
    Path fileC =
        Files.createFile(Files.createDirectories(testRoot.resolve("dirC-absent")).resolve("fileC"));

    Blob layer =
        new ReproducibleLayerBuilder(
                ImmutableList.of(
                    new FileEntry(
                        parent,
                        AbsoluteUnixPath.get("/root/dirA"),
                        FilePermissions.fromOctalString("111"),
                        Instant.ofEpochSecond(10)),
                    new FileEntry(
                        fileA,
                        AbsoluteUnixPath.get("/root/dirA/fileA"),
                        FilePermissions.fromOctalString("222"),
                        Instant.ofEpochSecond(20)),
                    new FileEntry(
                        fileB,
                        AbsoluteUnixPath.get("/root/dirB-ignored/fileB"),
                        FilePermissions.fromOctalString("333"),
                        Instant.ofEpochSecond(30)),
                    new FileEntry(
                        ignoredParent,
                        AbsoluteUnixPath.get("/root/dirB-ignored"),
                        FilePermissions.fromOctalString("444"),
                        Instant.ofEpochSecond(40)),
                    new FileEntry(
                        fileC,
                        AbsoluteUnixPath.get("/root/dirC-absent/file3"),
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
      assertThat(root.getMode()).isEqualTo(040755);
      assertThat(root.getModTime().toInstant()).isEqualTo(Instant.ofEpochSecond(1));
      assertThat(root.getLongUserId()).isEqualTo(0);
      assertThat(root.getLongGroupId()).isEqualTo(0);
      assertThat(root.getUserName()).isEmpty();
      assertThat(root.getGroupName()).isEmpty();

      // parentAAA (custom permissions, custom timestamp)
      TarArchiveEntry rootParentA = in.getNextTarEntry();
      assertThat(rootParentA.getMode()).isEqualTo(040111);
      assertThat(rootParentA.getModTime().toInstant()).isEqualTo(Instant.ofEpochSecond(10));
      assertThat(rootParentA.getLongUserId()).isEqualTo(0);
      assertThat(rootParentA.getLongGroupId()).isEqualTo(0);
      assertThat(rootParentA.getUserName()).isEmpty();
      assertThat(rootParentA.getGroupName()).isEmpty();

      // skip over fileA
      in.getNextTarEntry();

      // parentBBB (default permissions - ignored custom permissions, since fileB added first)
      TarArchiveEntry rootParentB = in.getNextTarEntry();
      // TODO (#1650): we want 040444 here.
      assertThat(rootParentB.getMode()).isEqualTo(040755);
      // TODO (#1650): we want Instant.ofEpochSecond(40) here.
      assertThat(rootParentB.getModTime().toInstant()).isEqualTo(Instant.ofEpochSecond(1));
      assertThat(rootParentB.getLongUserId()).isEqualTo(0);
      assertThat(rootParentB.getLongGroupId()).isEqualTo(0);
      assertThat(rootParentB.getUserName()).isEmpty();
      assertThat(rootParentB.getGroupName()).isEmpty();

      // skip over fileB
      in.getNextTarEntry();

      // parentCCC (default permissions - no entry provided)
      TarArchiveEntry rootParentC = in.getNextTarEntry();
      assertThat(rootParentC.getMode()).isEqualTo(040755);
      assertThat(rootParentC.getModTime().toInstant()).isEqualTo(Instant.ofEpochSecond(1));
      assertThat(rootParentC.getLongUserId()).isEqualTo(0);
      assertThat(rootParentC.getLongGroupId()).isEqualTo(0);
      assertThat(rootParentC.getUserName()).isEmpty();
      assertThat(rootParentC.getGroupName()).isEmpty();

      // we don't care about fileC
    }
  }

  @Test
  public void testBuild_timestampDefault() throws IOException {
    Path file = createFile(temporaryFolder.getRoot().toPath(), "fileA", "some content", 54321);

    Blob blob =
        new ReproducibleLayerBuilder(
                ImmutableList.of(layerEntry(file, AbsoluteUnixPath.get("/fileA"))))
            .build();

    Path tarFile = temporaryFolder.newFile().toPath();
    try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(tarFile))) {
      blob.writeTo(out);
    }

    // Reads the file back.
    try (TarArchiveInputStream in = new TarArchiveInputStream(Files.newInputStream(tarFile))) {
      verifyThatModificationTimeIsReset(in.getNextEntry());
    }
  }

  @Test
  public void testBuild_timestampNonDefault() throws IOException {
    Path file = createFile(temporaryFolder.getRoot().toPath(), "fileA", "some content", 54321);

    Blob blob =
        new ReproducibleLayerBuilder(
                ImmutableList.of(layerEntry(file, AbsoluteUnixPath.get("/fileA"))))
            .build();

    Path tarFile = temporaryFolder.newFile().toPath();
    try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(tarFile))) {
      blob.writeTo(out);
    }

    // Reads the file back.
    try (TarArchiveInputStream in = new TarArchiveInputStream(Files.newInputStream(tarFile))) {
      verifyThatModificationTimeIsReset(in.getNextTarEntry());
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
                    layerEntry(fileA, AbsoluteUnixPath.get("/somewhere/fileA")),
                    layerEntry(
                        fileB,
                        AbsoluteUnixPath.get("/somewhere/fileB"),
                        FilePermissions.fromOctalString("123")),
                    layerEntry(
                        folder,
                        AbsoluteUnixPath.get("/somewhere/folder"),
                        FilePermissions.fromOctalString("456"))))
            .build();

    Path tarFile = temporaryFolder.newFile().toPath();
    try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(tarFile))) {
      blob.writeTo(out);
    }

    try (TarArchiveInputStream in = new TarArchiveInputStream(Files.newInputStream(tarFile))) {
      // Root folder (default folder permissions)
      assertThat(in.getNextTarEntry().getMode()).isEqualTo(040755);
      // fileA (default file permissions)
      assertThat(in.getNextTarEntry().getMode()).isEqualTo(0100644);
      // fileB (custom file permissions)
      assertThat(in.getNextTarEntry().getMode()).isEqualTo(0100123);
      // folder (custom folder permissions)
      assertThat(in.getNextTarEntry().getMode()).isEqualTo(040456);
    }
  }

  @Test
  public void testBuild_ownership() throws IOException {
    Path testRoot = temporaryFolder.getRoot().toPath();
    Path someFile = createFile(testRoot, "someFile", "content", 54321);

    Blob blob =
        new ReproducibleLayerBuilder(
                ImmutableList.of(
                    layerEntry(someFile, AbsoluteUnixPath.get("/file1")),
                    layerEntry(
                        someFile,
                        AbsoluteUnixPath.get("/file2"),
                        FilePermissions.fromOctalString("123"),
                        ""),
                    layerEntry(
                        someFile,
                        AbsoluteUnixPath.get("/file3"),
                        FilePermissions.fromOctalString("123"),
                        ":"),
                    layerEntry(
                        someFile,
                        AbsoluteUnixPath.get("/file4"),
                        FilePermissions.fromOctalString("123"),
                        "333:"),
                    layerEntry(
                        someFile,
                        AbsoluteUnixPath.get("/file5"),
                        FilePermissions.fromOctalString("123"),
                        ":555"),
                    layerEntry(
                        someFile,
                        AbsoluteUnixPath.get("/file6"),
                        FilePermissions.fromOctalString("123"),
                        "333:555"),
                    layerEntry(
                        someFile,
                        AbsoluteUnixPath.get("/file7"),
                        FilePermissions.fromOctalString("123"),
                        "user:"),
                    layerEntry(
                        someFile,
                        AbsoluteUnixPath.get("/file8"),
                        FilePermissions.fromOctalString("123"),
                        ":group"),
                    layerEntry(
                        someFile,
                        AbsoluteUnixPath.get("/file9"),
                        FilePermissions.fromOctalString("123"),
                        "user:group")))
            .build();

    Path tarFile = temporaryFolder.newFile().toPath();
    try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(tarFile))) {
      blob.writeTo(out);
    }

    try (TarArchiveInputStream in = new TarArchiveInputStream(Files.newInputStream(tarFile))) {
      TarArchiveEntry entry1 = in.getNextTarEntry();
      assertThat(entry1.getLongUserId()).isEqualTo(0);
      assertThat(entry1.getLongGroupId()).isEqualTo(0);
      assertThat(entry1.getUserName()).isEmpty();
      assertThat(entry1.getGroupName()).isEmpty();
      verifyThatModificationTimeIsReset(entry1);

      TarArchiveEntry entry2 = in.getNextTarEntry();
      assertThat(entry2.getLongUserId()).isEqualTo(0);
      assertThat(entry2.getLongGroupId()).isEqualTo(0);
      assertThat(entry2.getUserName()).isEmpty();
      assertThat(entry2.getGroupName()).isEmpty();
      verifyThatModificationTimeIsReset(entry2);

      TarArchiveEntry entry3 = in.getNextTarEntry();
      assertThat(entry3.getLongUserId()).isEqualTo(0);
      assertThat(entry3.getLongGroupId()).isEqualTo(0);
      assertThat(entry3.getUserName()).isEmpty();
      assertThat(entry3.getGroupName()).isEmpty();
      verifyThatModificationTimeIsReset(entry3);

      TarArchiveEntry entry4 = in.getNextTarEntry();
      assertThat(entry4.getLongUserId()).isEqualTo(333);
      assertThat(entry4.getLongGroupId()).isEqualTo(0);
      assertThat(entry4.getUserName()).isEmpty();
      assertThat(entry4.getGroupName()).isEmpty();
      verifyThatModificationTimeIsReset(entry4);

      TarArchiveEntry entry5 = in.getNextTarEntry();
      assertThat(entry5.getLongUserId()).isEqualTo(0);
      assertThat(entry5.getLongGroupId()).isEqualTo(555);
      assertThat(entry5.getUserName()).isEmpty();
      assertThat(entry5.getGroupName()).isEmpty();
      verifyThatModificationTimeIsReset(entry5);

      TarArchiveEntry entry6 = in.getNextTarEntry();
      assertThat(entry6.getLongUserId()).isEqualTo(333);
      assertThat(entry6.getLongGroupId()).isEqualTo(555);
      assertThat(entry6.getUserName()).isEmpty();
      assertThat(entry6.getGroupName()).isEmpty();
      verifyThatModificationTimeIsReset(entry6);

      TarArchiveEntry entry7 = in.getNextTarEntry();
      assertThat(entry7.getLongUserId()).isEqualTo(0);
      assertThat(entry7.getLongGroupId()).isEqualTo(0);
      assertThat(entry7.getUserName()).isEqualTo("user");
      assertThat(entry7.getGroupName()).isEmpty();
      verifyThatModificationTimeIsReset(entry7);

      TarArchiveEntry entry8 = in.getNextTarEntry();
      assertThat(entry8.getLongUserId()).isEqualTo(0);
      assertThat(entry8.getLongGroupId()).isEqualTo(0);
      assertThat(entry8.getUserName()).isEmpty();
      assertThat(entry8.getGroupName()).isEqualTo("group");
      verifyThatModificationTimeIsReset(entry8);

      TarArchiveEntry entry9 = in.getNextTarEntry();
      assertThat(entry9.getLongUserId()).isEqualTo(0);
      assertThat(entry9.getLongGroupId()).isEqualTo(0);
      assertThat(entry9.getUserName()).isEqualTo("user");
      assertThat(entry9.getGroupName()).isEqualTo("group");
      verifyThatModificationTimeIsReset(entry9);
    }
  }

  private static Path createFile(Path root, String filename, String content, long modificationTime)
      throws IOException {
    Path newFile =
        Files.write(
            root.resolve(filename),
            content.getBytes(StandardCharsets.UTF_8),
            StandardOpenOption.CREATE_NEW);
    Files.setLastModifiedTime(newFile, FileTime.fromMillis(modificationTime));
    return newFile;
  }
}
