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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.junit.Assert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** Tests for {@link Timer}. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TimerTest {

  @Mock private Clock mockClock;

  @Test
  void testLap() {
    Mockito.when(mockClock.instant()).thenReturn(Instant.EPOCH);
    Timer parentTimer = new Timer(mockClock, null);
    Mockito.when(mockClock.instant()).thenReturn(Instant.EPOCH.plusMillis(5));
    Duration parentDuration1 = parentTimer.lap();
    Mockito.when(mockClock.instant()).thenReturn(Instant.EPOCH.plusMillis(15));
    Duration parentDuration2 = parentTimer.lap();

    Mockito.when(mockClock.instant()).thenReturn(Instant.EPOCH.plusMillis(16));
    Timer childTimer = new Timer(mockClock, parentTimer);
    Mockito.when(mockClock.instant()).thenReturn(Instant.EPOCH.plusMillis(16).plusNanos(1));
    Duration childDuration = childTimer.lap();

    Mockito.when(mockClock.instant()).thenReturn(Instant.EPOCH.plusMillis(16).plusNanos(2));
    Duration parentDuration3 = parentTimer.lap();

    Assert.assertTrue(parentDuration2.compareTo(parentDuration1) > 0);
    Assert.assertTrue(parentDuration1.compareTo(parentDuration3) > 0);
    Assert.assertTrue(parentDuration3.compareTo(childDuration) > 0);
  }
}
