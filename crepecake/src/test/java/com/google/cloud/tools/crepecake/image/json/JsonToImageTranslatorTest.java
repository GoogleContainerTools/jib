/*
 * Copyright 2017 Google Inc.
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

package com.google.cloud.tools.crepecake.image.json;

import com.google.cloud.tools.crepecake.blob.BlobDescriptor;
import com.google.cloud.tools.crepecake.image.DescriptorDigest;
import com.google.cloud.tools.crepecake.image.DuplicateLayerException;
import com.google.cloud.tools.crepecake.image.Image;
import com.google.cloud.tools.crepecake.image.Layer;
import com.google.cloud.tools.crepecake.image.LayerCountMismatchException;
import com.google.cloud.tools.crepecake.image.LayerPropertyNotFoundException;
import com.google.cloud.tools.crepecake.json.JsonTemplateMapper;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.security.DigestException;
import java.util.Arrays;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

/** Tests for {@link JsonToImageTranslator}. */
public class JsonToImageTranslatorTest {

  @Test
  public void testToImage_v21()
      throws IOException, LayerPropertyNotFoundException, DuplicateLayerException, DigestException,
          URISyntaxException {
    // Loads the JSON string.
    File jsonFile =
        new File(getClass().getClassLoader().getResource("json/v21manifest.json").toURI());

    // Deserializes into a manifest JSON object.
    V21ManifestTemplate manifestTemplate =
        JsonTemplateMapper.readJsonFromFile(jsonFile, V21ManifestTemplate.class);

    Image image = JsonToImageTranslator.toImage(manifestTemplate);

    List<Layer> layers = image.getLayers();
    Assert.assertEquals(1, layers.size());
    Assert.assertEquals(
        DescriptorDigest.fromDigest(
            "sha256:8c662931926fa990b41da3c9f42663a537ccd498130030f9149173a0493832ad"),
        layers.get(0).getBlobDescriptor().getDigest());
  }

  @Test
  public void testToImage_v22()
      throws IOException, LayerPropertyNotFoundException, DuplicateLayerException,
          LayerCountMismatchException, DigestException, URISyntaxException {
    // Loads the container configuration JSON.
    File containerConfigurationJsonFile =
        new File(getClass().getClassLoader().getResource("json/containerconfig.json").toURI());
    ContainerConfigurationTemplate containerConfigurationTemplate =
        JsonTemplateMapper.readJsonFromFile(
            containerConfigurationJsonFile, ContainerConfigurationTemplate.class);

    // Loads the manifest JSON.
    File manifestJsonFile =
        new File(getClass().getClassLoader().getResource("json/v22manifest.json").toURI());
    V22ManifestTemplate manifestTemplate =
        JsonTemplateMapper.readJsonFromFile(manifestJsonFile, V22ManifestTemplate.class);

    Image image = JsonToImageTranslator.toImage(manifestTemplate, containerConfigurationTemplate);

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
  }
}
