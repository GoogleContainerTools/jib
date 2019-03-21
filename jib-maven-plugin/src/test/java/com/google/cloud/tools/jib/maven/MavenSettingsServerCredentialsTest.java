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

package com.google.cloud.tools.jib.maven;

import com.google.cloud.tools.jib.plugins.common.AuthProperty;
import java.util.Arrays;
import java.util.Optional;
import org.apache.maven.settings.Server;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests for {@link MavenSettingsServerCredentials}. */
@RunWith(MockitoJUnitRunner.class)
public class MavenSettingsServerCredentialsTest {

  @Mock private DecryptedMavenSettings mockSettings;
  @Mock private Server mockServer1;

  private MavenSettingsServerCredentials testMavenSettingsServerCredentials;

  @Before
  public void setUp() {
    Mockito.when(mockSettings.getServers()).thenReturn(Arrays.asList(mockServer1));
    Mockito.when(mockServer1.getId()).thenReturn("server1");
    Mockito.when(mockServer1.getUsername()).thenReturn("server1 username");
    Mockito.when(mockServer1.getPassword()).thenReturn("server1 password");
    testMavenSettingsServerCredentials = new MavenSettingsServerCredentials(mockSettings);
  }

  @Test
  public void testRetrieve_found() {
    Optional<AuthProperty> auth = testMavenSettingsServerCredentials.apply("server1");
    Assert.assertTrue(auth.isPresent());
    Assert.assertEquals("server1 username", auth.get().getUsername());
    Assert.assertEquals("server1 password", auth.get().getPassword());
  }

  @Test
  public void testRetrieve_notFound() {
    Assert.assertFalse(testMavenSettingsServerCredentials.apply("serverUnknown").isPresent());
  }
}
