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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Assert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests for {@link LockFile}. */
class LockFileTest {

  @TempDir public Path temporaryFolder;

  @Test
  void testLockAndRelease() throws InterruptedException {
    AtomicInteger atomicInt = new AtomicInteger(0);

    // Runnable that would produce a race condition without a lock file
    Runnable procedure =
        () -> {
          try (LockFile ignored = LockFile.lock(temporaryFolder.resolve("testLock"))) {
            Assert.assertTrue(Files.exists(temporaryFolder.resolve("testLock")));

            int valueBeforeSleep = atomicInt.intValue();
            Thread.sleep(100);
            atomicInt.set(valueBeforeSleep + 1);

          } catch (InterruptedException | IOException ex) {
            throw new AssertionError(ex);
          }
        };

    // Run the runnable once in this thread + once in the main thread
    Thread thread = new Thread(procedure);
    thread.start();
    procedure.run();
    thread.join();

    // Assert no overlap while lock was in place
    Assert.assertEquals(2, atomicInt.intValue());
  }
}
