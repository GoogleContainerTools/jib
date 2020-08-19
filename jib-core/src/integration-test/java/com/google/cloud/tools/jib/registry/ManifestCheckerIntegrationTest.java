/*
 * Copyright 2020 Google LLC.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.cloud.tools.jib.api.RegistryException;
import com.google.cloud.tools.jib.event.EventHandlers;
import com.google.cloud.tools.jib.http.FailoverHttpClient;
import com.google.cloud.tools.jib.image.json.ManifestTemplate;
import java.io.IOException;
import java.util.Optional;
import org.junit.Test;

/** Integration tests for {@link ManifestChecker}. */
public class ManifestCheckerIntegrationTest {

  /** A known manifest list sha for openjdk:11-jre-slim. */
  private static final String KNOWN_MANIFEST =
      "sha256:8ab7b3078b01ba66b937b7fbe0b9eccf60449cc101c42e99aeefaba0e1781155";

  /** A fictitious sha to test unknown images. */
  private static final String UNKNOWN_MANIFEST =
      "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

  private final FailoverHttpClient httpClient = new FailoverHttpClient(true, false, ignored -> {});

  @Test
  public void testExistingManifest() throws IOException, RegistryException {
    RegistryClient registryClient =
        RegistryClient.factory(
                EventHandlers.NONE, "registry-1.docker.io", "library/openjdk", httpClient)
            .newRegistryClient();
    registryClient.doPullBearerAuth();

    Optional<ManifestAndDigest<ManifestTemplate>> manifestDescriptor =
        registryClient.checkManifest(KNOWN_MANIFEST);

    assertTrue(manifestDescriptor.isPresent());
    assertEquals(KNOWN_MANIFEST, manifestDescriptor.get().getDigest().toString());
  }

  @Test
  public void testNonExistingManifest() throws IOException, RegistryException {
    RegistryClient registryClient =
        RegistryClient.factory(
                EventHandlers.NONE, "registry-1.docker.io", "library/openjdk", httpClient)
            .newRegistryClient();
    registryClient.doPullBearerAuth();

    Optional<ManifestAndDigest<ManifestTemplate>> manifestDescriptor =
        registryClient.checkManifest(UNKNOWN_MANIFEST);

    assertEquals(Optional.empty(), manifestDescriptor);
  }
}
