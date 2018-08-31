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

import com.google.common.io.ByteStreams;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
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
    String canonicalDestination = destination.toFile().getCanonicalPath();

    try (InputStream fileIn = Files.newInputStream(archive);
        ZipInputStream zipIn = new ZipInputStream(new BufferedInputStream(fileIn))) {

      for (ZipEntry entry = zipIn.getNextEntry(); entry != null; entry = zipIn.getNextEntry()) {
        Path entryPath = destination.resolve(entry.getName());

        String canonicalTarget = entryPath.toFile().getCanonicalPath();
        if (!canonicalTarget.startsWith(canonicalDestination + File.separator)) {
          String offender = entry.getName() + " from " + archive;
          throw new IOException("Blocked unzipping files outside destination: " + offender);
        }

        if (entry.isDirectory()) {
          Files.createDirectories(entryPath);
        } else {
          try (OutputStream out = Files.newOutputStream(entryPath)) {
            ByteStreams.copy(zipIn, out);
          }
        }
      }
    }
  }
}
