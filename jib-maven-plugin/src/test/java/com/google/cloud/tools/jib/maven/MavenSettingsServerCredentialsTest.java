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

import com.google.cloud.tools.jib.configuration.credentials.Credential;
import java.util.Collections;
import java.util.Optional;
import org.apache.maven.plugin.MojoExecutionException;
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
  @Mock private MavenJibLogger mockLogger;

  private MavenSettingsServerCredentials testMavenSettingsServerCredentials;

  @Before
  public void setUp() {
    testMavenSettingsServerCredentials =
        new MavenSettingsServerCredentials(mockSettings, null, mockLogger);
  }

  @Test
  public void testRetrieve_found() throws MojoExecutionException {
    Mockito.when(mockSettings.getServer("server1")).thenReturn(mockServer1);

    Mockito.when(mockServer1.getUsername()).thenReturn("server1 username");
    Mockito.when(mockServer1.getPassword()).thenReturn("server1 password");

    Optional<Credential> optionalCredential =
        testMavenSettingsServerCredentials.retrieve("server1");
    Assert.assertTrue(optionalCredential.isPresent());
    Assert.assertEquals(
        Credential.basic("server1 username", "server1 password"), optionalCredential.get());

    Mockito.verifyZeroInteractions(mockLogger);
  }

  @Test
  public void testRetrieve_notFound() throws MojoExecutionException {
    Assert.assertFalse(testMavenSettingsServerCredentials.retrieve("serverUnknown").isPresent());
  }

  @Test
  public void testRetrieve_withNullServer() throws MojoExecutionException {
    Assert.assertFalse(testMavenSettingsServerCredentials.retrieve(null).isPresent());
  }

  @Test
  public void testRetrieve_withNullDecrypter_encrypted() throws MojoExecutionException {
    Mockito.when(mockSettings.getServer("server1")).thenReturn(mockServer1);
    Mockito.when(mockServer1.getUsername()).thenReturn("server1 username");
    Mockito.when(mockServer1.getPassword()).thenReturn("{COQLCE6DU6GtcS5P=}");

    Optional<Credential> optionalCredential =
        testMavenSettingsServerCredentials.retrieve("server1");
    Assert.assertTrue(optionalCredential.isPresent());
    Assert.assertEquals(
        Credential.basic("server1 username", "{COQLCE6DU6GtcS5P=}"), optionalCredential.get());
    Mockito.verify(mockLogger)
        .warn(
            "Server password for registry server1 appears to be encrypted, "
                + "but there is no decrypter available");
  }

  @Test
  public void testRetrieve_withDecrypter_success() throws MojoExecutionException {
    SettingsDecryptionResult mockResult = Mockito.mock(SettingsDecryptionResult.class);
    Mockito.when(mockResult.getProblems()).thenReturn(Collections.emptyList());
    Mockito.when(mockResult.getServer()).thenReturn(mockServer1);

    // don't actually perform encryption/decryption
    SettingsDecrypter mockDecrypter = Mockito.mock(SettingsDecrypter.class);
    Mockito.when(mockDecrypter.decrypt(Mockito.any())).thenReturn(mockResult);
    testMavenSettingsServerCredentials =
        new MavenSettingsServerCredentials(mockSettings, mockDecrypter, mockLogger);

    // essentially the same as testRetrieve_found()
    Mockito.when(mockSettings.getServer("server1")).thenReturn(mockServer1);
    Mockito.when(mockServer1.getUsername()).thenReturn("server1 username");
    Mockito.when(mockServer1.getPassword()).thenReturn("server1 password");

    Optional<Credential> optionalCredential =
        testMavenSettingsServerCredentials.retrieve("server1");
    Assert.assertTrue(optionalCredential.isPresent());
    Assert.assertEquals(
        Credential.basic("server1 username", "server1 password"), optionalCredential.get());

    Mockito.verify(mockDecrypter).decrypt(Mockito.any());
    Mockito.verify(mockResult).getProblems();
    Mockito.verify(mockResult, Mockito.atLeastOnce()).getServer();
  }

  @Test
  public void testRetrieve_withDecrypter_failure() {

    SettingsProblem mockProblem = Mockito.mock(SettingsProblem.class);
    Mockito.when(mockProblem.getSeverity()).thenReturn(SettingsProblem.Severity.ERROR);
    // Maven's SettingsProblem has a more structured toString, but irrelevant here
    Mockito.when(mockProblem.toString()).thenReturn("MockProblemText");

    SettingsDecryptionResult mockResult = Mockito.mock(SettingsDecryptionResult.class);
    Mockito.when(mockResult.getProblems()).thenReturn(Collections.singletonList(mockProblem));

    // return an result with problems
    SettingsDecrypter mockDecrypter = Mockito.mock(SettingsDecrypter.class);
    Mockito.when(mockDecrypter.decrypt(Mockito.any())).thenReturn(mockResult);
    testMavenSettingsServerCredentials =
        new MavenSettingsServerCredentials(mockSettings, mockDecrypter, mockLogger);

    // essentially the same as testRetrieve_found()
    Mockito.when(mockSettings.getServer("server1")).thenReturn(mockServer1);

    try {
      testMavenSettingsServerCredentials.retrieve("server1");
      Assert.fail("decryption should have failed");
    } catch (MojoExecutionException ex) {
      Assert.assertEquals(
          ex.getMessage(), "Unable to decrypt password for server1: MockProblemText");
      Mockito.verify(mockDecrypter).decrypt(Mockito.any());
      Mockito.verify(mockResult).getProblems();
      Mockito.verifyNoMoreInteractions(mockResult); // getServer() should never be called
    }
  }

  @Test
  public void testIsEncrypted_plaintext() {
    Assert.assertFalse(MavenSettingsServerCredentials.isEncrypted("plain text"));
  }

  @Test
  public void testIsEncrypted_encryptedPayload() {
    String examples[] = {
      "{COQLCE6DU6GtcS5P=}",
      "expires on 2009-04-11 {COQLCE6DU6GtcS5P=}", // with note
      "{jSMOWnoPFgsHVpMvz5VrIt5kRbzGpI8u+\\{EF1iFQyJQ=}" // with escaped brace
    };
    for (String payload : examples) {
      Assert.assertTrue(MavenSettingsServerCredentials.isEncrypted(payload));
    }
  }
}
