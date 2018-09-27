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

package com.google.cloud.tools.jib.docker;

import com.google.cloud.tools.jib.blob.Blobs;
import com.google.cloud.tools.jib.image.ImageReference;
import com.google.cloud.tools.jib.image.InvalidImageReferenceException;
import com.google.common.io.ByteStreams;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests for {@link DockerClient}. */
@RunWith(MockitoJUnitRunner.class)
public class DockerClientTest {

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Mock private ProcessBuilder mockProcessBuilder;
  @Mock private Process mockProcess;

  @Before
  public void setUp() throws IOException {
    Mockito.when(mockProcessBuilder.start()).thenReturn(mockProcess);
  }

  @Test
  public void testIsDockerInstalled_pass() {
    Assert.assertTrue(new DockerClient(ignored -> mockProcessBuilder).isDockerInstalled());
  }

  @Test
  public void testIsDockerInstalled_fail() {
    ProcessBuilder nonexistentProcessBuilder =
        new ProcessBuilder(Paths.get("path/to/nonexistent/file").toString());
    Assert.assertFalse(new DockerClient(ignored -> nonexistentProcessBuilder).isDockerInstalled());
  }

  @Test
  public void testLoad() throws IOException, InterruptedException {
    DockerClient testDockerClient =
        new DockerClient(
            subcommand -> {
              Assert.assertEquals(Collections.singletonList("load"), subcommand);
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
  public void testLoad_stdinFail() throws InterruptedException {
    DockerClient testDockerClient = new DockerClient(ignored -> mockProcessBuilder);

    Mockito.when(mockProcess.getOutputStream())
        .thenReturn(
            new OutputStream() {

              @Override
              public void write(int b) throws IOException {
                throw new IOException();
              }
            });
    Mockito.when(mockProcess.getErrorStream())
        .thenReturn(new ByteArrayInputStream("error".getBytes(StandardCharsets.UTF_8)));

    try {
      testDockerClient.load(Blobs.from("jib"));
      Assert.fail("Write should have failed");

    } catch (IOException ex) {
      Assert.assertEquals("'docker load' command failed with error: error", ex.getMessage());
    }
  }

  @Test
  public void testLoad_stdinFail_stderrFail() throws InterruptedException {
    DockerClient testDockerClient = new DockerClient(ignored -> mockProcessBuilder);
    IOException expectedIOException = new IOException();

    Mockito.when(mockProcess.getOutputStream())
        .thenReturn(
            new OutputStream() {

              @Override
              public void write(int b) throws IOException {
                throw expectedIOException;
              }
            });
    Mockito.when(mockProcess.getErrorStream())
        .thenReturn(
            new InputStream() {

              @Override
              public int read() throws IOException {
                throw new IOException();
              }
            });

    try {
      testDockerClient.load(Blobs.from("jib"));
      Assert.fail("Write should have failed");

    } catch (IOException ex) {
      Assert.assertSame(expectedIOException, ex);
    }
  }

  @Test
  public void testLoad_stdoutFail() throws InterruptedException {
    DockerClient testDockerClient = new DockerClient(ignored -> mockProcessBuilder);
    Mockito.when(mockProcess.waitFor()).thenReturn(1);

    Mockito.when(mockProcess.getOutputStream()).thenReturn(ByteStreams.nullOutputStream());
    Mockito.when(mockProcess.getInputStream())
        .thenReturn(new ByteArrayInputStream("ignored".getBytes(StandardCharsets.UTF_8)));

    try {
      testDockerClient.load(Blobs.from("jib"));
      Assert.fail("Process should have failed");

    } catch (IOException ex) {
      Assert.assertEquals("'docker load' command failed with output: ignored", ex.getMessage());
    }
  }

  @Test
  public void testTag() throws InterruptedException, IOException, InvalidImageReferenceException {
    DockerClient testDockerClient =
        new DockerClient(
            subcommand -> {
              Assert.assertEquals(Arrays.asList("tag", "original", "new"), subcommand);
              return mockProcessBuilder;
            });
    Mockito.when(mockProcess.waitFor()).thenReturn(0);

    testDockerClient.tag(ImageReference.of(null, "original", null), ImageReference.parse("new"));
  }

  @Test
  public void testTag_fail()
      throws InterruptedException, IOException, InvalidImageReferenceException {
    DockerClient testDockerClient =
        new DockerClient(
            subcommand -> {
              Assert.assertEquals(Arrays.asList("tag", "original", "new"), subcommand);
              return mockProcessBuilder;
            });
    Mockito.when(mockProcess.waitFor()).thenReturn(1);

    Mockito.when(mockProcess.getErrorStream())
        .thenReturn(new ByteArrayInputStream("error".getBytes(StandardCharsets.UTF_8)));

    try {
      testDockerClient.tag(ImageReference.of(null, "original", null), ImageReference.parse("new"));
      Assert.fail("docker tag should have failed");

    } catch (IOException ex) {
      Assert.assertEquals("'docker tag' command failed with error: error", ex.getMessage());
    }
  }
}
