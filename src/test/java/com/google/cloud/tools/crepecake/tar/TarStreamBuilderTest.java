/*
 * Copyright 2017 Google Inc.
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

package com.google.cloud.tools.crepecake.tar;

import com.google.cloud.tools.crepecake.blob.Blob;
import com.google.common.io.CharStreams;
import com.google.common.io.Resources;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link TarStreamBuilder}. */
public class TarStreamBuilderTest {

  private String expectedFileAString;
  private String expectedFileBString;
  private TarStreamBuilder testTarStreamBuilder = new TarStreamBuilder();

  @Before
  public void setUp() throws IOException, URISyntaxException {
    // Gets the test resource files.
    Path fileA = Paths.get(Resources.getResource("fileA").toURI());
    Path fileB = Paths.get(Resources.getResource("fileB").toURI());
    Path directoryA = Paths.get(Resources.getResource("directoryA").toURI());

    expectedFileAString = new String(Files.readAllBytes(fileA), StandardCharsets.UTF_8);
    expectedFileBString = new String(Files.readAllBytes(fileB), StandardCharsets.UTF_8);

    // Prepares a test TarStreamBuilder.
    testTarStreamBuilder.addEntry(
        new TarArchiveEntry(fileA.toFile(), "some/path/to/resourceFileA"));
    testTarStreamBuilder.addEntry(new TarArchiveEntry(fileB.toFile(), "crepecake"));
    testTarStreamBuilder.addEntry(new TarArchiveEntry(directoryA.toFile(), "some/path/to"));
  }

  @Test
  public void testToBlob() throws IOException {
    Blob blob = testTarStreamBuilder.toBlob();

    // Writes the BLOB and captures the output.
    ByteArrayOutputStream tarByteOutputStream = new ByteArrayOutputStream();
    blob.writeTo(tarByteOutputStream);

    // Rearrange the output into input for verification.
    ByteArrayInputStream tarByteInputStream =
        new ByteArrayInputStream(tarByteOutputStream.toByteArray());
    TarArchiveInputStream tarArchiveInputStream = new TarArchiveInputStream(tarByteInputStream);

    verifyTarArchive(tarArchiveInputStream);
  }

  @Test
  public void testToBlob_withCompression() throws IOException {
    Blob blob = testTarStreamBuilder.toBlob();

    // Writes the BLOB and captures the output.
    ByteArrayOutputStream tarByteOutputStream = new ByteArrayOutputStream();
    OutputStream compressorStream = new GZIPOutputStream(tarByteOutputStream);
    blob.writeTo(compressorStream);

    // Rearrange the output into input for verification.
    ByteArrayInputStream byteArrayInputStream =
        new ByteArrayInputStream(tarByteOutputStream.toByteArray());
    InputStream tarByteInputStream = new GZIPInputStream(byteArrayInputStream);
    TarArchiveInputStream tarArchiveInputStream = new TarArchiveInputStream(tarByteInputStream);

    verifyTarArchive(tarArchiveInputStream);
  }

  /**
   * Helper method to verify that the files were archived correctly by reading {@code
   * tarArchiveInputStream}.
   */
  private void verifyTarArchive(TarArchiveInputStream tarArchiveInputStream) throws IOException {
    // Verifies fileA was archived correctly.
    TarArchiveEntry headerFileA = tarArchiveInputStream.getNextTarEntry();
    Assert.assertEquals("some/path/to/resourceFileA", headerFileA.getName());
    String fileAString =
        CharStreams.toString(new InputStreamReader(tarArchiveInputStream, StandardCharsets.UTF_8));
    Assert.assertEquals(expectedFileAString, fileAString);

    // Verifies fileB was archived correctly.
    TarArchiveEntry headerFileB = tarArchiveInputStream.getNextTarEntry();
    Assert.assertEquals("crepecake", headerFileB.getName());
    String fileBString =
        CharStreams.toString(new InputStreamReader(tarArchiveInputStream, StandardCharsets.UTF_8));
    Assert.assertEquals(expectedFileBString, fileBString);

    // Verifies directoryA was archived correctly.
    TarArchiveEntry headerDirectoryA = tarArchiveInputStream.getNextTarEntry();
    Assert.assertEquals("some/path/to/", headerDirectoryA.getName());

    Assert.assertNull(tarArchiveInputStream.getNextTarEntry());
  }
}
