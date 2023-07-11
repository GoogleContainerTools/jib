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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.google.cloud.tools.jib.api.DescriptorDigest;
import com.google.cloud.tools.jib.api.RegistryException;
import com.google.cloud.tools.jib.event.EventHandlers;
import com.google.cloud.tools.jib.http.FailoverHttpClient;
import com.google.cloud.tools.jib.image.json.V22ManifestTemplate;
import java.io.IOException;
import java.security.DigestException;
import org.junit.jupiter.api.Test;

/** Integration tests for {@link BlobChecker}. */
public class BlobCheckerIntegrationTest {

  private final FailoverHttpClient httpClient = new FailoverHttpClient(true, false, ignored -> {});

  @Test
  public void testCheck_exists() throws IOException, RegistryException {
    RegistryClient registryClient =
        RegistryClient.factory(EventHandlers.NONE, "gcr.io", "distroless/base", httpClient)
            .newRegistryClient();
    V22ManifestTemplate manifestTemplate =
        registryClient
            .pullManifest(
                ManifestPullerIntegrationTest.KNOWN_MANIFEST_V22_SHA, V22ManifestTemplate.class)
            .getManifest();
    DescriptorDigest blobDigest = manifestTemplate.getLayers().get(0).getDigest();

    assertEquals(blobDigest, registryClient.checkBlob(blobDigest).get().getDigest());
  }

  @Test
  public void testCheck_doesNotExist() throws IOException, RegistryException, DigestException {
    RegistryClient registryClient =
        RegistryClient.factory(EventHandlers.NONE, "gcr.io", "distroless/base", httpClient)
            .newRegistryClient();
    DescriptorDigest fakeBlobDigest =
        DescriptorDigest.fromHash(
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");

    assertFalse(registryClient.checkBlob(fakeBlobDigest).isPresent());
  }
}
