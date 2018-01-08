/*
 * Copyright 2018 Google Inc.
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

package com.google.cloud.tools.crepecake.registry;

import java.net.MalformedURLException;
import java.net.URL;
import org.junit.Assert;
import org.junit.Test;

/** Tests for {@link RegistryAuthenticator}. */
public class RegistryAuthenticatorTest {

  @Test
  public void testFromAuthenticationMethod()
      throws MalformedURLException, RegistryAuthenticationFailedException {
    RegistryAuthenticator registryAuthenticator =
        RegistryAuthenticator.fromAuthenticationMethod(
            "Bearer realm=\"https://somerealm\",service=\"someservice\",scope=\"somescope\"",
            "someimage");
    Assert.assertEquals(
        new URL("https://somerealm?service=someservice&scope=repository:someimage:pull"),
        registryAuthenticator.getAuthenticationUrl());
  }

  @Test
  public void testFromAuthenticationMethod_noBearer() throws MalformedURLException {
    try {
      RegistryAuthenticator.fromAuthenticationMethod(
          "realm=\"https://somerealm\",service=\"someservice\",scope=\"somescope\"", "someimage");
      Assert.fail("Authentication method without 'Bearer ' should fail");

    } catch (RegistryAuthenticationFailedException ex) {
      Assert.assertEquals(
          "Failed to authenticate with the registry because: 'Bearer' was not found in the 'WWW-Authenticate' header, tried to parse: realm=\"https://somerealm\",service=\"someservice\",scope=\"somescope\"",
          ex.getMessage());
    }
  }

  @Test
  public void testFromAuthenticationMethod_noRealm() throws MalformedURLException {
    try {
      RegistryAuthenticator.fromAuthenticationMethod("Bearer scope=\"somescope\"", "someimage");
      Assert.fail("Authentication method without 'realm' should fail");

    } catch (RegistryAuthenticationFailedException ex) {
      Assert.assertEquals(
          "Failed to authenticate with the registry because: 'realm' was not found in the 'WWW-Authenticate' header, tried to parse: Bearer scope=\"somescope\"",
          ex.getMessage());
    }
  }

  @Test
  public void testFromAuthenticationMethod_noService() throws MalformedURLException {
    try {
      RegistryAuthenticator.fromAuthenticationMethod(
          "Bearer realm=\"https://somerealm\"", "someimage");
      Assert.fail("Authentication method without 'service' should fail");

    } catch (RegistryAuthenticationFailedException ex) {
      Assert.assertEquals(
          "Failed to authenticate with the registry because: 'service' was not found in the 'WWW-Authenticate' header, tried to parse: Bearer realm=\"https://somerealm\"",
          ex.getMessage());
    }
  }
}
