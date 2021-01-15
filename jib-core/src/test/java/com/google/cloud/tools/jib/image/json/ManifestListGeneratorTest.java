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

  private Image image1;
  private Image image2;
  private ManifestListGenerator manifestListGenerator;

  @Before
  public void setUp() {
    image1 =
        Image.builder(V22ManifestTemplate.class).setArchitecture("amd64").setOs("linux").build();
    image2 =
        Image.builder(V22ManifestTemplate.class).setArchitecture("arm64").setOs("windows").build();
    manifestListGenerator = new ManifestListGenerator(Arrays.asList(image1, image2));
  }

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
    try {
      new ManifestListGenerator(Arrays.asList(image1, image2))
          .getManifestListTemplate(OciManifestTemplate.class);
      Assert.fail();
    } catch (IllegalArgumentException ex) {
      Assert.assertEquals("Build an OCI image index is not yet supported", ex.getMessage());
    }
  }
}
