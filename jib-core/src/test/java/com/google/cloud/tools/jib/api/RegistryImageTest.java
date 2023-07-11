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

import com.google.cloud.tools.jib.registry.credentials.CredentialRetrievalException;
import org.junit.Assert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** Tests for {@link RegistryImage}. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RegistryImageTest {

  @Mock private CredentialRetriever mockCredentialRetriever;

  @Test
  void testGetters_default() throws InvalidImageReferenceException {
    RegistryImage image = RegistryImage.named("registry/image");

    Assert.assertEquals("registry/image", image.getImageReference().toString());
    Assert.assertEquals(0, image.getCredentialRetrievers().size());
  }

  @Test
  void testGetters()
      throws InvalidImageReferenceException, AssertionError, CredentialRetrievalException {
    RegistryImage image =
        RegistryImage.named("registry/image")
            .addCredentialRetriever(mockCredentialRetriever)
            .addCredential("username", "password");

    Assert.assertEquals(2, image.getCredentialRetrievers().size());
    Assert.assertSame(mockCredentialRetriever, image.getCredentialRetrievers().get(0));
    Assert.assertEquals(
        Credential.from("username", "password"),
        image.getCredentialRetrievers().get(1).retrieve().get());
  }
}
