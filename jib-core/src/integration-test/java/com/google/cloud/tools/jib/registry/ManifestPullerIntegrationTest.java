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

import static com.google.common.truth.Truth.assertThat;

import com.google.cloud.tools.jib.api.RegistryException;
import com.google.cloud.tools.jib.event.EventHandlers;
import com.google.cloud.tools.jib.http.FailoverHttpClient;
import com.google.cloud.tools.jib.image.json.ManifestTemplate;
import com.google.cloud.tools.jib.image.json.OciIndexTemplate;
import com.google.cloud.tools.jib.image.json.OciManifestTemplate;
import com.google.cloud.tools.jib.image.json.V21ManifestTemplate;
import com.google.cloud.tools.jib.image.json.V22ManifestListTemplate;
import com.google.cloud.tools.jib.image.json.V22ManifestTemplate;
import java.io.IOException;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.jupiter.api.Test;

/** Integration tests for {@link ManifestPuller}. */
public class ManifestPullerIntegrationTest {

  /** A known manifest list sha for gcr.io/distroless/base. */
  public static final String KNOWN_MANIFEST_LIST_SHA =
      "sha256:44cbdb9c24e123882d7894ba78fb6f572d2496889885a47eb4b32241a8c07a00";

  /** A known OCI image index sha for gcr.io/distroless/base. */
  public static final String KNOWN_OCI_INDEX_SHA =
      "sha256:2c50b819aa3bfaf6ae72e47682f6c5abc0f647cf3f4224a4a9be97dd30433909";

  /** A known docker manifest schema 2 sha for gcr.io/distroless/base. */
  public static final String KNOWN_MANIFEST_V22_SHA =
      "sha256:da5c568e59f3241b09e5699a525a37b3309ce2c182d8d20802b9eaee55711b19";

  /** A known oci manifest sha for gcr.io/distroless/base. */
  public static final String KNOWN_OCI_MANIFEST_SHA =
      "sha256:0477dc38b254096e350a9b605b7355d3cf0d5a844558e6986148ce2a1fe18ba8";

  @ClassRule public static LocalRegistry localRegistry = new LocalRegistry(5000);
  public final String dockerHost =
      System.getenv("DOCKER_IP") != null ? System.getenv("DOCKER_IP") : "localhost";

  @BeforeClass
  public static void setUp() throws IOException, InterruptedException {
    localRegistry.pullAndPushToLocal("busybox", "busybox");
  }

  private final FailoverHttpClient httpClient = new FailoverHttpClient(true, false, ignored -> {});

  @Test
  public void testPull_v21() throws IOException, RegistryException {
    RegistryClient registryClient =
        RegistryClient.factory(EventHandlers.NONE, dockerHost + ":5000", "busybox", httpClient)
            .newRegistryClient();

    V21ManifestTemplate manifestTemplate =
        registryClient.pullManifest("latest", V21ManifestTemplate.class).getManifest();

    assertThat(manifestTemplate.getSchemaVersion()).isEqualTo(1);
    assertThat(manifestTemplate.getFsLayers()).isNotEmpty();
  }

  @Test
  public void testPull_v22() throws IOException, RegistryException {
    RegistryClient registryClient =
        RegistryClient.factory(EventHandlers.NONE, "gcr.io", "distroless/base", httpClient)
            .newRegistryClient();

    V22ManifestTemplate manifestTemplate =
        registryClient
            .pullManifest(KNOWN_MANIFEST_V22_SHA, V22ManifestTemplate.class)
            .getManifest();

    assertThat(manifestTemplate.getSchemaVersion()).isEqualTo(2);
    assertThat(manifestTemplate.getLayers()).isNotEmpty();
  }

  @Test
  public void testPull_ociManifest() throws IOException, RegistryException {
    RegistryClient registryClient =
        RegistryClient.factory(EventHandlers.NONE, "gcr.io", "distroless/base", httpClient)
            .newRegistryClient();

    OciManifestTemplate manifestTemplate =
        registryClient
            .pullManifest(KNOWN_OCI_MANIFEST_SHA, OciManifestTemplate.class)
            .getManifest();

    assertThat(manifestTemplate.getSchemaVersion()).isEqualTo(2);
    assertThat(manifestTemplate.getLayers()).isNotEmpty();
  }

  @Test
  public void testPull_v22ManifestList() throws IOException, RegistryException {
    RegistryClient registryClient =
        RegistryClient.factory(EventHandlers.NONE, "gcr.io", "distroless/base", httpClient)
            .newRegistryClient();

    // Ensures call to image at the specified SHA returns a manifest list
    V22ManifestListTemplate manifestListTargeted =
        registryClient
            .pullManifest(KNOWN_MANIFEST_LIST_SHA, V22ManifestListTemplate.class)
            .getManifest();
    assertThat(manifestListTargeted.getSchemaVersion()).isEqualTo(2);
    assertThat(manifestListTargeted.getManifests()).isNotEmpty();

    // Generic call to image at the specified SHA, should also return a manifest list
    ManifestTemplate manifestListGeneric =
        registryClient.pullManifest(KNOWN_MANIFEST_LIST_SHA).getManifest();
    assertThat(manifestListGeneric.getSchemaVersion()).isEqualTo(2);
    assertThat(manifestListGeneric).isInstanceOf(V22ManifestListTemplate.class);
    assertThat(((V22ManifestListTemplate) manifestListGeneric).getManifests()).isNotEmpty();
  }

  @Test
  public void testPull_ociIndex() throws IOException, RegistryException {
    RegistryClient registryClient =
        RegistryClient.factory(EventHandlers.NONE, "gcr.io", "distroless/base", httpClient)
            .newRegistryClient();

    // Ensures call to image at the specified SHA returns an OCI index
    OciIndexTemplate manifestListTargeted =
        registryClient.pullManifest(KNOWN_OCI_INDEX_SHA, OciIndexTemplate.class).getManifest();
    assertThat(manifestListTargeted.getSchemaVersion()).isEqualTo(2);
    assertThat((manifestListTargeted.getManifests().size() > 0)).isTrue();

    // Generic call to image at the specified SHA, should also return an OCI index
    ManifestTemplate manifestListGeneric =
        registryClient.pullManifest(KNOWN_OCI_INDEX_SHA).getManifest();
    assertThat(manifestListGeneric.getSchemaVersion()).isEqualTo(2);
    assertThat(manifestListGeneric).isInstanceOf(OciIndexTemplate.class);
    assertThat(((OciIndexTemplate) manifestListGeneric).getManifests()).isNotEmpty();
  }

  @Test
  public void testPull_unknownManifest() throws RegistryException, IOException {
    try {
      RegistryClient registryClient =
          RegistryClient.factory(EventHandlers.NONE, dockerHost + ":5000", "busybox", httpClient)
              .newRegistryClient();
      registryClient.pullManifest("nonexistent-tag");
      Assert.fail("Trying to pull nonexistent image should have errored");

    } catch (RegistryErrorException ex) {
      assertThat(ex)
          .hasMessageThat()
          .contains("pull image manifest for " + dockerHost + ":5000/busybox:nonexistent-tag");
    }
  }
}
