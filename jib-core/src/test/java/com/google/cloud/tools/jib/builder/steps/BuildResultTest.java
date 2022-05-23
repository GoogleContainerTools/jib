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

package com.google.cloud.tools.jib.builder.steps;

import com.google.cloud.tools.jib.api.DescriptorDigest;
import com.google.cloud.tools.jib.image.Image;
import com.google.cloud.tools.jib.image.json.V22ManifestTemplate;
import java.io.IOException;
import java.security.DigestException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link BuildResult}. */
public class BuildResultTest {

  private DescriptorDigest digest1;
  private DescriptorDigest digest2;
  private DescriptorDigest id;

  @Before
  public void setUp() throws DigestException {
    digest1 =
        DescriptorDigest.fromDigest(
            "sha256:abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789");
    digest2 =
        DescriptorDigest.fromDigest(
            "sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
    id =
        DescriptorDigest.fromDigest(
            "sha256:9876543210fedcba9876543210fedcba9876543210fedcba9876543210fedcba");
  }

  @Test
  public void testCreated() {
    BuildResult container = new BuildResult(digest1, id, true);
    Assert.assertEquals(digest1, container.getImageDigest());
    Assert.assertEquals(id, container.getImageId());
    Assert.assertTrue(container.isImagePushed());
  }

  @Test
  public void testEquality() {
    BuildResult container1 = new BuildResult(digest1, id, true);
    BuildResult container2 = new BuildResult(digest1, id, true);
    BuildResult container3 = new BuildResult(digest2, id, true);
    BuildResult container4 = new BuildResult(digest1, id, false);

    Assert.assertEquals(container1, container2);
    Assert.assertEquals(container1.hashCode(), container2.hashCode());
    Assert.assertEquals(container1.hashCode(), container4.hashCode());
    Assert.assertNotEquals(container1, container3);
  }

  @Test
  public void testFromImage() throws IOException {
    Image image1 = Image.builder(V22ManifestTemplate.class).setUser("user").build();
    Image image2 = Image.builder(V22ManifestTemplate.class).setUser("user").build();
    Image image3 = Image.builder(V22ManifestTemplate.class).setUser("anotherUser").build();
    Assert.assertEquals(
        BuildResult.fromImage(image1, V22ManifestTemplate.class),
        BuildResult.fromImage(image2, V22ManifestTemplate.class));
    Assert.assertNotEquals(
        BuildResult.fromImage(image1, V22ManifestTemplate.class),
        BuildResult.fromImage(image3, V22ManifestTemplate.class));
  }
}
