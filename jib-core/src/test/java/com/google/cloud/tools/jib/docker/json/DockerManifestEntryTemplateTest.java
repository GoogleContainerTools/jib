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

package com.google.cloud.tools.jib.docker.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.tools.jib.api.ImageReference;
import com.google.cloud.tools.jib.json.JsonTemplateMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

/** Tests for {@link DockerManifestEntryTemplate}. */
public class DockerManifestEntryTemplateTest {

  @Test
  public void testToJson() throws URISyntaxException, IOException {
    // Loads the expected JSON string.
    Path jsonFile = Paths.get(Resources.getResource("core/json/loadmanifest.json").toURI());
    String expectedJson = new String(Files.readAllBytes(jsonFile), StandardCharsets.UTF_8);

    DockerManifestEntryTemplate template = new DockerManifestEntryTemplate();
    template.addRepoTag(
        ImageReference.of("testregistry", "testrepo", "testtag").toStringWithQualifier());
    template.addLayerFile("layer1.tar.gz");
    template.addLayerFile("layer2.tar.gz");
    template.addLayerFile("layer3.tar.gz");

    List<DockerManifestEntryTemplate> loadManifest = Collections.singletonList(template);
    Assert.assertEquals(expectedJson, JsonTemplateMapper.toUtf8String(loadManifest));
  }

  @Test
  public void testFromJson() throws URISyntaxException, IOException {
    // Loads the expected JSON string.
    Path jsonFile = Paths.get(Resources.getResource("core/json/loadmanifest2.json").toURI());
    String sourceJson = new String(Files.readAllBytes(jsonFile), StandardCharsets.UTF_8);
    DockerManifestEntryTemplate template =
        new ObjectMapper().readValue(sourceJson, DockerManifestEntryTemplate[].class)[0];

    Assert.assertEquals(
        ImmutableList.of("layer1.tar.gz", "layer2.tar.gz", "layer3.tar.gz"),
        template.getLayerFiles());
    Assert.assertEquals("config.json", template.getConfig());
  }
}
