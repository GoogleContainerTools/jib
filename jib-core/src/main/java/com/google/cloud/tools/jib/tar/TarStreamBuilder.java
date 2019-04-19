/*
 * Copyright 2017 Google LLC.
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
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;

/** Builds a tarball archive. */
public class TarStreamBuilder {

  /**
   * Represents contents of a tar entry. It may represent "yet-to-be-realized" contents; for
   * example, loading the actual contents from a file may happen only when writing the contents into
   * an output stream.
   */
  @FunctionalInterface
  private interface Contents {

    void writeTo(OutputStream out) throws IOException;
  }

  /**
   * Maps from {@link TarArchiveEntry} to a {@link Contents}. The order of the entries is the order
   * they belong in the tarball.
   */
  private final LinkedHashMap<TarArchiveEntry, Contents> archiveMap = new LinkedHashMap<>();

  /**
   * Writes each entry in the filesystem to the tarball archive stream.
   *
   * @param out the stream to write to.
   * @throws IOException if building the tarball fails.
   */
  public void writeAsTarArchiveTo(OutputStream out) throws IOException {
    try (TarArchiveOutputStream tarArchiveOutputStream =
        new TarArchiveOutputStream(out, StandardCharsets.UTF_8.name())) {
      // Enables PAX extended headers to support long file names.
      tarArchiveOutputStream.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
      for (Map.Entry<TarArchiveEntry, Contents> entry : archiveMap.entrySet()) {
        tarArchiveOutputStream.putArchiveEntry(entry.getKey());
        entry.getValue().writeTo(tarArchiveOutputStream);
        tarArchiveOutputStream.closeArchiveEntry();
      }
    }
  }

  /**
   * Adds a {@link TarArchiveEntry} to the archive.
   *
   * @param entry the {@link TarArchiveEntry}
   */
  public void addTarArchiveEntry(TarArchiveEntry entry) {
    if (!entry.isFile()) {
      archiveMap.put(entry, ignored -> {});
    } else {
      Blob fileBlob = Blobs.from(entry.getFile().toPath());
      archiveMap.put(entry, outputStream -> fileBlob.writeTo(outputStream));
    }
  }

  /**
   * Adds byte contents to the archive. Note that this should be used with raw bytes and not file
   * contents; for adding files to the archive, use {@link #addTarArchiveEntry}.
   *
   * @param contents the bytes to add to the tarball
   * @param name the name of the entry (i.e. filename)
   */
  public void addByteEntry(byte[] contents, String name) {
    TarArchiveEntry entry = new TarArchiveEntry(name);
    entry.setSize(contents.length);
    archiveMap.put(entry, outputStream -> outputStream.write(contents));
  }

  /**
   * Adds a blob to the archive. Note that this should be used with non-file {@link Blob}s; for
   * adding files to the archive, use {@link #addTarArchiveEntry}.
   *
   * @param blob the {@link Blob} to add to the tarball
   * @param size the size (in bytes) of {@code blob}
   * @param name the name of the entry (i.e. filename)
   */
  public void addBlobEntry(Blob blob, long size, String name) {
    TarArchiveEntry entry = new TarArchiveEntry(name);
    entry.setSize(size);
    archiveMap.put(entry, outputStream -> blob.writeTo(outputStream));
  }
}
