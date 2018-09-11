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

package com.google.cloud.tools.jib.builder;

import com.google.common.base.Preconditions;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.TimeUnit;
import org.junit.Assert;
import org.junit.Test;

/** Tests for {@link Timer}. */
public class TimerTest {

  private final Deque<Long> times = new ArrayDeque<>();

  @Test
  public void test_smoke() throws InterruptedException {
    try (Timer timer = new Timer(times::offer)) {
      TimeUnit.MILLISECONDS.sleep(1);
      timer.lap();
      TimeUnit.MILLISECONDS.sleep(10);
      timer.lap();
    }
    long time1 = Preconditions.checkNotNull(times.poll());
    long time2 = Preconditions.checkNotNull(times.poll());
    long time3 = Preconditions.checkNotNull(times.poll());
    Assert.assertTrue(time2 > time1);
    Assert.assertTrue(time1 > time3);
  }
}
