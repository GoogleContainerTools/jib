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

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.Assert;
import org.junit.Test;

/** Tests for {@link Timer}. */
public class TimerTest {

  @Test
  public void testLap() throws InterruptedException {
    Timer parentTimer = new Timer();
    TimeUnit.MILLISECONDS.sleep(5);
    Duration parentDuration1 = parentTimer.lap();
    TimeUnit.MILLISECONDS.sleep(10);
    Duration parentDuration2 = parentTimer.lap();
    TimeUnit.MILLISECONDS.sleep(1);

    Timer childTimer = new Timer(parentTimer);
    Duration childDuration = childTimer.lap();

    Duration parentDuration3 = parentTimer.lap();

    Assert.assertTrue(parentDuration2.compareTo(parentDuration1) > 0);
    Assert.assertTrue(parentDuration1.compareTo(parentDuration3) > 0);
    Assert.assertTrue(parentDuration3.compareTo(childDuration) > 0);
  }
}
