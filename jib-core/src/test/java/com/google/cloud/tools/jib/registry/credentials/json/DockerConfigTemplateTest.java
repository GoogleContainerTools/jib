/*
 * Copyright 2018 Google Inc.
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

package com.google.cloud.tools.jib.registry.credentials.json;

import com.google.cloud.tools.jib.json.JsonTemplateMapper;
import com.google.common.io.Resources;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Assert;
import org.junit.Test;

/** Tests for {@link DockerConfigTemplate}. */
public class DockerConfigTemplateTest {

  @Test
  public void test_toJson() throws URISyntaxException, IOException {
    // Loads the expected JSON string.
    Path jsonFile = Paths.get(Resources.getResource("json/dockerconfig.json").toURI());
    String expectedJson = new String(Files.readAllBytes(jsonFile), StandardCharsets.UTF_8);

    // Creates the JSON object to serialize.
    DockerConfigTemplate dockerConfigTemplate =
        new DockerConfigTemplate()
            .addAuth("some registry", "some auth")
            .addAuth("some other registry", "some other auth")
            .addAuth("just registry", null)
            .setCredsStore("some credential store")
            .addCredHelper("some registry", "some credential helper")
            .addCredHelper("another registry", "another credential helper");

    // Serializes the JSON object.
    ByteArrayOutputStream jsonStream = new ByteArrayOutputStream();
    JsonTemplateMapper.toBlob(dockerConfigTemplate).writeTo(jsonStream);

    Assert.assertEquals(expectedJson, jsonStream.toString());
  }

  @Test
  public void test_fromJson() throws URISyntaxException, IOException {
    // Loads the JSON string.
    Path jsonFile = Paths.get(Resources.getResource("json/dockerconfig.json").toURI());

    // Deserializes into a docker config JSON object.
    DockerConfigTemplate dockerConfigTemplate =
        JsonTemplateMapper.readJsonFromFile(jsonFile, DockerConfigTemplate.class);

    Assert.assertEquals("some auth", dockerConfigTemplate.getAuthFor("some registry"));
    Assert.assertEquals("some other auth", dockerConfigTemplate.getAuthFor("some other registry"));
    Assert.assertEquals(null, dockerConfigTemplate.getAuthFor("just registry"));

    Assert.assertEquals(
        "some credential store", dockerConfigTemplate.getCredentialHelperFor("some registry"));
    Assert.assertEquals(
        "some credential store",
        dockerConfigTemplate.getCredentialHelperFor("some other registry"));
    Assert.assertEquals(
        "some credential store", dockerConfigTemplate.getCredentialHelperFor("just registry"));
    Assert.assertEquals(
        "another credential helper",
        dockerConfigTemplate.getCredentialHelperFor("another registry"));
    Assert.assertEquals(null, dockerConfigTemplate.getCredentialHelperFor("unknonwn registry"));
  }
}
