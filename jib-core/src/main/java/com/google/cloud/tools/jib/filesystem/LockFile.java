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

  // In Gradle composite builds with independent buildSrc directories, each included build loads
  // jib-core in its own classloader. A normal static field creates separate lockMap instances per
  // classloader, failing to prevent concurrent FileChannel.lock() calls on the same file and
  // causing OverlappingFileLockException (#3347). This is admittedly a hack, but there is no
  // clean way to share state across classloaders in the same JVM. System.getProperties() returns
  // the same Hashtable regardless of classloader, so it serves as a JVM-global namespace.
  // The map's key/value types (Path, Lock) are bootstrap-loaded, so they're safe to cast.
  @SuppressWarnings("unchecked")
  private static final ConcurrentHashMap<Path, Lock> lockMap =
      (ConcurrentHashMap<Path, Lock>)
          System.getProperties()
              .computeIfAbsent(
                  "jib.lockFile.lockMap", key -> new ConcurrentHashMap<Path, Lock>());

  private final Path lockFilePath;
  private final FileLock fileLock;
  private final OutputStream outputStream;

  private LockFile(Path lockFilePath, FileLock fileLock, OutputStream outputStream) {
    this.lockFilePath = lockFilePath;
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
      Preconditions.checkNotNull(lockMap.get(lockFilePath)).unlock();
    }
  }
}
