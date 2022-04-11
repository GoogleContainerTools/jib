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

package com.google.cloud.tools.jib.api;

import com.google.common.collect.ImmutableSet;
import java.security.DigestException;
import java.util.Set;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Tests for {@link JibContainer}. */
public class JibContainerTest {

  @Rule public TemporaryFolder temporaryDirectory = new TemporaryFolder();

  private ImageReference targetImage1;
  private ImageReference targetImage2;
  private DescriptorDigest digest1;
  private DescriptorDigest digest2;
  private Set<String> tags1;
  private Set<String> tags2;

  @Before
  public void setUp() throws DigestException, InvalidImageReferenceException {
    targetImage1 = ImageReference.parse("gcr.io/project/image:tag");
    targetImage2 = ImageReference.parse("gcr.io/project/image:tag2");
    digest1 =
        DescriptorDigest.fromDigest(
            "sha256:abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789");
    digest2 =
        DescriptorDigest.fromDigest(
            "sha256:9876543210fedcba9876543210fedcba9876543210fedcba9876543210fedcba");
    tags1 = ImmutableSet.of("latest", "custom-tag");
    tags2 = ImmutableSet.of("latest");
  }

  @Test
  public void testCreation() {
    JibContainer container = new JibContainer(targetImage1, digest1, digest2, tags1, true);

    Assert.assertEquals(targetImage1, container.getTargetImage());
    Assert.assertEquals(digest1, container.getDigest());
    Assert.assertEquals(digest2, container.getImageId());
    Assert.assertEquals(tags1, container.getTags());
    Assert.assertEquals(true, container.isImagePushed());
  }

  @Test
  public void testEquality() {
    JibContainer container1 = new JibContainer(targetImage1, digest1, digest2, tags1, true);
    JibContainer container2 = new JibContainer(targetImage1, digest1, digest2, tags1, true);

    Assert.assertEquals(container1, container2);
    Assert.assertEquals(container1.hashCode(), container2.hashCode());
  }

  @Test
  public void testEquality_differentTargetImage() {
    JibContainer container1 = new JibContainer(targetImage1, digest1, digest2, tags1, true);
    JibContainer container2 = new JibContainer(targetImage2, digest1, digest2, tags1, true);

    Assert.assertNotEquals(container1, container2);
    Assert.assertNotEquals(container1.hashCode(), container2.hashCode());
  }

  @Test
  public void testEquality_differentImageDigest() {
    JibContainer container1 = new JibContainer(targetImage1, digest1, digest2, tags1, true);
    JibContainer container2 = new JibContainer(targetImage1, digest2, digest2, tags1, true);

    Assert.assertNotEquals(container1, container2);
    Assert.assertNotEquals(container1.hashCode(), container2.hashCode());
  }

  @Test
  public void testEquality_differentImageId() {
    JibContainer container1 = new JibContainer(targetImage1, digest1, digest1, tags1, true);
    JibContainer container2 = new JibContainer(targetImage1, digest1, digest2, tags1, true);

    Assert.assertNotEquals(container1, container2);
    Assert.assertNotEquals(container1.hashCode(), container2.hashCode());
  }

  @Test
  public void testEquality_differentTags() {
    JibContainer container1 = new JibContainer(targetImage1, digest1, digest1, tags1, true);
    JibContainer container2 = new JibContainer(targetImage1, digest1, digest1, tags2, true);

    Assert.assertNotEquals(container1, container2);
    Assert.assertNotEquals(container1.hashCode(), container2.hashCode());
  }
}
