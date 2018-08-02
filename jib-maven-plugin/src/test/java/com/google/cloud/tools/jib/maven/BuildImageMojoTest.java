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

package com.google.cloud.tools.jib.maven;

import com.google.cloud.tools.jib.JibLogger;
import com.google.cloud.tools.jib.http.Authorization;
import com.google.cloud.tools.jib.http.Authorizations;
import com.google.cloud.tools.jib.maven.JibPluginConfiguration.AuthConfiguration;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * Test for {@link BuildImageMojo}.
 *
 * <p>TODO: This only tests the {@link BuildImageMojo#getImageAuth(JibLogger, String, String,
 * String, AuthConfiguration)} method, which is copy-pasted between the 3 build mojos. When we
 * refactor, we'll need to move this test.
 */
@RunWith(MockitoJUnitRunner.class)
public class BuildImageMojoTest {

  @Mock private JibLogger mockLogger;

  @Test
  public void testGetImageAuth() {
    AuthConfiguration auth = new AuthConfiguration();
    auth.setUsername("vwxyz");
    auth.setPassword("98765");

    // System properties set
    System.setProperty("jib.test.auth.user", "abcde");
    System.setProperty("jib.test.auth.pass", "12345");
    Authorization expected = Authorizations.withBasicCredentials("abcde", "12345");
    Authorization actual =
        BuildImageMojo.getImageAuth(
            mockLogger, "to", "jib.test.auth.user", "jib.test.auth.pass", auth);
    Assert.assertNotNull(actual);
    Assert.assertEquals(expected.toString(), actual.toString());

    // Auth set in configuration
    System.clearProperty("jib.test.auth.user");
    System.clearProperty("jib.test.auth.pass");
    expected = Authorizations.withBasicCredentials("vwxyz", "98765");
    actual =
        BuildImageMojo.getImageAuth(
            mockLogger, "to", "jib.test.auth.user", "jib.test.auth.pass", auth);
    Assert.assertNotNull(actual);
    Assert.assertEquals(expected.toString(), actual.toString());
    Mockito.verify(mockLogger, Mockito.never()).warn(Mockito.any());

    // Auth completely missing
    auth = new AuthConfiguration();
    actual =
        BuildImageMojo.getImageAuth(
            mockLogger, "to", "jib.test.auth.user", "jib.test.auth.pass", auth);
    Assert.assertNull(actual);

    // Password missing
    auth = new AuthConfiguration();
    auth.setUsername("vwxyz");
    actual =
        BuildImageMojo.getImageAuth(
            mockLogger, "to", "jib.test.auth.user", "jib.test.auth.pass", auth);
    Assert.assertNull(actual);
    Mockito.verify(mockLogger)
        .warn(
            "<to><auth><password> is missing from maven configuration; ignoring <to><auth> section.");

    // Username missing
    auth = new AuthConfiguration();
    auth.setPassword("98765");
    actual =
        BuildImageMojo.getImageAuth(
            mockLogger, "to", "jib.test.auth.user", "jib.test.auth.pass", auth);
    Assert.assertNull(actual);
    Mockito.verify(mockLogger)
        .warn(
            "<to><auth><username> is missing from maven configuration; ignoring <to><auth> section.");
  }
}
