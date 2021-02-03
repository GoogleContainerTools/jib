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

package com.google.cloud.tools.jib.plugins.common;

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
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Utility class for Zip archives. */
public class ZipUtil {

  /**
   * Unzips {@code archive} into {@code destination}.
   *
   * @param archive zip archive to unzip
   * @param destination target root for unzipping
   * @throws IOException when I/O error occurs
   */
  public static void unzip(Path archive, Path destination) throws IOException {
    unzip(archive, destination, false);
  }

  /**
   * Unzips {@code archive} into {@code destination}.
   *
   * @param archive zip archive to unzip
   * @param destination target root for unzipping
   * @param enableReproducibleTimestamps whether or not reproducible timestamps should be used
   * @throws IOException when I/O error occurs
   */
  public static void unzip(Path archive, Path destination, boolean enableReproducibleTimestamps)
      throws IOException {
    String canonicalDestination = destination.toFile().getCanonicalPath();
    List<ZipEntry> entries = new ArrayList<>();
    try (InputStream fileIn = new BufferedInputStream(Files.newInputStream(archive));
        ZipInputStream zipIn = new ZipInputStream(fileIn)) {
      for (ZipEntry entry = zipIn.getNextEntry(); entry != null; entry = zipIn.getNextEntry()) {
        entries.add(entry);
        Path entryPath = destination.resolve(entry.getName());

        String canonicalTarget = entryPath.toFile().getCanonicalPath();
        if (!canonicalTarget.startsWith(canonicalDestination + File.separator)) {
          String offender = entry.getName() + " from " + archive;
          throw new IOException("Blocked unzipping files outside destination: " + offender);
        }

        if (entry.isDirectory()) {
          Files.createDirectories(entryPath);
        } else {
          if (entryPath.getParent() != null) {
            Files.createDirectories(entryPath.getParent());
          }
          try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(entryPath))) {
            ByteStreams.copy(zipIn, out);
          }
        }
      }
    }
    preserveModificationTimes(destination, entries, enableReproducibleTimestamps);
  }

  /**
   * Preserve modification time of files and directories in a zip file. If a directory is not an
   * entry in the zip file and reproducible timestamps are enabled then it's modification timestamp
   * is set to a constant value.
   *
   * @param destination target root for unzipping
   * @param entries list of entries in zip file
   * @param enableReproducibleTimestamps whether or not reproducible timestamps should be used
   * @throws IOException when I/O error occurs
   */
  private static void preserveModificationTimes(
      Path destination, List<ZipEntry> entries, boolean enableReproducibleTimestamps)
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
    for (ZipEntry entry : entries) {
      Files.setLastModifiedTime(destination.resolve(entry.getName()), entry.getLastModifiedTime());
    }
  }
}
