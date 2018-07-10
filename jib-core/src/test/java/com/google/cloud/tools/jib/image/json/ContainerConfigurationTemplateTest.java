/*
 * Copyright 2017 Google LLC. All rights reserved.
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

import com.google.cloud.tools.jib.configuration.Port;
import com.google.cloud.tools.jib.configuration.Port.Protocol;
import com.google.cloud.tools.jib.image.DescriptorDigest;
import com.google.cloud.tools.jib.json.JsonTemplateMapper;
import com.google.common.io.Resources;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.DigestException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link ContainerConfigurationTemplate}. */
public class ContainerConfigurationTemplateTest {

  private List<Port> exposedPorts;

  @Before
  public void setup() {
    exposedPorts = new ArrayList<>();
    exposedPorts.add(new Port(1000, Protocol.TCP));
    exposedPorts.add(new Port(2000, Protocol.TCP));
    exposedPorts.add(new Port(3000, Protocol.UDP));
  }

  @Test
  public void testToJson() throws IOException, URISyntaxException, DigestException {
    // Loads the expected JSON string.
    Path jsonFile = Paths.get(Resources.getResource("json/containerconfig.json").toURI());
    String expectedJson = new String(Files.readAllBytes(jsonFile), StandardCharsets.UTF_8);

    // Creates the JSON object to serialize.
    ContainerConfigurationTemplate containerConfigJson = new ContainerConfigurationTemplate();

    containerConfigJson.setContainerEnvironment(Arrays.asList("VAR1=VAL1", "VAR2=VAL2"));
    containerConfigJson.setContainerEntrypoint(Arrays.asList("some", "entrypoint", "command"));
    containerConfigJson.setContainerCmd(Arrays.asList("arg1", "arg2"));
    containerConfigJson.setContainerExposedPorts(exposedPorts);

    containerConfigJson.addLayerDiffId(
        DescriptorDigest.fromDigest(
            "sha256:8c662931926fa990b41da3c9f42663a537ccd498130030f9149173a0493832ad"));

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

    Assert.assertEquals(
        Arrays.asList("VAR1=VAL1", "VAR2=VAL2"), containerConfigJson.getContainerEnvironment());

    Assert.assertEquals(
        Arrays.asList("some", "entrypoint", "command"),
        containerConfigJson.getContainerEntrypoint());

    Assert.assertEquals(Arrays.asList("arg1", "arg2"), containerConfigJson.getContainerCmd());

    Assert.assertEquals(
        DescriptorDigest.fromDigest(
            "sha256:8c662931926fa990b41da3c9f42663a537ccd498130030f9149173a0493832ad"),
        containerConfigJson.getLayerDiffId(0));
  }
}
