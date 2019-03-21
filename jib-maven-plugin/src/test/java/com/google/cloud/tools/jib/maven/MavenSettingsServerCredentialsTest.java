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
import com.google.cloud.tools.jib.plugins.common.InferredAuthRetrievalException;
import java.util.Collections;
import java.util.Optional;
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

/** Tests for {@link MavenSettingsServerCredentials}. */
@RunWith(MockitoJUnitRunner.class)
public class MavenSettingsServerCredentialsTest {

  @Mock private Settings mockSettings;
  @Mock private Server mockServer1;
  @Mock private SettingsDecrypter mockSettingsDecrypter;
  @Mock private SettingsDecryptionResult mockSettingsDecryptionResult;

  private MavenSettingsServerCredentials testMavenSettingsServerCredentials;

  @Before
  public void setUp() {
    Mockito.when(mockSettingsDecryptionResult.getProblems()).thenReturn(Collections.emptyList());
    Mockito.when(mockSettingsDecryptionResult.getServer()).thenReturn(mockServer1);
    Mockito.when(mockSettingsDecrypter.decrypt(Mockito.any()))
        .thenReturn(mockSettingsDecryptionResult);

    testMavenSettingsServerCredentials =
        new MavenSettingsServerCredentials(mockSettings, mockSettingsDecrypter);
  }

  @Test
  public void testRetrieve_found() throws InferredAuthRetrievalException {
    Mockito.when(mockSettings.getServer("server1")).thenReturn(mockServer1);

    Mockito.when(mockServer1.getUsername()).thenReturn("server1 username");
    Mockito.when(mockServer1.getPassword()).thenReturn("server1 password");

    Optional<AuthProperty> auth = testMavenSettingsServerCredentials.getAuth("server1");
    Assert.assertTrue(auth.isPresent());
    Assert.assertEquals("server1 username", auth.get().getUsername());
    Assert.assertEquals("server1 password", auth.get().getPassword());
  }

  @Test
  public void testRetrieve_notFound() throws InferredAuthRetrievalException {
    Assert.assertFalse(testMavenSettingsServerCredentials.getAuth("serverUnknown").isPresent());
  }

  @Test
  public void testRetrieve_withNullServer() throws InferredAuthRetrievalException {
    Assert.assertFalse(testMavenSettingsServerCredentials.getAuth(null).isPresent());
  }

  @Test
  public void testRetrieve_withDecrypter_success() throws InferredAuthRetrievalException {
    testMavenSettingsServerCredentials =
        new MavenSettingsServerCredentials(mockSettings, mockSettingsDecrypter);

    // essentially the same as testRetrieve_found()
    Mockito.when(mockSettings.getServer("server1")).thenReturn(mockServer1);
    Mockito.when(mockServer1.getUsername()).thenReturn("server1 username");
    Mockito.when(mockServer1.getPassword()).thenReturn("server1 password");

    Optional<AuthProperty> auth = testMavenSettingsServerCredentials.getAuth("server1");
    Assert.assertTrue(auth.isPresent());
    Assert.assertEquals("server1 username", auth.get().getUsername());
    Assert.assertEquals("server1 password", auth.get().getPassword());

    Mockito.verify(mockSettingsDecrypter).decrypt(Mockito.any());
    Mockito.verify(mockSettingsDecryptionResult).getProblems();
    Mockito.verify(mockSettingsDecryptionResult, Mockito.atLeastOnce()).getServer();
  }

  @Test
  public void testRetrieve_withDecrypter_failure() {

    SettingsProblem mockProblem = Mockito.mock(SettingsProblem.class);
    Mockito.when(mockProblem.getSeverity()).thenReturn(SettingsProblem.Severity.ERROR);
    // Maven's SettingsProblem has a more structured toString, but irrelevant here
    Mockito.when(mockProblem.toString()).thenReturn("MockProblemText");
    Mockito.when(mockSettingsDecryptionResult.getProblems())
        .thenReturn(Collections.singletonList(mockProblem));

    testMavenSettingsServerCredentials =
        new MavenSettingsServerCredentials(mockSettings, mockSettingsDecrypter);

    // essentially the same as testRetrieve_found()
    Mockito.when(mockSettings.getServer("server1")).thenReturn(mockServer1);

    try {
      testMavenSettingsServerCredentials.getAuth("server1");
      Assert.fail("decryption should have failed");
    } catch (InferredAuthRetrievalException ex) {
      Assert.assertEquals(
          ex.getMessage(), "Unable to decrypt password for server1: MockProblemText");
      Mockito.verify(mockSettingsDecrypter).decrypt(Mockito.any());
      Mockito.verify(mockSettingsDecryptionResult).getProblems();
      Mockito.verifyNoMoreInteractions(
          mockSettingsDecryptionResult); // getServer() should never be called
    }
  }
}
