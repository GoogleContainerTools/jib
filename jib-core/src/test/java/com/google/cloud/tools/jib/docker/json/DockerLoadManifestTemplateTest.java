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

import com.google.cloud.tools.jib.blob.Blobs;
import com.google.cloud.tools.jib.image.ImageReference;
import com.google.cloud.tools.jib.json.JsonTemplateMapper;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Assert;
import org.junit.Test;

/** Tests for {@link DockerLoadManifestTemplate}. */
public class DockerLoadManifestTemplateTest {

  @Test
  public void testToJson() throws URISyntaxException, IOException {
    // Loads the expected JSON string.
    Path jsonFile = Paths.get(Resources.getResource("json/loadmanifest.json").toURI());
    String expectedJson = new String(Files.readAllBytes(jsonFile), StandardCharsets.UTF_8);

    DockerLoadManifestTemplate template = new DockerLoadManifestTemplate();
    template.setRepoTags(
        ImageReference.of("testregistry", "testrepo", "testtag").toStringWithTag());
    template.addLayerFile("layer1.tar.gz");
    template.addLayerFile("layer2.tar.gz");
    template.addLayerFile("layer3.tar.gz");

    Assert.assertEquals(expectedJson, Blobs.writeToString(JsonTemplateMapper.toBlob(template)));
  }
}
