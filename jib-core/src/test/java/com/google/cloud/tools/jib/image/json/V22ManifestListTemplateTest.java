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

import com.google.cloud.tools.jib.image.json.V22ManifestListTemplate.ManifestDescriptorTemplate;
import com.google.cloud.tools.jib.json.JsonTemplateMapper;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.DigestException;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

public class V22ManifestListTemplateTest {

  @Test
  public void testFromJson() throws IOException, URISyntaxException, DigestException {
    Path jsonFile = Paths.get(Resources.getResource("core/json/v22manifest_list.json").toURI());

    V22ManifestListTemplate manifestListJson =
        JsonTemplateMapper.readJsonFromFile(jsonFile, V22ManifestListTemplate.class);

    Assert.assertEquals(2, manifestListJson.getSchemaVersion());
    List<ManifestDescriptorTemplate> manifests = manifestListJson.getManifests();
    Assert.assertEquals(3, manifests.size());

    List<String> validPlatformPPC = manifestListJson.getDigestForPlatform("ppc64le", "linux");
    Assert.assertEquals(1, validPlatformPPC.size());
    Assert.assertEquals(
        "sha256:e692418e4cbaf90ca69d05a66403747baa33ee08806650b51fab815ad7fc331f",
        validPlatformPPC.get(0));

    List<String> validPlatformAMD = manifestListJson.getDigestForPlatform("amd64", "linux");
    Assert.assertEquals(2, validPlatformAMD.size());
    Assert.assertEquals(
        "sha256:5b0bcabd1ed22e9fb1310cf6c2dec7cdef19f0ad69efa1f392e94a4333501270",
        validPlatformAMD.get(0));
    Assert.assertEquals(
        "sha256:cccbcabd1ed22e9fb1310cf6c2dec7cdef19f0ad69efa1f392e94a4333501999",
        validPlatformAMD.get(1));

    List<String> invalidArch = manifestListJson.getDigestForPlatform("amd72", "linux");
    Assert.assertEquals(0, invalidArch.size());

    List<String> invalidOs = manifestListJson.getDigestForPlatform("amd64", "minix");
    Assert.assertEquals(0, invalidOs.size());
  }
}
