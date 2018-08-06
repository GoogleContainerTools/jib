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
  @Mock private AuthProperty mockAuth;

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
    Mockito.when(mockAuth.getUsernamePropertyDescriptor()).thenReturn("user");
    Mockito.when(mockAuth.getPasswordPropertyDescriptor()).thenReturn("pass");
    Mockito.when(mockAuth.getUsername()).thenReturn("vwxyz");
    Mockito.when(mockAuth.getPassword()).thenReturn("98765");

    // System properties set
    System.setProperty("jib.test.auth.user", "abcde");
    System.setProperty("jib.test.auth.pass", "12345");
    Authorization expected = Authorizations.withBasicCredentials("abcde", "12345");
    Authorization actual =
        ConfigurationPropertyValidator.getImageAuth(
            mockLogger, "jib.test.auth.user", "jib.test.auth.pass", mockAuth);
    Assert.assertNotNull(actual);
    Assert.assertEquals(expected.toString(), actual.toString());

    // Auth set in configuration
    System.clearProperty("jib.test.auth.user");
    System.clearProperty("jib.test.auth.pass");
    expected = Authorizations.withBasicCredentials("vwxyz", "98765");
    actual =
        ConfigurationPropertyValidator.getImageAuth(
            mockLogger, "jib.test.auth.user", "jib.test.auth.pass", mockAuth);
    Assert.assertNotNull(actual);
    Assert.assertEquals(expected.toString(), actual.toString());
    Mockito.verify(mockLogger, Mockito.never()).warn(Mockito.any());

    // Auth completely missing
    Mockito.when(mockAuth.getUsername()).thenReturn(null);
    Mockito.when(mockAuth.getPassword()).thenReturn(null);
    actual =
        ConfigurationPropertyValidator.getImageAuth(
            mockLogger, "jib.test.auth.user", "jib.test.auth.pass", mockAuth);
    Assert.assertNull(actual);

    // Password missing
    Mockito.when(mockAuth.getUsername()).thenReturn("vwxyz");
    Mockito.when(mockAuth.getPassword()).thenReturn(null);
    actual =
        ConfigurationPropertyValidator.getImageAuth(
            mockLogger, "jib.test.auth.user", "jib.test.auth.pass", mockAuth);
    Assert.assertNull(actual);
    Mockito.verify(mockLogger)
        .warn("pass is missing from build configuration; ignoring auth section.");

    // Username missing
    Mockito.when(mockAuth.getUsername()).thenReturn(null);
    Mockito.when(mockAuth.getPassword()).thenReturn("98765");
    actual =
        ConfigurationPropertyValidator.getImageAuth(
            mockLogger, "jib.test.auth.user", "jib.test.auth.pass", mockAuth);
    Assert.assertNull(actual);
    Mockito.verify(mockLogger)
        .warn("user is missing from build configuration; ignoring auth section.");
  }
}
