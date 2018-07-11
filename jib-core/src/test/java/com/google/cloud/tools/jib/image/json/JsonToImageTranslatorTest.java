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

import com.google.cloud.tools.jib.blob.BlobDescriptor;
import com.google.cloud.tools.jib.configuration.Port;
import com.google.cloud.tools.jib.configuration.Port.Protocol;
import com.google.cloud.tools.jib.image.DescriptorDigest;
import com.google.cloud.tools.jib.image.Image;
import com.google.cloud.tools.jib.image.Layer;
import com.google.cloud.tools.jib.image.LayerCountMismatchException;
import com.google.cloud.tools.jib.image.LayerPropertyNotFoundException;
import com.google.cloud.tools.jib.json.JsonTemplateMapper;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.DigestException;
import java.util.Arrays;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

/** Tests for {@link JsonToImageTranslator}. */
public class JsonToImageTranslatorTest {

  @Test
  public void testToImage_v21()
      throws IOException, LayerPropertyNotFoundException, DigestException, URISyntaxException {
    // Loads the JSON string.
    Path jsonFile =
        Paths.get(getClass().getClassLoader().getResource("json/v21manifest.json").toURI());

    // Deserializes into a manifest JSON object.
    V21ManifestTemplate manifestTemplate =
        JsonTemplateMapper.readJsonFromFile(jsonFile, V21ManifestTemplate.class);

    Image<Layer> image = JsonToImageTranslator.toImage(manifestTemplate);

    List<Layer> layers = image.getLayers();
    Assert.assertEquals(1, layers.size());
    Assert.assertEquals(
        DescriptorDigest.fromDigest(
            "sha256:8c662931926fa990b41da3c9f42663a537ccd498130030f9149173a0493832ad"),
        layers.get(0).getBlobDescriptor().getDigest());
  }

  @Test
  public void testToImage_v22()
      throws IOException, LayerPropertyNotFoundException, LayerCountMismatchException,
          DigestException, URISyntaxException {
    testToImage_buildable("json/v22manifest.json", V22ManifestTemplate.class);
  }

  @Test
  public void testToImage_oci()
      throws IOException, LayerPropertyNotFoundException, LayerCountMismatchException,
          DigestException, URISyntaxException {
    testToImage_buildable("json/ocimanifest.json", OCIManifestTemplate.class);
  }

  private <T extends BuildableManifestTemplate> void testToImage_buildable(
      String jsonFilename, Class<T> manifestTemplateClass)
      throws IOException, LayerPropertyNotFoundException, LayerCountMismatchException,
          DigestException, URISyntaxException {
    // Loads the container configuration JSON.
    Path containerConfigurationJsonFile =
        Paths.get(getClass().getClassLoader().getResource("json/containerconfig.json").toURI());
    ContainerConfigurationTemplate containerConfigurationTemplate =
        JsonTemplateMapper.readJsonFromFile(
            containerConfigurationJsonFile, ContainerConfigurationTemplate.class);

    // Loads the manifest JSON.
    Path manifestJsonFile =
        Paths.get(getClass().getClassLoader().getResource(jsonFilename).toURI());
    T manifestTemplate =
        JsonTemplateMapper.readJsonFromFile(manifestJsonFile, manifestTemplateClass);

    Image<Layer> image =
        JsonToImageTranslator.toImage(manifestTemplate, containerConfigurationTemplate);

    List<Layer> layers = image.getLayers();
    Assert.assertEquals(1, layers.size());
    Assert.assertEquals(
        new BlobDescriptor(
            1000000,
            DescriptorDigest.fromDigest(
                "sha256:4945ba5011739b0b98c4a41afe224e417f47c7c99b2ce76830999c9a0861b236")),
        layers.get(0).getBlobDescriptor());
    Assert.assertEquals(
        DescriptorDigest.fromDigest(
            "sha256:8c662931926fa990b41da3c9f42663a537ccd498130030f9149173a0493832ad"),
        layers.get(0).getDiffId());
    Assert.assertEquals(Arrays.asList("some", "entrypoint", "command"), image.getEntrypoint());
    Assert.assertEquals(Arrays.asList("VAR1=VAL1", "VAR2=VAL2"), image.getEnvironment());
    Assert.assertEquals(
        ImmutableList.of(
            new Port(1000, Protocol.TCP),
            new Port(2000, Protocol.TCP),
            new Port(3000, Protocol.UDP)),
        image.getExposedPorts());
  }
}
