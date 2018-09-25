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

import com.google.cloud.tools.jib.EmptyJibLogger;
import com.google.cloud.tools.jib.http.Authorization;
import com.google.cloud.tools.jib.image.ImageReference;
import com.google.cloud.tools.jib.image.InvalidImageReferenceException;
import java.io.IOException;
import org.junit.Assert;
import org.junit.Test;

/** Integration tests for {@link RegistryAuthenticator}. */
public class RegistryAuthenticatorIntegrationTest {

  private static final EmptyJibLogger BUILD_LOGGER = new EmptyJibLogger();

  @Test
  public void testAuthenticate()
      throws RegistryAuthenticationFailedException, InvalidImageReferenceException, IOException,
          RegistryException {
    ImageReference dockerHubImageReference = ImageReference.parse("library/busybox");
    RegistryAuthenticator registryAuthenticator =
        RegistryAuthenticator.initializer(
                BUILD_LOGGER,
                dockerHubImageReference.getRegistry(),
                dockerHubImageReference.getRepository())
            .initialize();
    Assert.assertNotNull(registryAuthenticator);
    Authorization authorization = registryAuthenticator.authenticatePull();

    // Checks that some token was received.
    Assert.assertTrue(0 < authorization.getToken().length());
  }
}
