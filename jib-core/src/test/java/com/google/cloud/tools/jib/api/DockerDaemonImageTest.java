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

package com.google.cloud.tools.jib.api;

import com.google.cloud.tools.jib.docker.CliDockerClient;
import com.google.common.collect.ImmutableMap;
import java.nio.file.Paths;
import org.junit.Assert;
import org.junit.Test;

/** Tests for {@link DockerDaemonImage}. */
public class DockerDaemonImageTest {

  @Test
  public void testGetters_default() throws InvalidImageReferenceException {
    DockerDaemonImage dockerDaemonImage = DockerDaemonImage.named("docker/daemon/image");

    Assert.assertEquals("docker/daemon/image", dockerDaemonImage.getImageReference().toString());
    Assert.assertEquals(
        CliDockerClient.DEFAULT_DOCKER_CLIENT, dockerDaemonImage.getDockerExecutable());
    Assert.assertEquals(0, dockerDaemonImage.getDockerEnvironment().size());
  }

  @Test
  public void testGetters() throws InvalidImageReferenceException {
    DockerDaemonImage dockerDaemonImage =
        DockerDaemonImage.named("docker/daemon/image")
            .setDockerExecutable(Paths.get("docker/binary"))
            .setDockerEnvironment(ImmutableMap.of("key", "value"));

    Assert.assertEquals(Paths.get("docker/binary"), dockerDaemonImage.getDockerExecutable());
    Assert.assertEquals(ImmutableMap.of("key", "value"), dockerDaemonImage.getDockerEnvironment());
  }
}
