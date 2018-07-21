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
import com.google.common.io.ByteSource;
import com.google.common.io.MoreFiles;
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
   * Maps from {@link TarArchiveEntry} to a {@link ByteSource}. The order of the entries is the
   * order they belong in the tarball.
   */
  private final LinkedHashMap<TarArchiveEntry, ByteSource> archiveMap = new LinkedHashMap<>();

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
      for (Map.Entry<TarArchiveEntry, ByteSource> entry : archiveMap.entrySet()) {
        tarArchiveOutputStream.putArchiveEntry(entry.getKey());
        tarArchiveOutputStream.write(entry.getValue().read());
        tarArchiveOutputStream.closeArchiveEntry();
      }
    }
  }

  /**
   * Adds a {@link TarArchiveEntry} to the archive.
   *
   * @param entry the {@link TarArchiveEntry}
   */
  public void addEntry(TarArchiveEntry entry) {
    archiveMap.put(
        entry,
        entry.isFile() ? MoreFiles.asByteSource(entry.getFile().toPath()) : ByteSource.empty());
  }

  /**
   * Adds a blob to the archive.
   *
   * @param contents the blob contents to add to the tarball
   * @param name the name of the entry (i.e. filename)
   */
  public void addEntry(byte[] contents, String name) {
    TarArchiveEntry entry = new TarArchiveEntry(name);
    entry.setSize(contents.length);
    archiveMap.put(entry, ByteSource.wrap(contents));
  }

  /** @return a new {@link Blob} that can stream the uncompressed tarball archive BLOB. */
  public Blob toBlob() {
    return Blobs.from(this::writeEntriesAsTarArchive);
  }
}
