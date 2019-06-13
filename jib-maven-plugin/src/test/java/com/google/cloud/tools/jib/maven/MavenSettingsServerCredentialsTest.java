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
import com.google.cloud.tools.jib.plugins.common.InferredAuthException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link MavenSettingsServerCredentials}. */
public class MavenSettingsServerCredentialsTest {

  private MavenSettingsServerCredentials mavenSettingsServerCredentialsNoMasterPassword;
  private MavenSettingsServerCredentials mavenSettingsServerCredentials;
  private Path testSettings = Paths.get("src/test/resources/maven/settings/settings.xml");
  private Path testSettingsSecurity =
      Paths.get("src/test/resources/maven/settings/settings-security.xml");
  private Path testSettingsSecurityEmpty =
      Paths.get("src/test/resources/maven/settings/settings-security.empty.xml");

  @Before
  public void setUp() {
    mavenSettingsServerCredentials =
        new MavenSettingsServerCredentials(
            SettingsFixture.newSettings(testSettings),
            SettingsFixture.newSettingsDecrypter(testSettingsSecurity));
    mavenSettingsServerCredentialsNoMasterPassword =
        new MavenSettingsServerCredentials(
            SettingsFixture.newSettings(testSettings),
            SettingsFixture.newSettingsDecrypter(testSettingsSecurityEmpty));
  }

  @Test
  public void testInferredAuth_decrypterFailure() {
    try {
      mavenSettingsServerCredentials.inferAuth("badServer");
      Assert.fail();
    } catch (InferredAuthException ex) {
      Assert.assertThat(
          ex.getMessage(),
          CoreMatchers.startsWith("Unable to decrypt server(badServer) info from settings.xml:"));
    }
  }

  @Test
  public void testInferredAuth_successEncrypted() throws InferredAuthException {
    Optional<AuthProperty> auth = mavenSettingsServerCredentials.inferAuth("encryptedServer");
    Assert.assertTrue(auth.isPresent());
    Assert.assertEquals("encryptedUser", auth.get().getUsername());
    Assert.assertEquals("password1", auth.get().getPassword());
  }

  @Test
  public void testInferredAuth_successUnencrypted() throws InferredAuthException {
    Optional<AuthProperty> auth = mavenSettingsServerCredentials.inferAuth("simpleServer");
    Assert.assertTrue(auth.isPresent());
    Assert.assertEquals("simpleUser", auth.get().getUsername());
    Assert.assertEquals("password2", auth.get().getPassword());
  }

  @Test
  public void testInferredAuth_successNoPasswordDoesNotBlowUp() throws InferredAuthException {
    Optional<AuthProperty> auth =
        mavenSettingsServerCredentialsNoMasterPassword.inferAuth("simpleServer");
    Assert.assertTrue(auth.isPresent());
    Assert.assertEquals("simpleUser", auth.get().getUsername());
    Assert.assertEquals("password2", auth.get().getPassword());
  }

  @Test
  public void testInferredAuth_notFound() throws InferredAuthException {
    Assert.assertFalse(mavenSettingsServerCredentials.inferAuth("serverUnknown").isPresent());
  }
}
