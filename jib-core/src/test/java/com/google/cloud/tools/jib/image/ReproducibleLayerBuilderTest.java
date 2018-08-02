/*
 * Copyright 2018 Google LLC. All rights reserved.
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

import com.google.cloud.tools.jib.blob.Blob;
import com.google.cloud.tools.jib.blob.Blobs;
import com.google.cloud.tools.jib.filesystem.DirectoryWalker;
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
import java.util.Arrays;
import java.util.Collections;
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

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void testBuild() throws URISyntaxException, IOException {
    Path layerDirectory = Paths.get(Resources.getResource("layer").toURI());
    Path blobA = Paths.get(Resources.getResource("blobA").toURI());

    ReproducibleLayerBuilder layerBuilder =
        new ReproducibleLayerBuilder()
            .addFiles(Arrays.asList(layerDirectory, blobA), "extract/here/apple")
            .addFiles(Collections.singletonList(blobA), "extract/here/banana");

    // Writes the layer tar to a temporary file.
    UnwrittenLayer unwrittenLayer = layerBuilder.build();
    Path temporaryFile = temporaryFolder.newFile().toPath();
    try (OutputStream temporaryFileOutputStream =
        new BufferedOutputStream(Files.newOutputStream(temporaryFile))) {
      unwrittenLayer.getBlob().writeTo(temporaryFileOutputStream);
    }

    // Reads the file back.
    try (TarArchiveInputStream tarArchiveInputStream =
        new TarArchiveInputStream(Files.newInputStream(temporaryFile))) {
      // Verifies that blobA was added.
      verifyNextTarArchiveEntry(tarArchiveInputStream, "extract/here/apple/blobA", blobA);

      // Verifies that all the files have been added to the tarball stream.
      ImmutableList<Path> layerDirectoryPaths =
          new DirectoryWalker(layerDirectory).filter(path -> !path.equals(layerDirectory)).walk();
      for (Path path : layerDirectoryPaths) {
        TarArchiveEntry header = tarArchiveInputStream.getNextTarEntry();

        StringBuilder expectedExtractionPath = new StringBuilder("extract/here/apple");
        for (Path pathComponent : layerDirectory.getParent().relativize(path)) {
          expectedExtractionPath.append("/").append(pathComponent);
        }
        // Check path-equality because there might be an appended backslash in the header
        // filename.
        Assert.assertEquals(
            Paths.get(expectedExtractionPath.toString()), Paths.get(header.getName()));

        // If is a normal file, checks that the file contents match.
        if (Files.isRegularFile(path)) {
          String expectedFileString = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);

          String extractedFileString =
              CharStreams.toString(
                  new InputStreamReader(tarArchiveInputStream, StandardCharsets.UTF_8));

          Assert.assertEquals(expectedFileString, extractedFileString);
        }
      }

      // Verifies that blobA was added to the other location.
      verifyNextTarArchiveEntry(tarArchiveInputStream, "extract/here/banana/blobA", blobA);
    }
  }

  @Test
  public void testToBlob_reproducibility() throws IOException {
    Path testRoot = temporaryFolder.getRoot().toPath();
    Path root1 = Files.createDirectories(testRoot.resolve("files1"));
    Path root2 = Files.createDirectories(testRoot.resolve("files2"));

    String extractionPath = "/somewhere";

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
        new ReproducibleLayerBuilder()
            .addFiles(Arrays.asList(fileA1, fileB1), extractionPath)
            .build()
            .getBlob();
    Blob reproduced =
        new ReproducibleLayerBuilder()
            .addFiles(Arrays.asList(fileB2, fileA2), extractionPath)
            .build()
            .getBlob();

    byte[] layerContent = Blobs.writeToByteArray(layer);
    byte[] reproducedLayerContent = Blobs.writeToByteArray(reproduced);

    Assert.assertThat(layerContent, CoreMatchers.is(reproducedLayerContent));
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
