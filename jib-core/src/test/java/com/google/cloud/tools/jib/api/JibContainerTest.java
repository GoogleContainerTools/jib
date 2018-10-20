/*
 * Copyright 2018 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud.tools.jib.api;

import com.google.cloud.tools.jib.image.DescriptorDigest;
import java.security.DigestException;
import org.junit.Assert;
import org.junit.Test;

/** Tests for {@link JibContainer}. */
public class JibContainerTest {

  private static final String digest1 =
      "sha256:abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789";
  private static final String digest2 =
      "sha256:9876543210fedcba9876543210fedcba9876543210fedcba9876543210fedcba";
  private static final String digest3 =
      "sha256:fedcba9876543210fedcba9876543210fedcba9876543210fedcba9876543210";

  @Test
  public void testCreation() throws DigestException {
    JibContainer container =
        JibContainer.create(
            DescriptorDigest.fromDigest(digest1), DescriptorDigest.fromDigest(digest2));

    Assert.assertEquals(digest1, container.getDigest().toString());
    Assert.assertEquals(digest2, container.getImageId().toString());
  }

  @Test
  public void testEquality() throws DigestException {
    JibContainer container1 =
        JibContainer.create(
            DescriptorDigest.fromDigest(digest1), DescriptorDigest.fromDigest(digest2));
    JibContainer container2 =
        JibContainer.create(
            DescriptorDigest.fromDigest(digest1), DescriptorDigest.fromDigest(digest2));
    JibContainer container3 =
        JibContainer.create(
            DescriptorDigest.fromDigest(digest2), DescriptorDigest.fromDigest(digest3));

    Assert.assertEquals(container1, container2);
    Assert.assertEquals(container1.hashCode(), container2.hashCode());
    Assert.assertNotEquals(container1, container3);
  }
}
