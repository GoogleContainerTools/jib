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

package com.google.cloud.tools.jib.http;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import org.junit.Assert;
import org.junit.Test;

/** Tests for {@link ListenableCountingOutputStream}. */
public class ListenableCountingOutputStreamTest {

  @Test
  public void testCallback_correctSequence() throws IOException {
    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

    List<Long> byteCounts = new ArrayList<>();

    try (ListenableCountingOutputStream listenableCountingOutputStream =
        ListenableCountingOutputStream.wrap(byteArrayOutputStream)
            .every(Duration.ofSeconds(-1))
            .forEachByteCount(byteCounts::add)) {
      listenableCountingOutputStream.write(0);
      listenableCountingOutputStream.write(new byte[] {1, 2, 3});
      listenableCountingOutputStream.write(new byte[] {1, 2, 3, 4, 5}, 3, 2);
    }

    Assert.assertEquals(Arrays.asList(1L, 3L, 2L), byteCounts);
    Assert.assertArrayEquals(new byte[] {0, 1, 2, 3, 4, 5}, byteArrayOutputStream.toByteArray());
  }

  @Test
  public void testDelay() throws IOException {
    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

    List<Long> byteCounts = new ArrayList<>();

    Queue<Instant> instantQueue = new ArrayDeque<>();
    instantQueue.add(Instant.EPOCH);

    try (ListenableCountingOutputStream listenableCountingOutputStream =
        new ListenableCountingOutputStream(
            byteArrayOutputStream, byteCounts::add, Duration.ofSeconds(3), instantQueue::remove)) {
      instantQueue.add(Instant.EPOCH);
      listenableCountingOutputStream.write(100);
      instantQueue.add(Instant.EPOCH);
      listenableCountingOutputStream.write(new byte[] {101, 102, 103});
      instantQueue.add(Instant.EPOCH.plusSeconds(4));
      listenableCountingOutputStream.write(new byte[] {104, 105, 106});

      instantQueue.add(Instant.EPOCH.plusSeconds(10));
      listenableCountingOutputStream.write(new byte[] {107, 108});

      instantQueue.add(Instant.EPOCH.plusSeconds(10));
      listenableCountingOutputStream.write(new byte[] {109});
      instantQueue.add(Instant.EPOCH.plusSeconds(13));
      listenableCountingOutputStream.write(new byte[] {0, 110}, 1, 1);

      instantQueue.add(Instant.EPOCH.plusSeconds(14));
    }

    Assert.assertEquals(Arrays.asList(7L, 2L, 2L), byteCounts);
    Assert.assertArrayEquals(
        new byte[] {100, 101, 102, 103, 104, 105, 106, 107, 108, 109, 110},
        byteArrayOutputStream.toByteArray());
  }
}
