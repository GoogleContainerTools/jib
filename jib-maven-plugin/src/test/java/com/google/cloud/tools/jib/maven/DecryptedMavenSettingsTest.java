/*
 * Copyright 2019 Google LLC.
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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.settings.Proxy;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.building.SettingsProblem;
import org.apache.maven.settings.crypto.SettingsDecrypter;
import org.apache.maven.settings.crypto.SettingsDecryptionResult;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests for {@link DecryptedMavenSettings}. */
@RunWith(MockitoJUnitRunner.class)
public class DecryptedMavenSettingsTest {

  @Mock private Server server1;
  @Mock private Server server2;
  @Mock private Proxy proxy;
  @Mock private Settings settings;
  @Mock private SettingsDecrypter settingsDecrypter;
  @Mock private SettingsDecryptionResult decryptionResult;
  @Mock private Log log;

  private final List<Server> servers = Arrays.asList(server1, server2);
  private final List<Proxy> proxies = Arrays.asList(proxy);

  private DecryptedMavenSettings decryptedSettings;

  @Before
  public void setUp() {
    Mockito.when(settingsDecrypter.decrypt(Mockito.any())).thenReturn(decryptionResult);
    Mockito.when(decryptionResult.getProblems()).thenReturn(Collections.emptyList());

    decryptedSettings = DecryptedMavenSettings.from(settings, settingsDecrypter, log);
  }

  @Test
  public void testFrom_decrypterFailure() {
    SettingsProblem problem = Mockito.mock(SettingsProblem.class);
    Mockito.when(problem.getSeverity()).thenReturn(SettingsProblem.Severity.ERROR);
    // Maven's SettingsProblem has a more structured toString, but irrelevant here
    Mockito.when(problem.toString()).thenReturn("MockProblemText");
    Mockito.when(decryptionResult.getProblems()).thenReturn(Arrays.asList(problem));

    DecryptedMavenSettings.from(settings, settingsDecrypter, log);
    Mockito.verify(log).warn("Unable to decrypt settings.xml: MockProblemText");
  }

  @Test
  public void testGetServers() {
    Mockito.when(decryptionResult.getServers()).thenReturn(servers);
    Assert.assertEquals(servers, decryptedSettings.getServers());
  }

  @Test
  public void testGetProxies() {
    Mockito.when(decryptionResult.getProxies()).thenReturn(proxies);
    Assert.assertEquals(proxies, decryptedSettings.getProxies());
  }

  @Test
  public void testGetServers_emptyListFromDecryption() {
    Mockito.when(decryptionResult.getServers()).thenReturn(Collections.emptyList());
    Mockito.when(settings.getServers()).thenReturn(servers);

    Assert.assertEquals(servers, decryptedSettings.getServers());
  }

  @Test
  public void testGetProxies_emptyListFromDecryption() {
    Mockito.when(decryptionResult.getProxies()).thenReturn(Collections.emptyList());
    Mockito.when(settings.getProxies()).thenReturn(proxies);

    Assert.assertEquals(proxies, decryptedSettings.getProxies());
  }
}
