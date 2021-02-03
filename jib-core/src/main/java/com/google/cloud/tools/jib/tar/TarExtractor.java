/*
 * Copyright 2019 Google LLC.
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

import com.google.cloud.tools.jib.filesystem.DirectoryWalker;
import com.google.common.io.ByteStreams;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;

/** Extracts a tarball. */
public class TarExtractor {

  /**
   * Extracts a tarball to the specified destination.
   *
   * @param source the tarball to extract
   * @param destination the output directory
   * @throws IOException if extraction fails
   */
  public static void extract(Path source, Path destination) throws IOException {
    extract(source, destination, false);
  }

  /**
   * Extracts a tarball to the specified destination.
   *
   * @param source the tarball to extract
   * @param destination the output directory
   * @param enableReproducibleTimestamps whether or not reproducible timestamps should be used
   * @throws IOException if extraction fails
   */
  public static void extract(Path source, Path destination, boolean enableReproducibleTimestamps)
      throws IOException {
    String canonicalDestination = destination.toFile().getCanonicalPath();
    List<TarArchiveEntry> entries = new ArrayList<>();
    try (InputStream in = new BufferedInputStream(Files.newInputStream(source));
        TarArchiveInputStream tarArchiveInputStream = new TarArchiveInputStream(in)) {
      for (TarArchiveEntry entry = tarArchiveInputStream.getNextTarEntry();
          entry != null;
          entry = tarArchiveInputStream.getNextTarEntry()) {
        entries.add(entry);
        Path entryPath = destination.resolve(entry.getName());

        String canonicalTarget = entryPath.toFile().getCanonicalPath();
        if (!canonicalTarget.startsWith(canonicalDestination + File.separator)) {
          String offender = entry.getName() + " from " + source;
          throw new IOException("Blocked unzipping files outside destination: " + offender);
        }
        if (entry.isDirectory()) {
          Files.createDirectories(entryPath);
        } else {
          if (entryPath.getParent() != null) {
            Files.createDirectories(entryPath.getParent());
          }

          if (entry.isSymbolicLink()) {
            Files.createSymbolicLink(entryPath, Paths.get(entry.getLinkName()));
          } else {
            try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(entryPath))) {
              ByteStreams.copy(tarArchiveInputStream, out);
            }
          }
        }
      }
    }
    preserveModificationTimes(destination, entries, enableReproducibleTimestamps);
  }

  /**
   * Preserve modification time of files and directories in a tar file. If a directory is not an
   * entry in the tar file and reproducible timestamps are enabled then it's modification timestamp
   * is set to a constant value.
   *
   * @param destination target root for unzipping
   * @param entries list of entries in tar file
   * @param enableReproducibleTimestamps whether or not reproducible timestamps should be used
   * @throws IOException when I/O error occurs
   */
  private static void preserveModificationTimes(
      Path destination, List<TarArchiveEntry> entries, boolean enableReproducibleTimestamps)
      throws IOException {
    if (!Files.exists(destination)) {
      return;
    }
    if (enableReproducibleTimestamps) {
      FileTime epochPlusOne = FileTime.fromMillis(1000L);
      new DirectoryWalker(destination)
          .filter(path -> Files.isDirectory(path))
          .walk(path -> Files.setLastModifiedTime(path, epochPlusOne));
    }
    for (TarArchiveEntry entry : entries) {
      Files.setLastModifiedTime(
          destination.resolve(entry.getName()), FileTime.from(entry.getModTime().toInstant()));
    }
  }
}
