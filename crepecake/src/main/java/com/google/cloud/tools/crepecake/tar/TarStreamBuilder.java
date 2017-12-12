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
import com.google.cloud.tools.crepecake.blob.Blobs;
import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteStreams;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;

/** Builds a tarball archive. */
public class TarStreamBuilder {

  /** Holds the entries added to the archive. */
  private final List<TarArchiveEntry> entries = new ArrayList<>();

  /** Writes each entry in the filesystem to the tarball archive stream. */
  private static void writeEntriesAsTarArchive(
      ImmutableList<TarArchiveEntry> entries, OutputStream tarByteStream) throws IOException {
    try (TarArchiveOutputStream tarArchiveOutputStream =
        new TarArchiveOutputStream(tarByteStream)) {
      for (TarArchiveEntry entry : entries) {
        tarArchiveOutputStream.putArchiveEntry(entry);
        InputStream contentStream = new BufferedInputStream(new FileInputStream(entry.getFile()));
        ByteStreams.copy(contentStream, tarArchiveOutputStream);
        tarArchiveOutputStream.closeArchiveEntry();
      }
    }
  }

  /**
   * Adds a file to the archive.
   *
   * @param file the file to add
   * @param path the relative archive extraction path
   */
  public void addFile(File file, String path) {
    TarArchiveEntry entry = new TarArchiveEntry(file, path);

    entries.add(entry);
  }

  /** Builds a {@link Blob} that can stream the uncompressed tarball archive BLOB. */
  public Blob toBlob() {
    ImmutableList<TarArchiveEntry> immutableEntries = ImmutableList.copyOf(entries);
    return Blobs.from(outputStream -> writeEntriesAsTarArchive(immutableEntries, outputStream));
  }
}
