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

import com.google.cloud.tools.jib.event.progress.ThrottledAccumulatingConsumer;
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
import org.junit.jupiter.api.Test;

/** Tests for {@link NotifyingOutputStream}. */
class NotifyingOutputStreamTest {

  @Test
  void testCallback_correctSequence() throws IOException {
    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

    List<Long> byteCounts = new ArrayList<>();

    try (NotifyingOutputStream notifyingOutputStream =
        new NotifyingOutputStream(byteArrayOutputStream, byteCounts::add)) {
      notifyingOutputStream.write(0);
      notifyingOutputStream.write(new byte[] {1, 2, 3});
      notifyingOutputStream.write(new byte[] {1, 2, 3, 4, 5}, 3, 2);
    }

    Assert.assertEquals(Arrays.asList(1L, 3L, 2L), byteCounts);
    Assert.assertArrayEquals(new byte[] {0, 1, 2, 3, 4, 5}, byteArrayOutputStream.toByteArray());
  }

  @Test
  void testDelay() throws IOException {
    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

    List<Long> byteCounts = new ArrayList<>();

    Queue<Instant> instantQueue = new ArrayDeque<>();
    instantQueue.add(Instant.EPOCH);

    try (ThrottledAccumulatingConsumer byteCounter =
            new ThrottledAccumulatingConsumer(
                byteCounts::add, Duration.ofSeconds(3), instantQueue::remove);
        NotifyingOutputStream notifyingOutputStream =
            new NotifyingOutputStream(byteArrayOutputStream, byteCounter)) {
      instantQueue.add(Instant.EPOCH);
      notifyingOutputStream.write(100);
      instantQueue.add(Instant.EPOCH);
      notifyingOutputStream.write(new byte[] {101, 102, 103});
      instantQueue.add(Instant.EPOCH.plusSeconds(4));
      notifyingOutputStream.write(new byte[] {104, 105, 106});

      instantQueue.add(Instant.EPOCH.plusSeconds(10));
      notifyingOutputStream.write(new byte[] {107, 108});

      instantQueue.add(Instant.EPOCH.plusSeconds(10));
      notifyingOutputStream.write(new byte[] {109});
      instantQueue.add(Instant.EPOCH.plusSeconds(13));
      notifyingOutputStream.write(new byte[] {0, 110}, 1, 1);
    }

    Assert.assertEquals(Arrays.asList(7L, 2L, 2L), byteCounts);
    Assert.assertArrayEquals(
        new byte[] {100, 101, 102, 103, 104, 105, 106, 107, 108, 109, 110},
        byteArrayOutputStream.toByteArray());
  }
}
