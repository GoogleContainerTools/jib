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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.DigestException;
import java.util.Arrays;
import org.junit.Assert;
import org.junit.jupiter.api.Test;

/** Tests for {@link V21ManifestTemplate}. */
class V21ManifestTemplateTest {

  @Test
  void testFromJson() throws URISyntaxException, IOException, DigestException {
    // Loads the JSON string.
    Path jsonFile = Paths.get(Resources.getResource("core/json/v21manifest.json").toURI());

    // Deserializes into a manifest JSON object.
    V21ManifestTemplate manifestJson =
        JsonTemplateMapper.readJsonFromFile(jsonFile, V21ManifestTemplate.class);

    Assert.assertEquals(
        DescriptorDigest.fromDigest(
            "sha256:8c662931926fa990b41da3c9f42663a537ccd498130030f9149173a0493832ad"),
        manifestJson.getFsLayers().get(0).getDigest());

    ContainerConfigurationTemplate containerConfiguration =
        manifestJson.getContainerConfiguration().orElse(null);
    Assert.assertEquals(
        Arrays.asList("JAVA_HOME=/opt/openjdk", "PATH=/opt/openjdk/bin"),
        containerConfiguration.getContainerEnvironment());
    Assert.assertEquals(
        Arrays.asList("/opt/openjdk/bin/java"), containerConfiguration.getContainerEntrypoint());
  }
}
