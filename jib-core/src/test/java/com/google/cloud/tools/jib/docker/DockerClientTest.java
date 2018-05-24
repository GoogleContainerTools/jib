/*
 * Copyright 2018 Google LLC. All rights reserved.
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

package com.google.cloud.tools.jib.docker;

import com.google.cloud.tools.jib.blob.Blobs;
import com.google.common.io.ByteStreams;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests for {@link DockerClient}. */
@RunWith(MockitoJUnitRunner.class)
public class DockerClientTest {

  @Mock private ProcessBuilder mockProcessBuilder;
  @Mock private Process mockProcess;

  @Before
  public void setUp() throws IOException {
    Mockito.when(mockProcessBuilder.start()).thenReturn(mockProcess);
  }

  @Test
  public void testLoad() throws IOException, InterruptedException {
    DockerClient testDockerClient =
        new DockerClient(
            subcommand -> {
              Assert.assertEquals("load", subcommand.get(0));
              return mockProcessBuilder;
            });
    Mockito.when(mockProcess.waitFor()).thenReturn(0);

    // Captures stdin.
    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    Mockito.when(mockProcess.getOutputStream()).thenReturn(byteArrayOutputStream);

    // Simulates stdout.
    Mockito.when(mockProcess.getInputStream())
        .thenReturn(new ByteArrayInputStream("output".getBytes(StandardCharsets.UTF_8)));

    String output = testDockerClient.load(Blobs.from("jib"));

    Assert.assertEquals(
        "jib", new String(byteArrayOutputStream.toByteArray(), StandardCharsets.UTF_8));
    Assert.assertEquals("output", output);
  }

  @Test
  public void testLoad_fail() throws InterruptedException {
    DockerClient testDockerClient = new DockerClient(ignored -> mockProcessBuilder);
    Mockito.when(mockProcess.waitFor()).thenReturn(1);

    Mockito.when(mockProcess.getOutputStream()).thenReturn(ByteStreams.nullOutputStream());
    Mockito.when(mockProcess.getInputStream())
        .thenReturn(new ByteArrayInputStream("failed".getBytes(StandardCharsets.UTF_8)));

    try {
      testDockerClient.load(Blobs.from("jib"));
      Assert.fail("Process should have failed");

    } catch (IOException ex) {
      Assert.assertEquals("'docker load' command failed with output: failed", ex.getMessage());
    }
  }
}
