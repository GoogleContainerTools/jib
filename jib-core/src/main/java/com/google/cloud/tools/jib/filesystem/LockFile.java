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

package com.google.cloud.tools.jib.filesystem;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;

/** Creates and deletes lock files. */
public class LockFile {

  private final Path lockFile;
  private final FileLock lock;

  private LockFile(Path lockFile, FileLock lock) {
    this.lockFile = lockFile;
    this.lock = lock;
  }

  /**
   * Creates a lock file.
   *
   * @param lockFile the path of the lock file
   * @return a new {@link LockFile} that can be released later
   * @throws IOException if creating the lock file fails
   */
  public static LockFile lock(Path lockFile) throws IOException {
    Files.createDirectories(lockFile.getParent());
    while (true) {
      try {
        FileLock fileLock = new FileOutputStream(lockFile.toFile()).getChannel().tryLock();
        if (fileLock != null) {
          return new LockFile(lockFile, fileLock);
        }
      } catch (Exception ignored) {
      }

      try {
        Thread.sleep(500);
      } catch (InterruptedException ignored) {
      }
    }
  }

  /** Releases the lock file. */
  public void release() {
    try {
      lock.release();
    } catch (IOException ex) {
      throw new IllegalStateException("Unable to release lock", ex);
    }

    try {
      Files.delete(lockFile);
    } catch (IOException ignored) {
    }
  }
}
