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

import com.google.cloud.tools.jib.api.RegistryException;
import com.google.cloud.tools.jib.event.EventHandlers;
import com.google.cloud.tools.jib.http.FailoverHttpClient;
import java.io.IOException;
import org.junit.jupiter.api.Test;

/** Integration tests for bearer authentication. */
public class BearerAuthenticationIntegrationTest {

  private final FailoverHttpClient httpClient = new FailoverHttpClient(false, false, ignored -> {});

  @Test
  public void testGetRegistryAuthenticator() throws IOException, RegistryException {
    RegistryClient registryClient =
        RegistryClient.factory(
                EventHandlers.NONE, "registry.hub.docker.com", "library/busybox", httpClient)
            .newRegistryClient();
    // For public images, Docker Hub still requires bearer authentication (without credentials)
    registryClient.doPullBearerAuth();
    registryClient.pullManifest("latest");
  }
}
