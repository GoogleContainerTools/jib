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

package com.google.cloud.tools.jib.api;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.Assert;
import org.junit.jupiter.api.Test;

/** Tests for {@link com.google.cloud.tools.jib.api.Credential}. */
class CredentialTest {

  @Test
  void testCredentialsHash() {
    Credential credentialA1 = Credential.from("username", "password");
    Credential credentialA2 = Credential.from("username", "password");
    Credential credentialB1 = Credential.from("", "");
    Credential credentialB2 = Credential.from("", "");

    Assert.assertEquals(credentialA1, credentialA2);
    Assert.assertEquals(credentialB1, credentialB2);
    Assert.assertNotEquals(credentialA1, credentialB1);
    Assert.assertNotEquals(credentialA1, credentialB2);

    Set<Credential> credentialSet =
        new HashSet<>(Arrays.asList(credentialA1, credentialA2, credentialB1, credentialB2));
    Assert.assertEquals(new HashSet<>(Arrays.asList(credentialA2, credentialB1)), credentialSet);
  }

  @Test
  void testCredentialsOAuth2RefreshToken() {
    Credential oauth2Credential = Credential.from("<token>", "eyJhbGciOi...3gw");
    Assert.assertTrue(
        "Credential should be an auth2 token when username is <token>",
        oauth2Credential.isOAuth2RefreshToken());
    Assert.assertEquals(
        "OAuth2 token credential should take password as refresh token",
        "eyJhbGciOi...3gw",
        oauth2Credential.getPassword());
  }
}
