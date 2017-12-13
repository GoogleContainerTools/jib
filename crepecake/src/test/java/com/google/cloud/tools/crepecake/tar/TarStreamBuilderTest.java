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
import com.google.common.base.Charsets;
import com.google.common.io.CharStreams;
import com.google.common.io.Resources;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorOutputStream;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link TarStreamBuilder}. */
public class TarStreamBuilderTest {

  private String expectedFileAString;
  private String expectedFileBString;
  private TarStreamBuilder testTarStreamBuilder = new TarStreamBuilder();

  private Path fileA;
  private Path fileB;

  @Before
  public void setUp() throws IOException, URISyntaxException {
    // Gets the test resource files.
    fileA = Paths.get(Resources.getResource("fileA").toURI());
    fileB = Paths.get(Resources.getResource("fileB").toURI());

    expectedFileAString = new String(Files.readAllBytes(fileA), Charsets.UTF_8);
    expectedFileBString = new String(Files.readAllBytes(fileB), Charsets.UTF_8);

    // Prepares a test TarStreamBuilder.
    testTarStreamBuilder.addFile(fileA.toFile(), "some/path/to/resourceFileA");
    testTarStreamBuilder.addFile(fileB.toFile(), "crepecake");
  }

  @Test
  public void testToBlob() throws IOException {
    Blob blob = testTarStreamBuilder.toBlob();

    // Adding another file should not change a previously-obtained Blob.
    testTarStreamBuilder.addFile(fileA.toFile(), "this should not be in the archive");

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
  public void testToBlob_withCompression() throws IOException, CompressorException {
    Blob blob = testTarStreamBuilder.toBlob();

    // Writes the BLOB and captures the output.
    ByteArrayOutputStream tarByteOutputStream = new ByteArrayOutputStream();
    CompressorOutputStream compressorStream =
        new CompressorStreamFactory()
            .createCompressorOutputStream(CompressorStreamFactory.GZIP, tarByteOutputStream);
    blob.writeTo(compressorStream);

    // Rearrange the output into input for verification.
    ByteArrayInputStream byteArrayInputStream =
        new ByteArrayInputStream(tarByteOutputStream.toByteArray());
    InputStream tarByteInputStream =
        new CompressorStreamFactory().createCompressorInputStream(byteArrayInputStream);
    TarArchiveInputStream tarArchiveInputStream = new TarArchiveInputStream(tarByteInputStream);

    verifyTarArchive(tarArchiveInputStream);
  }

  /**
   * Helper method to verify that the files were archived correctly by reading {@code
   * tarArchiveInputStream}.
   */
  private void verifyTarArchive(TarArchiveInputStream tarArchiveInputStream) throws IOException {
    // Verifies fileA was archived correctly.
    TarArchiveEntry headerA = tarArchiveInputStream.getNextTarEntry();
    Assert.assertEquals("some/path/to/resourceFileA", headerA.getName());
    String fileAString = CharStreams.toString(new InputStreamReader(tarArchiveInputStream));
    Assert.assertEquals(expectedFileAString, fileAString);

    // Verifies fileB was archived correctly.
    TarArchiveEntry headerB = tarArchiveInputStream.getNextTarEntry();
    Assert.assertEquals("crepecake", headerB.getName());
    String fileBString = CharStreams.toString(new InputStreamReader(tarArchiveInputStream));
    Assert.assertEquals(expectedFileBString, fileBString);

    Assert.assertNull(tarArchiveInputStream.getNextTarEntry());
  }
}
