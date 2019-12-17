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
import org.junit.Test;

/** Tests for {@link OciIndexTemplate}. */
public class OciIndexTemplateTest {

  @Test
  public void testToJson() throws DigestException, IOException, URISyntaxException {
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
    Assert.assertEquals(expectedJson, JsonTemplateMapper.toUtf8String(ociIndexJson));
  }

  @Test
  public void testFromJson() throws IOException, URISyntaxException, DigestException {
    // Loads the JSON string.
    Path jsonFile = Paths.get(Resources.getResource("core/json/ociindex.json").toURI());

    // Deserializes into a manifest JSON object.
    OciIndexTemplate ociIndexJson =
        JsonTemplateMapper.readJsonFromFile(jsonFile, OciIndexTemplate.class);
    BuildableManifestTemplate.ContentDescriptorTemplate manifest =
        ociIndexJson.getManifests().get(0);

    Assert.assertEquals(
        DescriptorDigest.fromDigest(
            "sha256:8c662931926fa990b41da3c9f42663a537ccd498130030f9149173a0493832ad"),
        manifest.getDigest());
    Assert.assertEquals(
        "regis.try/repo:tag", manifest.getAnnotations().get("org.opencontainers.image.ref.name"));
    Assert.assertEquals(1000, manifest.getSize());
  }
}
