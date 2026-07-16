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
import java.util.Collections;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link ManifestListGenerator}. */
public class ManifestListGeneratorTest {

  private ManifestListGenerator manifestListGenerator;

  @Before
  public void setUp() {}

  @Test
  public void testGetManifestListTemplate() throws IOException {

    // Expected Manifest List JSON
    //  {
    //  "schemaVersion":2,
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
    //   ]
    // }
    Image image1 =
        Image.builder(V22ManifestTemplate.class).setArchitecture("amd64").setOs("linux").build();
    Image image2 =
        Image.builder(V22ManifestTemplate.class).setArchitecture("arm64").setOs("windows").build();
    manifestListGenerator = new ManifestListGenerator(Arrays.asList(image1, image2));

    ManifestTemplate manifestTemplate =
        manifestListGenerator.getManifestListTemplate(V22ManifestTemplate.class);
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

  @Test
  public void testGetManifestListTemplate_ociIndex() throws IOException {

    // Expected Manifest List JSON
    //  {
    //  "schemaVersion":2,
    //  "mediaType":"application/vnd.oci.image.index.v1+json",
    //  "manifests":[
    //    {
    //      "mediaType":"application/vnd.oci.image.manifest.v1+json",
    //      "digest":"sha256:835e93ca9c952a5f811fecadbc6337c50415cce1ce4d7a4f9b6347ce4605c1fa",
    //      "size":248,
    //      "platform":{
    //        "architecture":"amd64",
    //        "os":"linux"
    //      }
    //    },
    //    {
    //      "mediaType":"application/vnd.oci.image.manifest.v1+json",
    //      "digest":"sha256:7ad84c70b22af31a7b0cc2218121d7e0a93f822374ccf0a634447921295c795d",
    //      "size":248,
    //      "platform":{
    //        "architecture":"arm64",
    //        "os":"windows"
    //      }
    //    }
    //   ]
    // }
    Image image1 =
        Image.builder(OciManifestTemplate.class).setArchitecture("amd64").setOs("linux").build();
    Image image2 =
        Image.builder(OciManifestTemplate.class).setArchitecture("arm64").setOs("windows").build();
    manifestListGenerator = new ManifestListGenerator(Arrays.asList(image1, image2));

    ManifestTemplate manifestTemplate =
        manifestListGenerator.getManifestListTemplate(OciManifestTemplate.class);
    Assert.assertTrue(manifestTemplate instanceof OciIndexTemplate);
    OciIndexTemplate manifestList = (OciIndexTemplate) manifestTemplate;
    Assert.assertEquals(2, manifestList.getSchemaVersion());
    Assert.assertEquals(
        Arrays.asList("sha256:835e93ca9c952a5f811fecadbc6337c50415cce1ce4d7a4f9b6347ce4605c1fa"),
        manifestList.getDigestsForPlatform("amd64", "linux"));
    Assert.assertEquals(
        Arrays.asList("sha256:7ad84c70b22af31a7b0cc2218121d7e0a93f822374ccf0a634447921295c795d"),
        manifestList.getDigestsForPlatform("arm64", "windows"));
  }

  @Test
  public void testGetManifestListTemplate_emptyImagesList() throws IOException {

    try {
      new ManifestListGenerator(Collections.emptyList())
          .getManifestListTemplate(V22ManifestTemplate.class);
      Assert.fail();
    } catch (IllegalStateException ex) {
      Assert.assertEquals("no images given", ex.getMessage());
    }
  }

  @Test
  public void testGetManifestListTemplate_unsupportedImageFormat() throws IOException {
    Image image1 =
        Image.builder(V22ManifestTemplate.class).setArchitecture("amd64").setOs("linux").build();
    Image image2 =
        Image.builder(V22ManifestTemplate.class).setArchitecture("arm64").setOs("windows").build();
    Class<? extends BuildableManifestTemplate> unknownFormat = BuildableManifestTemplate.class;
    try {
      new ManifestListGenerator(Arrays.asList(image1, image2))
          .getManifestListTemplate(unknownFormat);
      Assert.fail();
    } catch (IllegalArgumentException ex) {
      Assert.assertEquals(
          "Unsupported manifestTemplateClass format " + unknownFormat, ex.getMessage());
    }
  }
}
