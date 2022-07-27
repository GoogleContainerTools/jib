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

import com.google.cloud.tools.jib.api.DescriptorDigest;
import com.google.cloud.tools.jib.api.DockerClient;
import com.google.cloud.tools.jib.api.ImageReference;
import com.google.cloud.tools.jib.docker.CliDockerClient.DockerImageDetails;
import com.google.cloud.tools.jib.image.ImageTarball;
import com.google.cloud.tools.jib.json.JsonTemplateMapper;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteStreams;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.security.DigestException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.AdditionalAnswers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.VoidAnswer1;

/** Tests for {@link CliDockerClient}. */
@RunWith(MockitoJUnitRunner.class)
public class CliDockerClientTest {

  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Mock private ProcessBuilder mockProcessBuilder;
  @Mock private Process mockProcess;
  @Mock private ImageTarball imageTarball;

  @Before
  public void setUp() throws IOException {
    Mockito.when(mockProcessBuilder.start()).thenReturn(mockProcess);
    Mockito.doAnswer(
            AdditionalAnswers.answerVoid(
                (VoidAnswer1<OutputStream>)
                    out -> out.write("jib".getBytes(StandardCharsets.UTF_8))))
        .when(imageTarball)
        .writeTo(Mockito.any(OutputStream.class));
  }

  @Test
  public void testIsDockerInstalled_fail() {
    Assert.assertFalse(CliDockerClient.isDockerInstalled(Paths.get("path/to/nonexistent/file")));
  }

  @Test
  public void testLoad() throws IOException, InterruptedException {
    DockerClient testDockerClient =
        new CliDockerClient(
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

    String output = testDockerClient.load(imageTarball, ignored -> {});

    Assert.assertEquals(
        "jib", new String(byteArrayOutputStream.toByteArray(), StandardCharsets.UTF_8));
    Assert.assertEquals("output", output);
  }

  @Test
  public void testLoad_stdinFail() throws InterruptedException {
    DockerClient testDockerClient = new CliDockerClient(ignored -> mockProcessBuilder);

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
      testDockerClient.load(imageTarball, ignored -> {});
      Assert.fail("Write should have failed");

    } catch (IOException ex) {
      Assert.assertEquals("'docker load' command failed with error: error", ex.getMessage());
    }
  }

  @Test
  public void testLoad_stdinFail_stderrFail() throws InterruptedException {
    DockerClient testDockerClient = new CliDockerClient(ignored -> mockProcessBuilder);

    Mockito.when(mockProcess.getOutputStream())
        .thenReturn(
            new OutputStream() {

              @Override
              public void write(int b) throws IOException {
                throw new IOException("I/O failed");
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
      testDockerClient.load(imageTarball, ignored -> {});
      Assert.fail("Write should have failed");

    } catch (IOException ex) {
      Assert.assertEquals("'docker load' command failed with error: I/O failed", ex.getMessage());
    }
  }

  @Test
  public void testLoad_stdoutFail() throws InterruptedException {
    DockerClient testDockerClient = new CliDockerClient(ignored -> mockProcessBuilder);
    Mockito.when(mockProcess.waitFor()).thenReturn(1);

    Mockito.when(mockProcess.getOutputStream()).thenReturn(ByteStreams.nullOutputStream());
    Mockito.when(mockProcess.getInputStream())
        .thenReturn(new ByteArrayInputStream("ignored".getBytes(StandardCharsets.UTF_8)));
    Mockito.when(mockProcess.getErrorStream())
        .thenReturn(new ByteArrayInputStream("error".getBytes(StandardCharsets.UTF_8)));

    try {
      testDockerClient.load(imageTarball, ignored -> {});
      Assert.fail("Process should have failed");

    } catch (IOException ex) {
      Assert.assertEquals("'docker load' command failed with error: error", ex.getMessage());
    }
  }

  @Test
  public void testSave() throws InterruptedException, IOException {
    DockerClient testDockerClient = makeDockerSaveClient();
    Mockito.when(mockProcess.waitFor()).thenReturn(0);

    long[] counter = new long[1];
    testDockerClient.save(
        ImageReference.of(null, "testimage", null),
        temporaryFolder.getRoot().toPath().resolve("out.tar"),
        bytes -> counter[0] += bytes);

    // InputStream writes "jib", so 3 bytes of progress should have been counted.
    Assert.assertEquals(3, counter[0]);
  }

  @Test
  public void testSave_fail() throws InterruptedException {
    DockerClient testDockerClient = makeDockerSaveClient();
    Mockito.when(mockProcess.waitFor()).thenReturn(1);

    Mockito.when(mockProcess.getErrorStream())
        .thenReturn(new ByteArrayInputStream("error".getBytes(StandardCharsets.UTF_8)));

    try {
      testDockerClient.save(
          ImageReference.of(null, "testimage", null),
          temporaryFolder.getRoot().toPath().resolve("out.tar"),
          ignored -> {});
      Assert.fail("docker save should have failed");

    } catch (IOException ex) {
      Assert.assertEquals("'docker save' command failed with error: error", ex.getMessage());
    }
  }

  @Test
  public void testDefaultProcessorBuilderFactory_customExecutable() {
    ProcessBuilder processBuilder =
        CliDockerClient.defaultProcessBuilderFactory("docker-executable", ImmutableMap.of())
            .apply(Arrays.asList("sub", "command"));

    Assert.assertEquals(
        Arrays.asList("docker-executable", "sub", "command"), processBuilder.command());
    Assert.assertEquals(System.getenv(), processBuilder.environment());
  }

  @Test
  public void testDefaultProcessorBuilderFactory_customEnvironment() {
    ImmutableMap<String, String> environment = ImmutableMap.of("Key1", "Value1");

    Map<String, String> expectedEnvironment = new HashMap<>(System.getenv());
    expectedEnvironment.putAll(environment);

    ProcessBuilder processBuilder =
        CliDockerClient.defaultProcessBuilderFactory("docker", environment)
            .apply(Collections.emptyList());

    Assert.assertEquals(expectedEnvironment, processBuilder.environment());
  }

  @Test
  public void testSize_fail() throws InterruptedException {
    DockerClient testDockerClient =
        new CliDockerClient(
            subcommand -> {
              Assert.assertEquals("inspect", subcommand.get(0));
              return mockProcessBuilder;
            });
    Mockito.when(mockProcess.waitFor()).thenReturn(1);

    Mockito.when(mockProcess.getErrorStream())
        .thenReturn(new ByteArrayInputStream("error".getBytes(StandardCharsets.UTF_8)));

    try {
      testDockerClient.inspect(ImageReference.of(null, "image", null));
      Assert.fail("docker inspect should have failed");

    } catch (IOException ex) {
      Assert.assertEquals("'docker inspect' command failed with error: error", ex.getMessage());
    }
  }

  @Test
  public void testDockerImageDetails() throws DigestException, IOException {
    String json =
        "{\"Size\":488118507,"
            + "\"Id\":\"sha256:e8d00769c8a805a0656dbfd49d4f91cbc2e36d0199f10343d1beba36ecdcb3fd\","
            + "\"RootFS\": { \"Layers\" : ["
            + "  \"sha256:55e6b89812f369277290d098c1e44c9e85a5ab0286c649f37e66e11074f8ebd1\","
            + "  \"sha256:26b1991f37bd5b798e1523f65d7f6aa6961b75515f465cf44123fa0ad3b8961b\","
            + "  \"sha256:8bacec4e34468110538ebf108ca8ec0d880a37018a55be91b9670b8e900c593a\"]}}\n";
    DockerImageDetails results = JsonTemplateMapper.readJson(json, DockerImageDetails.class);
    Assert.assertEquals(488118507, results.getSize());
    Assert.assertEquals(
        DescriptorDigest.fromHash(
            "e8d00769c8a805a0656dbfd49d4f91cbc2e36d0199f10343d1beba36ecdcb3fd"),
        results.getImageId());
    Assert.assertEquals(
        Arrays.asList(
            DescriptorDigest.fromHash(
                "55e6b89812f369277290d098c1e44c9e85a5ab0286c649f37e66e11074f8ebd1"),
            DescriptorDigest.fromHash(
                "26b1991f37bd5b798e1523f65d7f6aa6961b75515f465cf44123fa0ad3b8961b"),
            DescriptorDigest.fromHash(
                "8bacec4e34468110538ebf108ca8ec0d880a37018a55be91b9670b8e900c593a")),
        results.getDiffIds());
  }

  @Test
  public void testDockerImageDetails_unknownProperties() throws IOException, DigestException {
    String json =
        "{\"Unknown\": [ ], \"Structure\": [ { \"Test\": 0 } ], \"Size\": 1234,"
            + "\"Id\": \"sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\","
            + "\"RootFS\": { \"Someting\": \"unrelated\", \"Layers\": ["
            + "  \"sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc\" ] } }";
    DockerImageDetails results = JsonTemplateMapper.readJson(json, DockerImageDetails.class);
    Assert.assertEquals(1234, results.getSize());
    Assert.assertEquals(
        DescriptorDigest.fromHash(
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"),
        results.getImageId());
    Assert.assertEquals(
        Arrays.asList(
            DescriptorDigest.fromHash(
                "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc")),
        results.getDiffIds());
  }

  @Test
  public void testDockerImageDetails_emptyJson() throws IOException, DigestException {
    DockerImageDetails details = JsonTemplateMapper.readJson("{}", DockerImageDetails.class);
    Assert.assertEquals(0, details.getSize());
    Assert.assertEquals(Collections.emptyList(), details.getDiffIds());
    try {
      details.getImageId();
      Assert.fail();
    } catch (DigestException ex) {
      Assert.assertEquals("Invalid digest: ", ex.getMessage());
    }
  }

  private DockerClient makeDockerSaveClient() {
    return new CliDockerClient(
        subcommand -> {
          try {
            if (subcommand.contains("{{.Size}}")) {
              // It doesn't matter what size is actually returned by 'docker inspect' here, so just
              // use 150000 as a placeholder.
              Process mockSizeProcess = Mockito.mock(Process.class);
              Mockito.when(mockSizeProcess.getInputStream())
                  .thenReturn(new ByteArrayInputStream("150000".getBytes(StandardCharsets.UTF_8)));
              Mockito.when(mockProcessBuilder.start()).thenReturn(mockSizeProcess);
            } else {
              Assert.assertEquals(Arrays.asList("save", "testimage"), subcommand);
              Mockito.when(mockProcess.getInputStream())
                  .thenReturn(new ByteArrayInputStream("jib".getBytes(StandardCharsets.UTF_8)));
              Mockito.when(mockProcessBuilder.start()).thenReturn(mockProcess);
            }
          } catch (IOException ignored) {
            // ignored
          }
          return mockProcessBuilder;
        });
  }
}
