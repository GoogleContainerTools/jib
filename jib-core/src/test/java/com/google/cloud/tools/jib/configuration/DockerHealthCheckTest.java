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

package com.google.cloud.tools.jib.configuration;

import com.google.common.collect.ImmutableList;
import java.time.Duration;
import java.util.Arrays;
import org.junit.Assert;
import org.junit.jupiter.api.Test;

/** Tests for {@link DockerHealthCheck}. */
class DockerHealthCheckTest {

  @Test
  void testBuild() {
    DockerHealthCheck healthCheck =
        DockerHealthCheck.fromCommand(ImmutableList.of("echo", "hi"))
            .setInterval(Duration.ofNanos(123))
            .setTimeout(Duration.ofNanos(456))
            .setStartPeriod(Duration.ofNanos(789))
            .setRetries(10)
            .build();

    Assert.assertTrue(healthCheck.getInterval().isPresent());
    Assert.assertEquals(Duration.ofNanos(123), healthCheck.getInterval().get());
    Assert.assertTrue(healthCheck.getTimeout().isPresent());
    Assert.assertEquals(Duration.ofNanos(456), healthCheck.getTimeout().get());
    Assert.assertTrue(healthCheck.getStartPeriod().isPresent());
    Assert.assertEquals(Duration.ofNanos(789), healthCheck.getStartPeriod().get());
    Assert.assertTrue(healthCheck.getRetries().isPresent());
    Assert.assertEquals(10, (int) healthCheck.getRetries().get());
  }

  @Test
  void testBuild_invalidCommand() {
    try {
      DockerHealthCheck.fromCommand(ImmutableList.of());
      Assert.fail();
    } catch (IllegalArgumentException ex) {
      Assert.assertEquals("command must not be empty", ex.getMessage());
    }

    try {
      DockerHealthCheck.fromCommand(Arrays.asList("CMD", null));
      Assert.fail();
    } catch (IllegalArgumentException ex) {
      Assert.assertEquals("command must not contain null elements", ex.getMessage());
    }
  }
}
