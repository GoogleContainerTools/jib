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
import com.google.cloud.tools.jib.json.JsonTemplateMapper;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.DigestException;
import java.util.Map;
import org.junit.Assert;
import org.junit.Test;

/** Tests for {@link OciManifestTemplate}. */
public class OciManifestTemplateTest {

  @Test
  public void testToJson() throws DigestException, IOException, URISyntaxException {
    // Loads the expected JSON string.
    Path jsonFile = Paths.get(Resources.getResource("core/json/ocimanifest.json").toURI());
    String expectedJson = new String(Files.readAllBytes(jsonFile), StandardCharsets.UTF_8);

    // Creates the JSON object to serialize.
    OciManifestTemplate manifestJson = new OciManifestTemplate();

    manifestJson.setContainerConfiguration(
        1000,
        DescriptorDigest.fromDigest(
            "sha256:8c662931926fa990b41da3c9f42663a537ccd498130030f9149173a0493832ad"));

    manifestJson.addLayer(
        1000_000,
        DescriptorDigest.fromHash(
            "4945ba5011739b0b98c4a41afe224e417f47c7c99b2ce76830999c9a0861b236"));

    // Serializes the JSON object.
    Assert.assertEquals(expectedJson, JsonTemplateMapper.toUtf8String(manifestJson));
  }

  @Test
  public void testFromJson() throws IOException, URISyntaxException, DigestException {
    // Loads the JSON string.
    Path jsonFile = Paths.get(Resources.getResource("core/json/ocimanifest.json").toURI());

    // Deserializes into a manifest JSON object.
    OciManifestTemplate manifestJson =
        JsonTemplateMapper.readJsonFromFile(jsonFile, OciManifestTemplate.class);

    Assert.assertEquals(
        DescriptorDigest.fromDigest(
            "sha256:8c662931926fa990b41da3c9f42663a537ccd498130030f9149173a0493832ad"),
        manifestJson.getContainerConfiguration().getDigest());

    Assert.assertEquals(1000, manifestJson.getContainerConfiguration().getSize());

    Assert.assertEquals(
        DescriptorDigest.fromHash(
            "4945ba5011739b0b98c4a41afe224e417f47c7c99b2ce76830999c9a0861b236"),
        manifestJson.getLayers().get(0).getDigest());

    Assert.assertEquals(1000_000, manifestJson.getLayers().get(0).getSize());
  }

  @Test
  public void testAnnotations_addAndGet() {
    OciManifestTemplate manifest = new OciManifestTemplate();
    Assert.assertNull(manifest.getAnnotations());

    manifest.addAnnotation("org.opencontainers.image.base.name", "alpine:3.18");
    manifest.addAnnotation(
        "org.opencontainers.image.base.digest",
        "sha256:8c662931926fa990b41da3c9f42663a537ccd498130030f9149173a0493832ad");

    Map<String, String> annotations = manifest.getAnnotations();
    Assert.assertNotNull(annotations);
    Assert.assertEquals(2, annotations.size());
    Assert.assertEquals("alpine:3.18", annotations.get("org.opencontainers.image.base.name"));
    Assert.assertEquals(
        "sha256:8c662931926fa990b41da3c9f42663a537ccd498130030f9149173a0493832ad",
        annotations.get("org.opencontainers.image.base.digest"));
  }

  @Test
  public void testAnnotations_serialization() throws DigestException, IOException {
    OciManifestTemplate manifest = new OciManifestTemplate();
    manifest.setContainerConfiguration(
        1000,
        DescriptorDigest.fromDigest(
            "sha256:8c662931926fa990b41da3c9f42663a537ccd498130030f9149173a0493832ad"));
    manifest.addLayer(
        1000_000,
        DescriptorDigest.fromHash(
            "4945ba5011739b0b98c4a41afe224e417f47c7c99b2ce76830999c9a0861b236"));
    manifest.addAnnotation("org.opencontainers.image.base.name", "alpine:3.18");
    manifest.addAnnotation(
        "org.opencontainers.image.base.digest",
        "sha256:abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789");

    String json = JsonTemplateMapper.toUtf8String(manifest);
    Assert.assertTrue(json.contains("\"annotations\""));
    Assert.assertTrue(json.contains("\"org.opencontainers.image.base.name\":\"alpine:3.18\""));
    Assert.assertTrue(
        json.contains(
            "\"org.opencontainers.image.base.digest\":"
                + "\"sha256:abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789\""));
  }

  @Test
  public void testAnnotations_deserialization() throws IOException {
    String json =
        "{\"schemaVersion\":2,"
            + "\"mediaType\":\"application/vnd.oci.image.manifest.v1+json\","
            + "\"config\":{\"mediaType\":\"application/vnd.oci.image.config.v1+json\","
            + "\"digest\":\"sha256:8c662931926fa990b41da3c9f42663a537ccd498130030f9149173a0493832ad\","
            + "\"size\":1000},"
            + "\"layers\":[],"
            + "\"annotations\":{"
            + "\"org.opencontainers.image.base.name\":\"alpine:3.18\","
            + "\"org.opencontainers.image.base.digest\":\"sha256:abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789\"}}";

    OciManifestTemplate manifest = JsonTemplateMapper.readJson(json, OciManifestTemplate.class);

    Map<String, String> annotations = manifest.getAnnotations();
    Assert.assertNotNull(annotations);
    Assert.assertEquals("alpine:3.18", annotations.get("org.opencontainers.image.base.name"));
    Assert.assertEquals(
        "sha256:abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789",
        annotations.get("org.opencontainers.image.base.digest"));
  }

  @Test
  public void testAnnotations_getReturnsImmutableCopy() {
    OciManifestTemplate manifest = new OciManifestTemplate();
    manifest.addAnnotation("key", "value");

    Map<String, String> annotations = manifest.getAnnotations();
    try {
      annotations.put("another", "value");
      Assert.fail("Expected UnsupportedOperationException");
    } catch (UnsupportedOperationException expected) {
      // expected
    }
  }
}
