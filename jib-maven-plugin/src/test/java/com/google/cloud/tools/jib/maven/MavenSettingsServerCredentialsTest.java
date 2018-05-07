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

  private static final String SERVER1_USERNAME = "server1 username";
  private static final String SERVER1_PASSWORD = "server1 password";
  private static final String SERVER2_USERNAME = "server2 username";
  private static final String SERVER2_PASSWORD = "server2 password";
  private static final Authorization SERVER_1_AUTHORIZATION =
      Authorizations.withBasicCredentials(SERVER1_USERNAME, SERVER1_PASSWORD);
  private static final Authorization SERVER_2_AUTHORIZATION =
      Authorizations.withBasicCredentials(SERVER2_USERNAME, SERVER2_PASSWORD);

  @Mock private Settings mockSettings;
  @Mock private Server mockServer1;
  @Mock private Server mockServer2;

  private MavenSettingsServerCredentials testMavenSettingsServerCredentials;

  @Before
  public void setUp() {
    testMavenSettingsServerCredentials = new MavenSettingsServerCredentials(mockSettings);

    Mockito.when(mockSettings.getServer("server1")).thenReturn(mockServer1);
    Mockito.when(mockSettings.getServer("server2")).thenReturn(mockServer2);

    Mockito.when(mockServer1.getUsername()).thenReturn(SERVER1_USERNAME);
    Mockito.when(mockServer1.getPassword()).thenReturn(SERVER1_PASSWORD);
    Mockito.when(mockServer2.getUsername()).thenReturn(SERVER2_USERNAME);
    Mockito.when(mockServer2.getPassword()).thenReturn(SERVER2_PASSWORD);
  }

  @Test
  public void testRetrieve_found() {
    RegistryCredentials registryCredentials =
        testMavenSettingsServerCredentials.retrieve("server1", "server2");

    Assert.assertTrue(registryCredentials.has("server1"));
    Assert.assertTrue(registryCredentials.has("server2"));
    Assert.assertFalse(registryCredentials.has("serverUnknown"));
    Assert.assertEquals(
        MavenSettingsServerCredentials.CREDENTIAL_SOURCE,
        registryCredentials.getCredentialSource("server1"));
    Assert.assertEquals(
        MavenSettingsServerCredentials.CREDENTIAL_SOURCE,
        registryCredentials.getCredentialSource("server2"));

    Authorization retrievedServer1Authorization = registryCredentials.getAuthorization("server1");
    Assert.assertNotNull(retrievedServer1Authorization);
    Assert.assertEquals(
        SERVER_1_AUTHORIZATION.toString(), retrievedServer1Authorization.toString());

    Authorization retrievedServer2Authorization = registryCredentials.getAuthorization("server2");
    Assert.assertNotNull(retrievedServer2Authorization);
    Assert.assertEquals(
        SERVER_2_AUTHORIZATION.toString(), retrievedServer2Authorization.toString());
  }

  @Test
  public void testRetrieve_notFound() {
    RegistryCredentials registryCredentials =
        testMavenSettingsServerCredentials.retrieve("server1", "serverUnknown");

    Assert.assertTrue(registryCredentials.has("server1"));
    Assert.assertFalse(registryCredentials.has("server2"));
    Assert.assertFalse(registryCredentials.has("serverUnknown"));

    Authorization retrievedServer1Authorization = registryCredentials.getAuthorization("server1");
    Assert.assertNotNull(retrievedServer1Authorization);
    Assert.assertEquals(
        SERVER_1_AUTHORIZATION.toString(), retrievedServer1Authorization.toString());
  }

  @Test
  public void testRetrieve_withNullServer() {
    RegistryCredentials registryCredentials =
        testMavenSettingsServerCredentials.retrieve(null, "server2");

    Assert.assertFalse(registryCredentials.has("server1"));
    Assert.assertTrue(registryCredentials.has("server2"));
    Assert.assertFalse(registryCredentials.has("serverUnknown"));

    Authorization retrievedServer2Authorization = registryCredentials.getAuthorization("server2");
    Assert.assertNotNull(retrievedServer2Authorization);
    Assert.assertEquals(
        SERVER_2_AUTHORIZATION.toString(), retrievedServer2Authorization.toString());
  }
}
