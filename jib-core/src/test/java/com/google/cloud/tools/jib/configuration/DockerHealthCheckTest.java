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
import org.junit.Assert;
import org.junit.Test;

/** Tests for {@link DockerHealthCheck}. */
public class DockerHealthCheckTest {

  @Test
  public void testBuild_parameters() {
    DockerHealthCheck healthCheck =
        DockerHealthCheck.builderWithShellCommand("echo hi")
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
  public void testBuild_propagated() {
    DockerHealthCheck healthCheck = DockerHealthCheck.inherited();
    Assert.assertTrue(healthCheck.isInherited());
  }

  @Test
  public void testBuild_execArray() {
    DockerHealthCheck healthCheck =
        DockerHealthCheck.builderWithExecCommand("test", "command").build();
    Assert.assertEquals(ImmutableList.of("CMD", "test", "command"), healthCheck.getCommand());
  }

  @Test
  public void testBuild_execList() {
    DockerHealthCheck healthCheck =
        DockerHealthCheck.builderWithExecCommand(ImmutableList.of("test", "command")).build();
    Assert.assertEquals(ImmutableList.of("CMD", "test", "command"), healthCheck.getCommand());
  }

  @Test
  public void testBuild_shell() {
    DockerHealthCheck healthCheck =
        DockerHealthCheck.builderWithShellCommand("shell command").build();
    Assert.assertEquals(ImmutableList.of("CMD-SHELL", "shell command"), healthCheck.getCommand());
  }

  @Test
  public void testDisabled() {
    DockerHealthCheck healthCheck = DockerHealthCheck.disabled();
    Assert.assertEquals(ImmutableList.of("NONE"), healthCheck.getCommand());
  }
}
