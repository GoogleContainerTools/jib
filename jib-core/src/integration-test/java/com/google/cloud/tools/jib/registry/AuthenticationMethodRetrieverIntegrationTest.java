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

package com.google.cloud.tools.jib.registry;

import com.google.cloud.tools.jib.EmptyJibLogger;
import com.google.cloud.tools.jib.http.Authorization;
import java.io.IOException;
import org.junit.Assert;
import org.junit.Test;

/** Integration tests for {@link AuthenticationMethodRetriever}. */
public class AuthenticationMethodRetrieverIntegrationTest {

  private static final EmptyJibLogger BUILD_LOGGER = new EmptyJibLogger();

  @Test
  public void testGetRegistryAuthenticator()
      throws RegistryAuthenticationFailedException, IOException, RegistryException {
    RegistryClient registryClient =
        RegistryClient.factory(BUILD_LOGGER, "registry.hub.docker.com", "library/busybox")
            .newRegistryClient();
    RegistryAuthenticator registryAuthenticator = registryClient.getRegistryAuthenticator();
    Assert.assertNotNull(registryAuthenticator);
    Authorization authorization = registryAuthenticator.authenticatePull();

    RegistryClient authorizedRegistryClient =
        RegistryClient.factory(BUILD_LOGGER, "registry.hub.docker.com", "library/busybox")
            .setAuthorization(authorization)
            .newRegistryClient();
    authorizedRegistryClient.pullManifest("latest");
  }
}
