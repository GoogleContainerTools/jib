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

import com.google.cloud.tools.jib.image.DescriptorDigest;
import com.google.cloud.tools.jib.json.JsonTemplateMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.io.Resources;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.DigestException;
import java.time.Instant;
import java.util.Arrays;
import org.junit.Assert;
import org.junit.Test;

/** Tests for {@link ContainerConfigurationTemplate}. */
public class ContainerConfigurationTemplateTest {

  @Test
  public void testToJson() throws IOException, URISyntaxException, DigestException {
    // Loads the expected JSON string.
    Path jsonFile = Paths.get(Resources.getResource("json/containerconfig.json").toURI());
    String expectedJson = new String(Files.readAllBytes(jsonFile), StandardCharsets.UTF_8);

    // Creates the JSON object to serialize.
    ContainerConfigurationTemplate containerConfigJson = new ContainerConfigurationTemplate();

    containerConfigJson.setCreated("1970-01-01T00:00:20Z");
    containerConfigJson.setContainerEnvironment(Arrays.asList("VAR1=VAL1", "VAR2=VAL2"));
    containerConfigJson.setContainerEntrypoint(Arrays.asList("some", "entrypoint", "command"));
    containerConfigJson.setContainerCmd(Arrays.asList("arg1", "arg2"));
    containerConfigJson.setContainerExposedPorts(
        ImmutableSortedMap.of(
            "1000/tcp",
            ImmutableMap.of(),
            "2000/tcp",
            ImmutableMap.of(),
            "3000/udp",
            ImmutableMap.of()));
    containerConfigJson.setContainerLabels(ImmutableMap.of("key1", "value1", "key2", "value2"));
    containerConfigJson.setContainerWorkingDir("/some/workspace");

    containerConfigJson.addLayerDiffId(
        DescriptorDigest.fromDigest(
            "sha256:8c662931926fa990b41da3c9f42663a537ccd498130030f9149173a0493832ad"));
    containerConfigJson.addHistoryEntry(
        HistoryEntry.builder()
            .setCreationTimestamp(Instant.EPOCH)
            .setAuthor("Bazel")
            .setCreatedBy("bazel build ...")
            .setEmptyLayer(true)
            .build());
    containerConfigJson.addHistoryEntry(
        HistoryEntry.builder()
            .setCreationTimestamp(Instant.ofEpochSecond(20))
            .setAuthor("Jib")
            .setCreatedBy("jib")
            .build());

    // Serializes the JSON object.
    ByteArrayOutputStream jsonStream = new ByteArrayOutputStream();
    JsonTemplateMapper.toBlob(containerConfigJson).writeTo(jsonStream);

    Assert.assertEquals(expectedJson, jsonStream.toString());
  }

  @Test
  public void testFromJson() throws IOException, URISyntaxException, DigestException {
    // Loads the JSON string.
    Path jsonFile = Paths.get(Resources.getResource("json/containerconfig.json").toURI());

    // Deserializes into a manifest JSON object.
    ContainerConfigurationTemplate containerConfigJson =
        JsonTemplateMapper.readJsonFromFile(jsonFile, ContainerConfigurationTemplate.class);

    Assert.assertEquals("1970-01-01T00:00:20Z", containerConfigJson.getCreated());
    Assert.assertEquals(
        Arrays.asList("VAR1=VAL1", "VAR2=VAL2"), containerConfigJson.getContainerEnvironment());
    Assert.assertEquals(
        Arrays.asList("some", "entrypoint", "command"),
        containerConfigJson.getContainerEntrypoint());
    Assert.assertEquals(Arrays.asList("arg1", "arg2"), containerConfigJson.getContainerCmd());
    Assert.assertEquals(
        ImmutableMap.of("key1", "value1", "key2", "value2"),
        containerConfigJson.getContainerLabels());
    Assert.assertEquals("/some/workspace", containerConfigJson.getContainerWorkingDir());
    Assert.assertEquals(
        DescriptorDigest.fromDigest(
            "sha256:8c662931926fa990b41da3c9f42663a537ccd498130030f9149173a0493832ad"),
        containerConfigJson.getLayerDiffId(0));
    Assert.assertEquals(
        ImmutableList.of(
            HistoryEntry.builder()
                .setCreationTimestamp(Instant.EPOCH)
                .setAuthor("Bazel")
                .setCreatedBy("bazel build ...")
                .setEmptyLayer(true)
                .build(),
            HistoryEntry.builder()
                .setCreationTimestamp(Instant.ofEpochSecond(20))
                .setAuthor("Jib")
                .setCreatedBy("jib")
                .build()),
        containerConfigJson.getHistory());
  }
}
