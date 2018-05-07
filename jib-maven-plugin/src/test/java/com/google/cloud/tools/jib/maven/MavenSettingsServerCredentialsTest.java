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

import com.google.cloud.tools.jib.http.Authorization;
import com.google.cloud.tools.jib.http.Authorizations;
import com.google.cloud.tools.jib.registry.credentials.RegistryCredentials;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
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

  @Mock private Settings mockSettings;
  @Mock private Server mockServer1;

  private MavenSettingsServerCredentials testMavenSettingsServerCredentials;

  @Before
  public void setUp() {
    testMavenSettingsServerCredentials = new MavenSettingsServerCredentials(mockSettings);
  }

  @Test
  public void testRetrieve_found() {
    Mockito.when(mockSettings.getServer("server1")).thenReturn(mockServer1);

    Mockito.when(mockServer1.getUsername()).thenReturn("server1 username");
    Mockito.when(mockServer1.getPassword()).thenReturn("server1 password");

    RegistryCredentials registryCredentials =
        testMavenSettingsServerCredentials.retrieve("server1");

    Assert.assertNotNull(registryCredentials);
    Assert.assertEquals(
        MavenSettingsServerCredentials.CREDENTIAL_SOURCE,
        registryCredentials.getCredentialSource());

    Authorization retrievedServer1Authorization = registryCredentials.getAuthorization();
    Assert.assertNotNull(retrievedServer1Authorization);
    Assert.assertEquals(
        Authorizations.withBasicCredentials("server1 username", "server1 password").toString(),
        retrievedServer1Authorization.toString());
  }

  @Test
  public void testRetrieve_notFound() {
    RegistryCredentials registryCredentials =
        testMavenSettingsServerCredentials.retrieve("serverUnknown");

    Assert.assertNull(registryCredentials);
  }

  @Test
  public void testRetrieve_withNullServer() {
    RegistryCredentials registryCredentials = testMavenSettingsServerCredentials.retrieve(null);

    Assert.assertNull(registryCredentials);
  }
}
