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

import com.google.cloud.tools.jib.blob.BlobDescriptor;
import com.google.cloud.tools.jib.configuration.Port;
import com.google.cloud.tools.jib.image.DescriptorDigest;
import com.google.cloud.tools.jib.image.Image;
import com.google.cloud.tools.jib.image.Layer;
import com.google.cloud.tools.jib.image.LayerCountMismatchException;
import com.google.cloud.tools.jib.image.LayerPropertyNotFoundException;
import com.google.cloud.tools.jib.json.JsonTemplateMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedMap;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.DigestException;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
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
          DigestException, URISyntaxException, BadContainerConfigurationFormatException {
    testToImage_buildable("json/v22manifest.json", V22ManifestTemplate.class);
  }

  @Test
  public void testToImage_oci()
      throws IOException, LayerPropertyNotFoundException, LayerCountMismatchException,
          DigestException, URISyntaxException, BadContainerConfigurationFormatException {
    testToImage_buildable("json/ocimanifest.json", OCIManifestTemplate.class);
  }

  @Test
  public void testPortMapToList() throws BadContainerConfigurationFormatException {
    ImmutableSortedMap<String, Map<?, ?>> input =
        ImmutableSortedMap.of(
            "1000",
            ImmutableMap.of(),
            "2000/tcp",
            ImmutableMap.of(),
            "3000/udp",
            ImmutableMap.of());
    ImmutableList<Port> expected = ImmutableList.of(Port.tcp(1000), Port.tcp(2000), Port.udp(3000));
    Assert.assertEquals(expected, JsonToImageTranslator.portMapToList(input));

    ImmutableList<Map<String, Map<?, ?>>> badInputs =
        ImmutableList.of(
            ImmutableMap.of("abc", ImmutableMap.of()),
            ImmutableMap.of("1000-2000", ImmutableMap.of()),
            ImmutableMap.of("/udp", ImmutableMap.of()),
            ImmutableMap.of("123/xxx", ImmutableMap.of()));
    for (Map<String, Map<?, ?>> badInput : badInputs) {
      try {
        JsonToImageTranslator.portMapToList(badInput);
        Assert.fail();
      } catch (BadContainerConfigurationFormatException ignored) {
      }
    }
  }

  @Test
  public void testJsonToImageTranslatorRegex() {
    assertGoodEnvironmentPattern("NAME=VALUE", "NAME", "VALUE");
    assertGoodEnvironmentPattern("A1203921=www=ww", "A1203921", "www=ww");
    assertGoodEnvironmentPattern("&*%(&#$(*@(%&@$*$(=", "&*%(&#$(*@(%&@$*$(", "");
    assertGoodEnvironmentPattern("m_a_8943=100", "m_a_8943", "100");
    assertGoodEnvironmentPattern("A_B_C_D=*****", "A_B_C_D", "*****");

    assertBadEnvironmentPattern("=================");
    assertBadEnvironmentPattern("A_B_C");
  }

  private void assertGoodEnvironmentPattern(
      String input, String expectedName, String expectedValue) {
    Matcher matcher = JsonToImageTranslator.ENVIRONMENT_PATTERN.matcher(input);
    Assert.assertTrue(matcher.matches());
    Assert.assertEquals(expectedName, matcher.group("name"));
    Assert.assertEquals(expectedValue, matcher.group("value"));
  }

  private void assertBadEnvironmentPattern(String input) {
    Matcher matcher = JsonToImageTranslator.ENVIRONMENT_PATTERN.matcher(input);
    Assert.assertFalse(matcher.matches());
  }

  private <T extends BuildableManifestTemplate> void testToImage_buildable(
      String jsonFilename, Class<T> manifestTemplateClass)
      throws IOException, LayerPropertyNotFoundException, LayerCountMismatchException,
          DigestException, URISyntaxException, BadContainerConfigurationFormatException {
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
        image.getHistory());
    Assert.assertEquals(Instant.ofEpochSecond(20), image.getCreated());
    Assert.assertEquals(Arrays.asList("some", "entrypoint", "command"), image.getEntrypoint());
    Assert.assertEquals(ImmutableMap.of("VAR1", "VAL1", "VAR2", "VAL2"), image.getEnvironment());
    Assert.assertEquals("/some/workspace", image.getWorkingDirectory());
    Assert.assertEquals(
        ImmutableList.of(Port.tcp(1000), Port.tcp(2000), Port.udp(3000)), image.getExposedPorts());
  }
}
