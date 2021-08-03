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

import com.google.common.base.Preconditions;
import java.io.Closeable;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/** Creates and deletes lock files. */
public class LockFile implements Closeable {

  private static final ConcurrentHashMap<Path, Lock> lockMap = new ConcurrentHashMap<>();

  private final Path lockFile;
  private final FileLock fileLock;
  private final OutputStream outputStream;

  private LockFile(Path lockFile, FileLock fileLock, OutputStream outputStream) {
    this.lockFile = lockFile;
    this.fileLock = fileLock;
    this.outputStream = outputStream;
  }

  /**
   * Creates a lock file.
   *
   * @param lockFile the path of the lock file
   * @return a new {@link LockFile} that can be released later
   * @throws IOException if creating the lock file fails
   */
  public static LockFile lock(Path lockFile) throws IOException {
    try {
      // This first lock is to prevent multiple threads from calling FileChannel.lock(), which would
      // otherwise throw OverlappingFileLockException
      lockMap.computeIfAbsent(lockFile, key -> new ReentrantLock()).lockInterruptibly();

    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IOException("Interrupted while trying to acquire lock", ex);
    }

    Files.createDirectories(lockFile.getParent());
    FileOutputStream outputStream = new FileOutputStream(lockFile.toFile());
    FileLock fileLock = null;
    try {
      fileLock = outputStream.getChannel().lock();
      return new LockFile(lockFile, fileLock, outputStream);

    } finally {
      if (fileLock == null) {
        outputStream.close();
      }
    }
  }

  /** Releases the lock file. */
  @Override
  public void close() {
    try {
      fileLock.release();
      outputStream.close();

    } catch (IOException ex) {
      throw new IllegalStateException("Unable to release lock", ex);

    } finally {
      Preconditions.checkNotNull(lockMap.get(lockFile)).unlock();
    }
  }
}
