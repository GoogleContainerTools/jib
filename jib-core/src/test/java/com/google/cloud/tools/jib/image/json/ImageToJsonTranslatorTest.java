/*
 * Copyright 2017 Google LLC.
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

package com.google.cloud.tools.jib.image.json;

import com.google.cloud.tools.jib.api.DescriptorDigest;
import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.buildplan.Port;
import com.google.cloud.tools.jib.blob.Blob;
import com.google.cloud.tools.jib.blob.BlobDescriptor;
import com.google.cloud.tools.jib.blob.Blobs;
import com.google.cloud.tools.jib.configuration.DockerHealthCheck;
import com.google.cloud.tools.jib.hash.Digests;
import com.google.cloud.tools.jib.image.Image;
import com.google.cloud.tools.jib.image.Layer;
import com.google.cloud.tools.jib.image.LayerPropertyNotFoundException;
import com.google.cloud.tools.jib.json.JsonTemplate;
import com.google.cloud.tools.jib.json.JsonTemplateMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.DigestException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import org.junit.Assert;
import org.junit.jupiter.api.Test;

/** Tests for {@link ImageToJsonTranslator}. */
class ImageToJsonTranslatorTest {

  private ImageToJsonTranslator imageToJsonTranslator;

  private void setUp(Class<? extends BuildableManifestTemplate> imageFormat)
      throws DigestException, LayerPropertyNotFoundException {
    Image.Builder testImageBuilder =
        Image.builder(imageFormat)
            .setCreated(Instant.ofEpochSecond(20))
            .setArchitecture("wasm")
            .setOs("js")
            .addEnvironmentVariable("VAR1", "VAL1")
            .addEnvironmentVariable("VAR2", "VAL2")
            .setEntrypoint(Arrays.asList("some", "entrypoint", "command"))
            .setProgramArguments(Arrays.asList("arg1", "arg2"))
            .setHealthCheck(
                DockerHealthCheck.fromCommand(ImmutableList.of("CMD-SHELL", "/checkhealth"))
                    .setInterval(Duration.ofSeconds(3))
                    .setTimeout(Duration.ofSeconds(1))
                    .setStartPeriod(Duration.ofSeconds(2))
                    .setRetries(3)
                    .build())
            .addExposedPorts(ImmutableSet.of(Port.tcp(1000), Port.tcp(2000), Port.udp(3000)))
            .addVolumes(
                ImmutableSet.of(
                    AbsoluteUnixPath.get("/var/job-result-data"),
                    AbsoluteUnixPath.get("/var/log/my-app-logs")))
            .addLabels(ImmutableMap.of("key1", "value1", "key2", "value2"))
            .setWorkingDirectory("/some/workspace")
            .setUser("tomcat");

    DescriptorDigest fakeDigest =
        DescriptorDigest.fromDigest(
            "sha256:8c662931926fa990b41da3c9f42663a537ccd498130030f9149173a0493832ad");
    testImageBuilder.addLayer(
        new Layer() {

          @Override
          public Blob getBlob() throws LayerPropertyNotFoundException {
            return Blobs.from("ignored");
          }

          @Override
          public BlobDescriptor getBlobDescriptor() throws LayerPropertyNotFoundException {
            return new BlobDescriptor(1000, fakeDigest);
          }

          @Override
          public DescriptorDigest getDiffId() throws LayerPropertyNotFoundException {
            return fakeDigest;
          }
        });
    testImageBuilder.addHistory(
        HistoryEntry.builder()
            .setCreationTimestamp(Instant.EPOCH)
            .setAuthor("Bazel")
            .setCreatedBy("bazel build ...")
            .setEmptyLayer(true)
            .build());
    testImageBuilder.addHistory(
        HistoryEntry.builder()
            .setCreationTimestamp(Instant.ofEpochSecond(20))
            .setAuthor("Jib")
            .setCreatedBy("jib")
            .build());
    imageToJsonTranslator = new ImageToJsonTranslator(testImageBuilder.build());
  }

  @Test
  void testGetContainerConfiguration() throws IOException, URISyntaxException, DigestException {
    setUp(V22ManifestTemplate.class);

    // Loads the expected JSON string.
    Path jsonFile = Paths.get(Resources.getResource("core/json/containerconfig.json").toURI());
    String expectedJson = new String(Files.readAllBytes(jsonFile), StandardCharsets.UTF_8);

    // Translates the image to the container configuration and writes the JSON string.
    JsonTemplate containerConfiguration = imageToJsonTranslator.getContainerConfiguration();

    Assert.assertEquals(expectedJson, JsonTemplateMapper.toUtf8String(containerConfiguration));
  }

  @Test
  void testGetManifest_v22() throws URISyntaxException, IOException, DigestException {
    setUp(V22ManifestTemplate.class);
    testGetManifest(V22ManifestTemplate.class, "core/json/translated_v22manifest.json");
  }

  @Test
  void testGetManifest_oci() throws URISyntaxException, IOException, DigestException {
    setUp(OciManifestTemplate.class);
    testGetManifest(OciManifestTemplate.class, "core/json/translated_ocimanifest.json");
  }

  @Test
  void testPortListToMap() {
    ImmutableSet<Port> input = ImmutableSet.of(Port.tcp(1000), Port.udp(2000));
    ImmutableSortedMap<String, Map<?, ?>> expected =
        ImmutableSortedMap.of("1000/tcp", ImmutableMap.of(), "2000/udp", ImmutableMap.of());
    Assert.assertEquals(expected, ImageToJsonTranslator.portSetToMap(input));
  }

  @Test
  void testVolumeListToMap() {
    ImmutableSet<AbsoluteUnixPath> input =
        ImmutableSet.of(
            AbsoluteUnixPath.get("/var/job-result-data"),
            AbsoluteUnixPath.get("/var/log/my-app-logs"));
    ImmutableSortedMap<String, Map<?, ?>> expected =
        ImmutableSortedMap.of(
            "/var/job-result-data", ImmutableMap.of(), "/var/log/my-app-logs", ImmutableMap.of());
    Assert.assertEquals(expected, ImageToJsonTranslator.volumesSetToMap(input));
  }

  @Test
  void testEnvironmentMapToList() {
    ImmutableMap<String, String> input = ImmutableMap.of("NAME1", "VALUE1", "NAME2", "VALUE2");
    ImmutableList<String> expected = ImmutableList.of("NAME1=VALUE1", "NAME2=VALUE2");
    Assert.assertEquals(expected, ImageToJsonTranslator.environmentMapToList(input));
  }

  /** Tests translation of image to {@link BuildableManifestTemplate}. */
  private <T extends BuildableManifestTemplate> void testGetManifest(
      Class<T> manifestTemplateClass, String translatedJsonFilename)
      throws URISyntaxException, IOException {
    // Loads the expected JSON string.
    Path jsonFile = Paths.get(Resources.getResource(translatedJsonFilename).toURI());
    String expectedJson = new String(Files.readAllBytes(jsonFile), StandardCharsets.UTF_8);

    // Translates the image to the manifest and writes the JSON string.
    JsonTemplate containerConfiguration = imageToJsonTranslator.getContainerConfiguration();
    BlobDescriptor blobDescriptor = Digests.computeDigest(containerConfiguration);
    T manifestTemplate =
        imageToJsonTranslator.getManifestTemplate(manifestTemplateClass, blobDescriptor);

    Assert.assertEquals(expectedJson, JsonTemplateMapper.toUtf8String(manifestTemplate));
  }
}
