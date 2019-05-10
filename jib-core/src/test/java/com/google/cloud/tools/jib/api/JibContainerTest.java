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

import java.security.DigestException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Tests for {@link JibContainer}. */
public class JibContainerTest {

  @Rule public TemporaryFolder temporaryDirectory = new TemporaryFolder();

  private DescriptorDigest digest1;
  private DescriptorDigest digest2;
  private DescriptorDigest digest3;

  @Before
  public void setUp() throws DigestException {
    digest1 =
        DescriptorDigest.fromDigest(
            "sha256:abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789");
    digest2 =
        DescriptorDigest.fromDigest(
            "sha256:9876543210fedcba9876543210fedcba9876543210fedcba9876543210fedcba");
    digest3 =
        DescriptorDigest.fromDigest(
            "sha256:fedcba9876543210fedcba9876543210fedcba9876543210fedcba9876543210");
  }

  @Test
  public void testCreation() {
    JibContainer container = new JibContainer(digest1, digest2);

    Assert.assertEquals(digest1, container.getDigest());
    Assert.assertEquals(digest2, container.getImageId());
  }

  @Test
  public void testEquality() {
    JibContainer container1 = new JibContainer(digest1, digest2);
    JibContainer container2 = new JibContainer(digest1, digest2);
    JibContainer container3 = new JibContainer(digest2, digest3);

    Assert.assertEquals(container1, container2);
    Assert.assertEquals(container1.hashCode(), container2.hashCode());
    Assert.assertNotEquals(container1, container3);
  }
}
