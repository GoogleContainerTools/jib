/*
 * Copyright 2018 Google LLC. All rights reserved.
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

package com.google.cloud.tools.jib.plugins.common;

import com.google.cloud.tools.jib.JibLogger;
import com.google.cloud.tools.jib.http.Authorization;
import com.google.cloud.tools.jib.http.Authorizations;
import com.google.cloud.tools.jib.image.ImageReference;
import com.google.cloud.tools.jib.image.InvalidImageReferenceException;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests for {@link ConfigurationPropertyValidator}. */
@RunWith(MockitoJUnitRunner.class)
public class ConfigurationPropertyValidatorTest {

  @Mock private JibLogger mockLogger;

  @After
  public void tearDown() {
    System.clearProperty("jib.httpTimeout");
  }

  @Test
  public void testCheckHttpTimeoutSystemProperty_ok() throws Exception {
    Assert.assertNull(System.getProperty("jib.httpTimeout"));
    ConfigurationPropertyValidator.checkHttpTimeoutProperty(Exception::new);
  }

  @Test
  public void testCheckHttpTimeoutSystemProperty_stringValue() {
    System.setProperty("jib.httpTimeout", "random string");
    try {
      ConfigurationPropertyValidator.checkHttpTimeoutProperty(Exception::new);
      Assert.fail("Should error with a non-integer timeout");
    } catch (Exception ex) {
      Assert.assertEquals("jib.httpTimeout must be an integer: random string", ex.getMessage());
    }
  }

  @Test
  public void testCheckHttpTimeoutSystemProperty_negativeValue() {
    System.setProperty("jib.httpTimeout", "-80");
    try {
      ConfigurationPropertyValidator.checkHttpTimeoutProperty(Exception::new);
      Assert.fail("Should error with a negative timeout");
    } catch (Exception ex) {
      Assert.assertEquals("jib.httpTimeout cannot be negative: -80", ex.getMessage());
    }
  }

  @Test
  public void testGetImageAuth() {
    // System properties set
    System.setProperty("jib.test.auth.username", "abcde");
    System.setProperty("jib.test.auth.password", "12345");
    Authorization expected = Authorizations.withBasicCredentials("abcde", "12345");
    Authorization actual =
        ConfigurationPropertyValidator.newMavenPropertyValidator(mockLogger)
            .getImageAuth("test", null, null);
    Assert.assertNotNull(actual);
    Assert.assertEquals(expected.toString(), actual.toString());

    // Auth set in configuration
    System.clearProperty("jib.test.auth.username");
    System.clearProperty("jib.test.auth.password");
    expected = Authorizations.withBasicCredentials("vwxyz", "98765");
    actual =
        ConfigurationPropertyValidator.newMavenPropertyValidator(mockLogger)
            .getImageAuth("test", "vwxyz", "98765");
    Assert.assertNotNull(actual);
    Assert.assertEquals(expected.toString(), actual.toString());
    Mockito.verify(mockLogger, Mockito.never()).warn(Mockito.any());

    // Auth completely missing
    actual =
        ConfigurationPropertyValidator.newMavenPropertyValidator(mockLogger)
            .getImageAuth("test", null, null);
    Assert.assertNull(actual);

    // Password missing
    actual =
        ConfigurationPropertyValidator.newMavenPropertyValidator(mockLogger)
            .getImageAuth("test", "vwxyz", null);
    Assert.assertNull(actual);
    Mockito.verify(mockLogger)
        .warn("<test><auth><password> is missing from build configuration; ignoring auth section.");

    // Username missing
    actual =
        ConfigurationPropertyValidator.newMavenPropertyValidator(mockLogger)
            .getImageAuth("test", null, "98765");
    Assert.assertNull(actual);
    Mockito.verify(mockLogger)
        .warn("<test><auth><username> is missing from build configuration; ignoring auth section.");
  }

  @Test
  public void testGetDockerTag() throws InvalidImageReferenceException {
    // Target configured
    ImageReference result =
        ConfigurationPropertyValidator.newMavenPropertyValidator(mockLogger)
            .getGeneratedTargetDockerTag("a/b:c", "proj", "ver");
    Assert.assertEquals("a/b", result.getRepository());
    Assert.assertEquals("c", result.getTag());
    Mockito.verify(mockLogger, Mockito.never()).lifecycle(Mockito.any());

    // Test maven not configured
    result =
        ConfigurationPropertyValidator.newMavenPropertyValidator(mockLogger)
            .getGeneratedTargetDockerTag(null, "proj", "ver");
    Assert.assertEquals("proj", result.getRepository());
    Assert.assertEquals("ver", result.getTag());
    Mockito.verify(mockLogger)
        .lifecycle(
            "Tagging image with generated image reference proj:ver. If you'd like to specify a "
                + "different tag, you can set the <to><image> parameter in your pom.xml, or use "
                + "the -Dimage=<MY IMAGE> commandline flag.");

    // Test gradle not configured
    result =
        ConfigurationPropertyValidator.newGradlePropertyValidator(mockLogger)
            .getGeneratedTargetDockerTag(null, "proj", "ver");
    Assert.assertEquals("proj", result.getRepository());
    Assert.assertEquals("ver", result.getTag());
    Mockito.verify(mockLogger)
        .lifecycle(
            "Tagging image with generated image reference proj:ver. If you'd like to specify a "
                + "different tag, you can set the jib.to.image parameter in your build.gradle, or "
                + "use the -Dimage=<MY IMAGE> commandline flag.");
  }
}
