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

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.RestoreSystemProperties;

/** Tests for {@link JibSystemProperties}. */
public class JibSystemPropertiesTest {

  @Rule public final RestoreSystemProperties systemPropertyRestorer = new RestoreSystemProperties();

  @Test
  public void testCheckHttpTimeoutProperty_okWhenUndefined() throws NumberFormatException {
    System.clearProperty(JibSystemProperties.HTTP_TIMEOUT);
    JibSystemProperties.checkHttpTimeoutProperty();
  }

  @Test
  public void testCheckHttpTimeoutProperty_stringValue() {
    System.setProperty(JibSystemProperties.HTTP_TIMEOUT, "random string");
    try {
      JibSystemProperties.checkHttpTimeoutProperty();
      Assert.fail();
    } catch (NumberFormatException ex) {
      Assert.assertEquals("jib.httpTimeout must be an integer: random string", ex.getMessage());
    }
  }

  @Test
  public void testCheckHttpProxyPortProperty_undefined() throws NumberFormatException {
    System.clearProperty("http.proxyPort");
    System.clearProperty("https.proxyPort");
    JibSystemProperties.checkProxyPortProperty();
  }

  @Test
  public void testCheckHttpProxyPortProperty() throws NumberFormatException {
    System.setProperty("http.proxyPort", "0");
    System.setProperty("https.proxyPort", "0");
    JibSystemProperties.checkProxyPortProperty();

    System.setProperty("http.proxyPort", "1");
    System.setProperty("https.proxyPort", "1");
    JibSystemProperties.checkProxyPortProperty();

    System.setProperty("http.proxyPort", "65535");
    System.setProperty("https.proxyPort", "65535");
    JibSystemProperties.checkProxyPortProperty();

    System.setProperty("http.proxyPort", "65534");
    System.setProperty("https.proxyPort", "65534");
    JibSystemProperties.checkProxyPortProperty();
  }

  @Test
  public void testCheckHttpProxyPortProperty_negativeValue() {
    System.setProperty("http.proxyPort", "-1");
    System.clearProperty("https.proxyPort");
    try {
      JibSystemProperties.checkProxyPortProperty();
      Assert.fail();
    } catch (NumberFormatException ex) {
      Assert.assertEquals("http.proxyPort cannot be less than 0: -1", ex.getMessage());
    }

    System.clearProperty("http.proxyPort");
    System.setProperty("https.proxyPort", "-1");
    try {
      JibSystemProperties.checkProxyPortProperty();
      Assert.fail();
    } catch (NumberFormatException ex) {
      Assert.assertEquals("https.proxyPort cannot be less than 0: -1", ex.getMessage());
    }
  }

  @Test
  public void testCheckHttpProxyPortProperty_over65535() {
    System.setProperty("http.proxyPort", "65536");
    System.clearProperty("https.proxyPort");
    try {
      JibSystemProperties.checkProxyPortProperty();
      Assert.fail();
    } catch (NumberFormatException ex) {
      Assert.assertEquals("http.proxyPort cannot be greater than 65535: 65536", ex.getMessage());
    }

    System.clearProperty("http.proxyPort");
    System.setProperty("https.proxyPort", "65536");
    try {
      JibSystemProperties.checkProxyPortProperty();
      Assert.fail();
    } catch (NumberFormatException ex) {
      Assert.assertEquals("https.proxyPort cannot be greater than 65535: 65536", ex.getMessage());
    }
  }

  @Test
  public void testCheckHttpProxyPortProperty_stringValue() {
    System.setProperty("http.proxyPort", "some string");
    System.clearProperty("https.proxyPort");
    try {
      JibSystemProperties.checkProxyPortProperty();
      Assert.fail();
    } catch (NumberFormatException ex) {
      Assert.assertEquals("http.proxyPort must be an integer: some string", ex.getMessage());
    }

    System.clearProperty("http.proxyPort");
    System.setProperty("https.proxyPort", "some string");
    try {
      JibSystemProperties.checkProxyPortProperty();
      Assert.fail();
    } catch (NumberFormatException ex) {
      Assert.assertEquals("https.proxyPort must be an integer: some string", ex.getMessage());
    }
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

  @Test
  public void testSkipExistingImages_undefined() {
    System.clearProperty(JibSystemProperties.SKIP_EXISTING_IMAGES);
    Assert.assertFalse(JibSystemProperties.skipExistingImages());
  }
}
