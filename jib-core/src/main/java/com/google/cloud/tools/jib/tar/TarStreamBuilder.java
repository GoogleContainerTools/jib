/*
 * Copyright 2017 Google LLC. All rights reserved.
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

package com.google.cloud.tools.jib.tar;

import com.google.cloud.tools.jib.blob.Blob;
import com.google.cloud.tools.jib.blob.Blobs;
import com.google.common.io.ByteStreams;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;

/** Builds a tarball archive. */
public class TarStreamBuilder {

  @FunctionalInterface
  private interface TarArchiveOutputStreamConsumer {
    void accept(TarArchiveOutputStream tarArchiveOutputStream) throws IOException;
  }

  /**
   * Maps from {@link TarArchiveEntry} to function that outputs the entry onto a {@link
   * TarArchiveOutputStream}. The order of the entries is the order they belong in the tarball.
   */
  private final LinkedHashMap<TarArchiveEntry, TarArchiveOutputStreamConsumer> archiveMap =
      new LinkedHashMap<>();

  /**
   * Writes each entry in the filesystem to the tarball archive stream.
   *
   * @param tarByteStream the stream to write to.
   * @throws IOException if building the tarball fails.
   */
  private void writeEntriesAsTarArchive(OutputStream tarByteStream) throws IOException {
    try (TarArchiveOutputStream tarArchiveOutputStream =
        new TarArchiveOutputStream(tarByteStream, StandardCharsets.UTF_8.name())) {
      // Enables PAX extended headers to support long file names.
      tarArchiveOutputStream.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
      for (Map.Entry<TarArchiveEntry, TarArchiveOutputStreamConsumer> entry :
          archiveMap.entrySet()) {
        tarArchiveOutputStream.putArchiveEntry(entry.getKey());
        entry.getValue().accept(tarArchiveOutputStream);
        tarArchiveOutputStream.closeArchiveEntry();
      }
    }
  }

  /**
   * Adds an entry to the archive.
   *
   * @param entry the entry to add.
   */
  public void addEntry(TarArchiveEntry entry) {
    archiveMap.put(
        entry,
        tarArchiveOutputStream -> {
          // Note that this will skip files that don't exist.
          if (entry.isFile()) {
            try (InputStream contentStream =
                new BufferedInputStream(Files.newInputStream(entry.getFile().toPath()))) {
              ByteStreams.copy(contentStream, tarArchiveOutputStream);
            }
          }
        });
  }

  /**
   * Adds a blob to the archive.
   *
   * @param contents the blob contents to add to the tarball.
   * @param name the name of the entry (i.e. filename).
   */
  public void addEntry(String contents, String name) {
    TarArchiveEntry entry = new TarArchiveEntry(name);
    byte[] contentsBytes = contents.getBytes(StandardCharsets.UTF_8);
    entry.setSize(contentsBytes.length);
    archiveMap.put(entry, tarArchiveOutputStream -> tarArchiveOutputStream.write(contentsBytes));
  }

  /** @return a new {@link Blob} that can stream the uncompressed tarball archive BLOB. */
  public Blob toBlob() {
    return Blobs.from(this::writeEntriesAsTarArchive);
  }
}
