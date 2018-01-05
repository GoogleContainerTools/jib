/*
 * Copyright 2018 Google Inc.
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

package com.google.cloud.tools.crepecake.image;

import com.google.cloud.tools.crepecake.blob.Blob;
import com.google.cloud.tools.crepecake.blob.Blobs;
import com.google.common.io.CharStreams;
import com.google.common.io.Resources;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests for {@link LayerBuilder}. */
@RunWith(MockitoJUnitRunner.class)
public class LayerBuilderTest {

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void testBuild() throws URISyntaxException, IOException {
    Path layerDirectory = Paths.get(Resources.getResource("layer").toURI());

    LayerBuilder layerBuilder = new LayerBuilder();

    // Adds each file in the layer directory to the layer builder.
    Files.walk(layerDirectory)
        .filter(path -> !path.equals(layerDirectory))
        .forEach(
            path -> {
              Path extractionPathBase = Paths.get("extract/here");
              Path extractionPath = extractionPathBase.resolve(layerDirectory.relativize(path));
              layerBuilder.addFile(path.toFile(), extractionPath.toString());
            });

    // Writes the layer tar to a temporary file.
    UnwrittenLayer unwrittenLayer = layerBuilder.build();
    File temporaryFile = temporaryFolder.newFile();
    try (OutputStream temporaryFileOutputStream =
        new BufferedOutputStream(new FileOutputStream(temporaryFile))) {
      unwrittenLayer.getBlob().writeTo(temporaryFileOutputStream);
    }

    // Reads the file back.
    Blob fileBlob = Blobs.from(temporaryFile);
    ByteArrayOutputStream fileContentStream = new ByteArrayOutputStream();
    fileBlob.writeTo(fileContentStream);
    ByteArrayInputStream tarByteInputStream =
        new ByteArrayInputStream(fileContentStream.toByteArray());
    TarArchiveInputStream tarArchiveInputStream = new TarArchiveInputStream(tarByteInputStream);

    // Verifies that all the files have been added to the tarball stream.
    Files.walk(layerDirectory)
        .filter(path -> !path.equals(layerDirectory))
        .forEach(
            path -> {
              try {
                TarArchiveEntry header = tarArchiveInputStream.getNextTarEntry();

                Path expectedExtractionPath =
                    Paths.get("extract/here").resolve(layerDirectory.relativize(path));
                Assert.assertEquals(expectedExtractionPath, Paths.get(header.getName()));

                // If is a normal file, checks that the file contents match.
                if (path.toFile().isFile()) {
                  ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                  Blob blob = Blobs.from(path.toFile());
                  blob.writeTo(byteArrayOutputStream);
                  String expectedFileString =
                      new String(byteArrayOutputStream.toByteArray(), StandardCharsets.UTF_8);

                  String extractedFileString =
                      CharStreams.toString(
                          new InputStreamReader(tarArchiveInputStream, StandardCharsets.UTF_8));

                  Assert.assertEquals(expectedFileString, extractedFileString);
                }
              } catch (IOException ex) {
                throw new RuntimeException(ex);
              }
            });
  }
}
