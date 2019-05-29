/*
 * Copyright 2017 Google LLC.
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

import com.google.cloud.tools.jib.api.ImageReference;
import com.google.cloud.tools.jib.api.InvalidImageReferenceException;
import com.google.cloud.tools.jib.api.RegistryException;
import com.google.cloud.tools.jib.event.EventHandlers;
import com.google.cloud.tools.jib.http.Authorization;
import java.io.IOException;
import org.junit.Assert;
import org.junit.Test;

/** Integration tests for {@link RegistryAuthenticator}. */
public class RegistryAuthenticatorIntegrationTest {

  @Test
  public void testAuthenticate()
      throws IOException, RegistryException, InvalidImageReferenceException {
    ImageReference dockerHubImageReference = ImageReference.parse("library/busybox");
    RegistryAuthenticator registryAuthenticator =
        RegistryClient.factory(
                EventHandlers.NONE,
                dockerHubImageReference.getRegistry(),
                dockerHubImageReference.getRepository())
            .newRegistryClient()
            .getRegistryAuthenticator();
    Assert.assertNotNull(registryAuthenticator);
    Authorization authorization = registryAuthenticator.authenticatePull(null);

    // Checks that some token was received.
    Assert.assertTrue(0 < authorization.getToken().length());
  }
}
