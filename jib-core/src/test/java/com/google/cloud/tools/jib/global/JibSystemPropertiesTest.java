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

package com.google.cloud.tools.jib.global;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

/** Tests for {@link JibSystemProperties}. */
public class JibSystemPropertiesTest {

  @After
  public void tearDown() {
    System.clearProperty(JibSystemProperties.CROSS_REPOSITORY_BLOB_MOUNTS);
  }

  @Test
  public void testUseBlobMountsPropertyName() {
    Assert.assertEquals("jib.blobMounts", JibSystemProperties.CROSS_REPOSITORY_BLOB_MOUNTS);
  }

  @Test
  public void testUseBlobMounts_undefined() {
    System.clearProperty(JibSystemProperties.CROSS_REPOSITORY_BLOB_MOUNTS);
    Assert.assertTrue(JibSystemProperties.useCrossRepositoryBlobMounts());
  }

  @Test
  public void testUseBlobMounts_true() {
    System.setProperty(JibSystemProperties.CROSS_REPOSITORY_BLOB_MOUNTS, "true");
    Assert.assertTrue(JibSystemProperties.useCrossRepositoryBlobMounts());
  }

  @Test
  public void testUseBlobMounts_false() {
    System.setProperty(JibSystemProperties.CROSS_REPOSITORY_BLOB_MOUNTS, "false");
    Assert.assertFalse(JibSystemProperties.useCrossRepositoryBlobMounts());
  }

  @Test
  public void testUseBlobMounts_other() {
    System.setProperty(JibSystemProperties.CROSS_REPOSITORY_BLOB_MOUNTS, "nonbool");
    Assert.assertFalse(JibSystemProperties.useCrossRepositoryBlobMounts());
  }
}
