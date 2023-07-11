/*
 * Copyright 2019 Google LLC.
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
import com.google.cloud.tools.jib.blob.BlobDescriptor;
import com.google.cloud.tools.jib.json.JsonTemplateMapper;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.DigestException;
import org.junit.Assert;
import org.junit.jupiter.api.Test;

/** Tests for {@link OciIndexTemplate}. */
class OciIndexTemplateTest {

  @Test
  void testToJson() throws DigestException, IOException, URISyntaxException {
    // Loads the expected JSON string.
    Path jsonFile = Paths.get(Resources.getResource("core/json/ociindex.json").toURI());
    String expectedJson = new String(Files.readAllBytes(jsonFile), StandardCharsets.UTF_8);

    // Creates the JSON object to serialize.
    OciIndexTemplate ociIndexJson = new OciIndexTemplate();
    ociIndexJson.addManifest(
        new BlobDescriptor(
            1000,
            DescriptorDigest.fromDigest(
                "sha256:8c662931926fa990b41da3c9f42663a537ccd498130030f9149173a0493832ad")),
        "regis.try/repo:tag");

    // Serializes the JSON object.
    Assert.assertEquals(
        expectedJson.replaceAll("[\r\n\t ]", ""), JsonTemplateMapper.toUtf8String(ociIndexJson));
  }

  @Test
  void testFromJson() throws IOException, URISyntaxException, DigestException {
    // Loads the JSON string.
    Path jsonFile = Paths.get(Resources.getResource("core/json/ociindex.json").toURI());

    // Deserializes into a manifest JSON object.
    OciIndexTemplate ociIndexJson =
        JsonTemplateMapper.readJsonFromFile(jsonFile, OciIndexTemplate.class);
    BuildableManifestTemplate.ContentDescriptorTemplate manifest =
        ociIndexJson.getManifests().get(0);

    Assert.assertEquals(2, ociIndexJson.getSchemaVersion());
    Assert.assertEquals(OciIndexTemplate.MEDIA_TYPE, ociIndexJson.getManifestMediaType());
    Assert.assertEquals(
        DescriptorDigest.fromDigest(
            "sha256:8c662931926fa990b41da3c9f42663a537ccd498130030f9149173a0493832ad"),
        manifest.getDigest());
    Assert.assertEquals(
        "regis.try/repo:tag", manifest.getAnnotations().get("org.opencontainers.image.ref.name"));
    Assert.assertEquals(1000, manifest.getSize());
  }

  @Test
  void testToJsonWithPlatform() throws DigestException, IOException, URISyntaxException {
    // Loads the expected JSON string.
    Path jsonFile = Paths.get(Resources.getResource("core/json/ociindex_platforms.json").toURI());
    String expectedJson = new String(Files.readAllBytes(jsonFile), StandardCharsets.UTF_8);

    // Creates the JSON object to serialize.
    OciIndexTemplate ociIndexJson = new OciIndexTemplate();

    OciIndexTemplate.ManifestDescriptorTemplate ppc64leManifest =
        new OciIndexTemplate.ManifestDescriptorTemplate(
            OciManifestTemplate.MANIFEST_MEDIA_TYPE,
            7143,
            DescriptorDigest.fromDigest(
                "sha256:e692418e4cbaf90ca69d05a66403747baa33ee08806650b51fab815ad7fc331f"));
    ppc64leManifest.setPlatform("ppc64le", "linux");
    ociIndexJson.addManifest(ppc64leManifest);

    OciIndexTemplate.ManifestDescriptorTemplate amd64Manifest =
        new OciIndexTemplate.ManifestDescriptorTemplate(
            OciManifestTemplate.MANIFEST_MEDIA_TYPE,
            7682,
            DescriptorDigest.fromDigest(
                "sha256:5b0bcabd1ed22e9fb1310cf6c2dec7cdef19f0ad69efa1f392e94a4333501270"));
    amd64Manifest.setPlatform("amd64", "linux");
    ociIndexJson.addManifest(amd64Manifest);

    // Serializes the JSON object.
    Assert.assertEquals(
        expectedJson.replaceAll("[\r\n\t ]", ""), JsonTemplateMapper.toUtf8String(ociIndexJson));
  }

  @Test
  void testFromJsonWithPlatform() throws IOException, URISyntaxException, DigestException {
    // Loads the JSON string.
    Path jsonFile = Paths.get(Resources.getResource("core/json/ociindex_platforms.json").toURI());

    // Deserializes into a manifest JSON object.
    OciIndexTemplate ociIndexJson =
        JsonTemplateMapper.readJsonFromFile(jsonFile, OciIndexTemplate.class);

    Assert.assertEquals(2, ociIndexJson.getManifests().size());
    Assert.assertEquals(
        "ppc64le", ociIndexJson.getManifests().get(0).getPlatform().getArchitecture());
    Assert.assertEquals("linux", ociIndexJson.getManifests().get(0).getPlatform().getOs());
    Assert.assertEquals(
        "sha256:e692418e4cbaf90ca69d05a66403747baa33ee08806650b51fab815ad7fc331f",
        ociIndexJson.getDigestsForPlatform("ppc64le", "linux").get(0));
  }
}
