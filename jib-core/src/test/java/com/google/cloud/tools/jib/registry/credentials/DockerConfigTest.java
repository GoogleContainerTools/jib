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

import com.google.api.client.util.Base64;
import com.google.cloud.tools.jib.json.JsonTemplateMapper;
import com.google.cloud.tools.jib.registry.credentials.json.DockerConfigTemplate;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Assert;
import org.junit.Test;

/** Tests for {@link DockerConfig}. */
public class DockerConfigTest {

  private static String decodeBase64(String base64String) {
    return new String(Base64.decodeBase64(base64String), StandardCharsets.UTF_8);
  }

  @Test
  public void test_fromJson() throws URISyntaxException, IOException {
    // Loads the JSON string.
    Path jsonFile = Paths.get(Resources.getResource("core/json/dockerconfig.json").toURI());

    // Deserializes into a docker config JSON object.
    DockerConfig dockerConfig =
        new DockerConfig(JsonTemplateMapper.readJsonFromFile(jsonFile, DockerConfigTemplate.class));

    Assert.assertEquals("some:auth", decodeBase64(dockerConfig.getAuthFor("some registry")));
    Assert.assertEquals(
        "some:other:auth", decodeBase64(dockerConfig.getAuthFor("some other registry")));
    Assert.assertEquals("token", decodeBase64(dockerConfig.getAuthFor("registry")));
    Assert.assertEquals("token", decodeBase64(dockerConfig.getAuthFor("https://registry")));
    Assert.assertNull(dockerConfig.getAuthFor("just registry"));

    Assert.assertEquals(
        Paths.get("docker-credential-some credential store"),
        dockerConfig.getCredentialHelperFor("some registry").getCredentialHelper());
    Assert.assertEquals(
        Paths.get("docker-credential-some credential store"),
        dockerConfig.getCredentialHelperFor("some other registry").getCredentialHelper());
    Assert.assertEquals(
        Paths.get("docker-credential-some credential store"),
        dockerConfig.getCredentialHelperFor("just registry").getCredentialHelper());
    Assert.assertEquals(
        Paths.get("docker-credential-some credential store"),
        dockerConfig.getCredentialHelperFor("with.protocol").getCredentialHelper());
    Assert.assertEquals(
        Paths.get("docker-credential-another credential helper"),
        dockerConfig.getCredentialHelperFor("another registry").getCredentialHelper());
    Assert.assertNull(dockerConfig.getCredentialHelperFor("unknonwn registry"));
  }

  @Test
  public void testGetAuthFor_orderOfMatchPreference() throws URISyntaxException, IOException {
    Path json =
        Paths.get(Resources.getResource("core/json/dockerconfig_extra_matches.json").toURI());

    DockerConfig dockerConfig =
        new DockerConfig(JsonTemplateMapper.readJsonFromFile(json, DockerConfigTemplate.class));

    Assert.assertEquals("my-registry: exact match", dockerConfig.getAuthFor("my-registry"));
    Assert.assertEquals("cool-registry: with https", dockerConfig.getAuthFor("cool-registry"));
    Assert.assertEquals(
        "awesome-registry: starting with name", dockerConfig.getAuthFor("awesome-registry"));
    Assert.assertEquals(
        "dull-registry: starting with name and with https",
        dockerConfig.getAuthFor("dull-registry"));
  }

  @Test
  public void testGetAuthFor_correctSuffixMatching() throws URISyntaxException, IOException {
    Path json =
        Paths.get(Resources.getResource("core/json/dockerconfig_extra_matches.json").toURI());

    DockerConfig dockerConfig =
        new DockerConfig(JsonTemplateMapper.readJsonFromFile(json, DockerConfigTemplate.class));

    Assert.assertNull(dockerConfig.getAuthFor("example"));
  }

  @Test
  public void testGetCredentialHelperFor() throws URISyntaxException, IOException {
    Path json = Paths.get(Resources.getResource("core/json/dockerconfig.json").toURI());

    DockerConfig dockerConfig =
        new DockerConfig(JsonTemplateMapper.readJsonFromFile(json, DockerConfigTemplate.class));

    Assert.assertEquals(
        Paths.get("docker-credential-some credential store"),
        dockerConfig.getCredentialHelperFor("just registry").getCredentialHelper());
  }

  @Test
  public void testGetCredentialHelperFor_withHttps() throws URISyntaxException, IOException {
    Path json = Paths.get(Resources.getResource("core/json/dockerconfig.json").toURI());

    DockerConfig dockerConfig =
        new DockerConfig(JsonTemplateMapper.readJsonFromFile(json, DockerConfigTemplate.class));

    Assert.assertEquals(
        Paths.get("docker-credential-some credential store"),
        dockerConfig.getCredentialHelperFor("with.protocol").getCredentialHelper());
  }

  @Test
  public void testGetCredentialHelperFor_withSuffix() throws URISyntaxException, IOException {
    Path json = Paths.get(Resources.getResource("core/json/dockerconfig.json").toURI());

    DockerConfig dockerConfig =
        new DockerConfig(JsonTemplateMapper.readJsonFromFile(json, DockerConfigTemplate.class));

    Assert.assertEquals(
        Paths.get("docker-credential-some credential store"),
        dockerConfig.getCredentialHelperFor("with.suffix").getCredentialHelper());
  }

  @Test
  public void testGetCredentialHelperFor_withProtocolAndSuffix()
      throws URISyntaxException, IOException {
    Path json = Paths.get(Resources.getResource("core/json/dockerconfig.json").toURI());

    DockerConfig dockerConfig =
        new DockerConfig(JsonTemplateMapper.readJsonFromFile(json, DockerConfigTemplate.class));

    Assert.assertEquals(
        Paths.get("docker-credential-some credential store"),
        dockerConfig.getCredentialHelperFor("with.protocol.and.suffix").getCredentialHelper());
  }

  @Test
  public void testGetCredentialHelperFor_correctSuffixMatching()
      throws URISyntaxException, IOException {
    Path json = Paths.get(Resources.getResource("core/json/dockerconfig.json").toURI());

    DockerConfig dockerConfig =
        new DockerConfig(JsonTemplateMapper.readJsonFromFile(json, DockerConfigTemplate.class));

    Assert.assertNull(dockerConfig.getCredentialHelperFor("example"));
    Assert.assertNull(dockerConfig.getCredentialHelperFor("another.example"));
  }
}
