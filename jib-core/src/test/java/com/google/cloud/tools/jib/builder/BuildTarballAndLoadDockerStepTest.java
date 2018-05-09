/*
 * Copyright 2018 Google LLC. All Rights Reserved.
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

package com.google.cloud.tools.jib.builder;

import com.google.cloud.tools.jib.blob.Blob;
import com.google.cloud.tools.jib.blob.Blobs;
import com.google.cloud.tools.jib.image.ImageReference;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

/** Tests for {@link BuildTarballAndLoadDockerStep}. */
public class BuildTarballAndLoadDockerStepTest {

  @Test
  public void testGetDockerLoadManifest() throws URISyntaxException, IOException {
    // Loads the expected JSON string.
    Path jsonFile = Paths.get(Resources.getResource("json/loadmanifest.json").toURI());
    String expectedJson = new String(Files.readAllBytes(jsonFile), StandardCharsets.UTF_8);

    List<String> layers = Arrays.asList("layer1.tar.gz", "layer2.tar.gz", "layer3.tar.gz");
    ImageReference testImage = ImageReference.of("testregistry", "testrepo", "testtag");
    Blob manifestBlob = BuildTarballAndLoadDockerStep.getManifestBlob(testImage, layers);
    Assert.assertEquals(expectedJson, Blobs.writeToString(manifestBlob));
  }

  @Test
  public void testGetDockerLoadManifest_noTag() throws IOException, URISyntaxException {
    // Loads the expected JSON string.
    Path jsonFile = Paths.get(Resources.getResource("json/loadmanifest_notag.json").toURI());
    String expectedJson = new String(Files.readAllBytes(jsonFile), StandardCharsets.UTF_8);

    List<String> layers = Arrays.asList("layer1.tar.gz", "layer2.tar.gz", "layer3.tar.gz");
    ImageReference testImage = ImageReference.of(null, "testrepo", null);
    Blob manifestBlob = BuildTarballAndLoadDockerStep.getManifestBlob(testImage, layers);
    Assert.assertEquals(expectedJson, Blobs.writeToString(manifestBlob));
  }
}
