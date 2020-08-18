/*
 * Copyright 2020 Google LLC.
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

import com.google.cloud.tools.jib.image.Image;
import java.io.IOException;
import java.util.Arrays;
import org.junit.Assert;
import org.junit.Test;

/** Tests for {@link ManifestListGenerator}. */
public class ManifestListGeneratorTest {

  private Image image1;
  private Image image2;
  private ManifestListGenerator manifestListGenerator;

  private void setUp(Class<? extends BuildableManifestTemplate> imageFormat) {
    image1 = Image.builder(imageFormat).setArchitecture("amd64").setOs("linux").build();
    image2 = Image.builder(imageFormat).setArchitecture("arm64").setOs("windows").build();
    manifestListGenerator = new ManifestListGenerator(Arrays.asList(image1, image2));
  }

  @Test
  public void testGetManifest_v22() throws IOException {
    setUp(V22ManifestTemplate.class);
    testGetManifestListTemplate(V22ManifestTemplate.class);
  }

  /** Tests translation of image to {@link BuildableManifestTemplate}. */
  private <T extends BuildableManifestTemplate> void testGetManifestListTemplate(
      Class<T> manifestTemplateClass) throws IOException {

    // Expected Manifest List JSON
    //  {
    // "schemaVersion":2,
    //  "mediaType":"application/vnd.docker.distribution.manifest.list.v2+json",
    //  "manifests":[
    //    {
    //      "mediaType":"application/vnd.docker.distribution.manifest.v2+json",
    //      "digest":"sha256:1f25787aab4669d252bdae09a72b9c345d2a7b8c64c8dbfba4c82af4834dbccc",
    //      "size":264,
    //      "platform":{
    //        "architecture":"amd64",
    //        "os":"linux"
    //      }
    //    },
    //    {
    //      "mediaType":"application/vnd.docker.distribution.manifest.v2+json",
    //      "digest":"sha256:51038a7a91c0e8f747e05dd84c3b0393a7016ec312ce384fc945356778497ae3",
    //      "size":264,
    //      "platform":{
    //        "architecture":"arm64",
    //        "os":"windows"
    //      }
    //    }
    //  ]
    // }

    ManifestTemplate manifestTemplate =
        manifestListGenerator.getManifestListTemplate(manifestTemplateClass);
    Assert.assertTrue(manifestTemplate instanceof V22ManifestListTemplate);
    V22ManifestListTemplate manifestList = (V22ManifestListTemplate) manifestTemplate;
    Assert.assertEquals(2, manifestList.getSchemaVersion());
    Assert.assertEquals(
        Arrays.asList("sha256:1f25787aab4669d252bdae09a72b9c345d2a7b8c64c8dbfba4c82af4834dbccc"),
        manifestList.getDigestsForPlatform("amd64", "linux"));
    Assert.assertEquals(
        Arrays.asList("sha256:51038a7a91c0e8f747e05dd84c3b0393a7016ec312ce384fc945356778497ae3"),
        manifestList.getDigestsForPlatform("arm64", "windows"));
  }
}
