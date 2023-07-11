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

package com.google.cloud.tools.jib.registry.credentials;

import com.google.cloud.tools.jib.json.JsonTemplateMapper;
import com.google.cloud.tools.jib.registry.credentials.json.DockerConfigTemplate;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import org.junit.Assert;
import org.junit.jupiter.api.Test;

/** Tests for {@link DockerConfig}. */
class DockerConfigTest {

  private static String decodeBase64(String base64String) {
    return new String(Base64.getDecoder().decode(base64String), StandardCharsets.UTF_8);
  }

  @Test
  void test_fromJson() throws URISyntaxException, IOException {
    // Loads the JSON string.
    Path jsonFile = Paths.get(Resources.getResource("core/json/dockerconfig.json").toURI());

    // Deserializes into a docker config JSON object.
    DockerConfig dockerConfig =
        new DockerConfig(JsonTemplateMapper.readJsonFromFile(jsonFile, DockerConfigTemplate.class));

    Assert.assertEquals(
        "some:auth", decodeBase64(dockerConfig.getAuthFor("some registry").getAuth()));
    Assert.assertEquals(
        "some:other:auth", decodeBase64(dockerConfig.getAuthFor("some other registry").getAuth()));
    Assert.assertEquals("token", decodeBase64(dockerConfig.getAuthFor("registry").getAuth()));
    Assert.assertEquals(
        "token", decodeBase64(dockerConfig.getAuthFor("https://registry").getAuth()));
    Assert.assertNull(dockerConfig.getAuthFor("just registry"));

    Assert.assertEquals(
        Paths.get("docker-credential-some credential helper"),
        dockerConfig.getCredentialHelperFor("some registry").getCredentialHelper());
    Assert.assertEquals(
        Paths.get("docker-credential-another credential helper"),
        dockerConfig.getCredentialHelperFor("another registry").getCredentialHelper());
    Assert.assertEquals(
        Paths.get("docker-credential-some credential store"),
        dockerConfig.getCredentialHelperFor("unknown registry").getCredentialHelper());
  }

  @Test
  void testGetAuthFor_orderOfMatchPreference() throws URISyntaxException, IOException {
    Path json =
        Paths.get(Resources.getResource("core/json/dockerconfig_extra_matches.json").toURI());

    DockerConfig dockerConfig =
        new DockerConfig(JsonTemplateMapper.readJsonFromFile(json, DockerConfigTemplate.class));

    Assert.assertEquals(
        "my-registry: exact match", dockerConfig.getAuthFor("my-registry").getAuth());
    Assert.assertEquals(
        "cool-registry: with https", dockerConfig.getAuthFor("cool-registry").getAuth());
    Assert.assertEquals(
        "awesome-registry: starting with name",
        dockerConfig.getAuthFor("awesome-registry").getAuth());
    Assert.assertEquals(
        "dull-registry: starting with name and with https",
        dockerConfig.getAuthFor("dull-registry").getAuth());
  }

  @Test
  void testGetAuthFor_correctSuffixMatching() throws URISyntaxException, IOException {
    Path json =
        Paths.get(Resources.getResource("core/json/dockerconfig_extra_matches.json").toURI());

    DockerConfig dockerConfig =
        new DockerConfig(JsonTemplateMapper.readJsonFromFile(json, DockerConfigTemplate.class));

    Assert.assertNull(dockerConfig.getAuthFor("example"));
  }

  @Test
  void testGetCredentialHelperFor_exactMatchInCredHelpers() throws URISyntaxException, IOException {
    Path json = Paths.get(Resources.getResource("core/json/dockerconfig.json").toURI());

    DockerConfig dockerConfig =
        new DockerConfig(JsonTemplateMapper.readJsonFromFile(json, DockerConfigTemplate.class));

    Assert.assertEquals(
        Paths.get("docker-credential-credHelper for just.registry.in.helpers"),
        dockerConfig.getCredentialHelperFor("just.registry.in.helpers").getCredentialHelper());
  }

  @Test
  void testGetCredentialHelperFor_withHttps() throws URISyntaxException, IOException {
    Path json = Paths.get(Resources.getResource("core/json/dockerconfig.json").toURI());

    DockerConfig dockerConfig =
        new DockerConfig(JsonTemplateMapper.readJsonFromFile(json, DockerConfigTemplate.class));

    Assert.assertEquals(
        Paths.get("docker-credential-credHelper for https__with.protocol.in.helpers"),
        dockerConfig.getCredentialHelperFor("with.protocol.in.helpers").getCredentialHelper());
  }

  @Test
  void testGetCredentialHelperFor_withSuffix() throws URISyntaxException, IOException {
    Path json = Paths.get(Resources.getResource("core/json/dockerconfig.json").toURI());

    DockerConfig dockerConfig =
        new DockerConfig(JsonTemplateMapper.readJsonFromFile(json, DockerConfigTemplate.class));

    Assert.assertEquals(
        Paths.get("docker-credential-credHelper for with.suffix.in.helpers/v2/"),
        dockerConfig.getCredentialHelperFor("with.suffix.in.helpers").getCredentialHelper());
  }

  @Test
  void testGetCredentialHelperFor_withProtocolAndSuffix() throws URISyntaxException, IOException {
    Path json = Paths.get(Resources.getResource("core/json/dockerconfig.json").toURI());

    DockerConfig dockerConfig =
        new DockerConfig(JsonTemplateMapper.readJsonFromFile(json, DockerConfigTemplate.class));

    Assert.assertEquals(
        Paths.get(
            "docker-credential-credHelper for https__with.protocol.and.suffix.in.helpers/suffix"),
        dockerConfig
            .getCredentialHelperFor("with.protocol.and.suffix.in.helpers")
            .getCredentialHelper());
  }

  @Test
  void testGetCredentialHelperFor_correctSuffixMatching() throws URISyntaxException, IOException {
    Path json = Paths.get(Resources.getResource("core/json/dockerconfig.json").toURI());

    DockerConfig dockerConfig =
        new DockerConfig(JsonTemplateMapper.readJsonFromFile(json, DockerConfigTemplate.class));

    // Should fall back to credsStore
    Assert.assertEquals(
        Paths.get("docker-credential-some credential store"),
        dockerConfig.getCredentialHelperFor("example").getCredentialHelper());
    Assert.assertEquals(
        Paths.get("docker-credential-some credential store"),
        dockerConfig.getCredentialHelperFor("another.example").getCredentialHelper());
  }
}
