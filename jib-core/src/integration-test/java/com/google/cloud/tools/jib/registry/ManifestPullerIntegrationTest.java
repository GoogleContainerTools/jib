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

import com.google.cloud.tools.jib.api.RegistryException;
import com.google.cloud.tools.jib.event.EventHandlers;
import com.google.cloud.tools.jib.http.FailoverHttpClient;
import com.google.cloud.tools.jib.image.json.ManifestTemplate;
import com.google.cloud.tools.jib.image.json.V21ManifestTemplate;
import com.google.cloud.tools.jib.image.json.V22ManifestListTemplate;
import com.google.cloud.tools.jib.image.json.V22ManifestTemplate;
import java.io.IOException;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

/** Integration tests for {@link ManifestPuller}. */
public class ManifestPullerIntegrationTest {

  /** A known manifest list sha for gcr.io/distroless/base. */
  public static final String KNOWN_MANIFEST_LIST_SHA =
      "sha256:44cbdb9c24e123882d7894ba78fb6f572d2496889885a47eb4b32241a8c07a00";

  @ClassRule public static LocalRegistry localRegistry = new LocalRegistry(5000);

  @BeforeClass
  public static void setUp() throws IOException, InterruptedException {
    localRegistry.pullAndPushToLocal("mirror.gcr.io/library/busybox", "busybox");
  }

  private final FailoverHttpClient httpClient = new FailoverHttpClient(true, false, ignored -> {});

  @Test
  public void testPull_v21() throws IOException, RegistryException {
    RegistryClient registryClient =
        RegistryClient.factory(EventHandlers.NONE, "localhost:5000", "busybox", httpClient)
            .newRegistryClient();
    registryClient.doPullBearerAuth();

    V21ManifestTemplate manifestTemplate =
        registryClient.pullManifest("latest", V21ManifestTemplate.class).getManifest();

    Assert.assertEquals(1, manifestTemplate.getSchemaVersion());
    Assert.assertTrue(manifestTemplate.getFsLayers().size() > 0);
  }

  @Test
  public void testPull_v22() throws IOException, RegistryException {
    RegistryClient registryClient =
        RegistryClient.factory(EventHandlers.NONE, "gcr.io", "distroless/java", httpClient)
            .newRegistryClient();
    ManifestTemplate manifestTemplate = registryClient.pullManifest("latest").getManifest();

    Assert.assertEquals(2, manifestTemplate.getSchemaVersion());
    V22ManifestTemplate v22ManifestTemplate = (V22ManifestTemplate) manifestTemplate;
    Assert.assertTrue(v22ManifestTemplate.getLayers().size() > 0);
  }

  @Test
  public void testPull_v22ManifestList() throws IOException, RegistryException {
    RegistryClient registryClient =
        RegistryClient.factory(EventHandlers.NONE, "gcr.io", "distroless/base", httpClient)
            .newRegistryClient();

    // Ensure ":latest" is a manifest list
    V22ManifestListTemplate manifestList1 =
        registryClient.pullManifest("latest", V22ManifestListTemplate.class).getManifest();
    Assert.assertEquals(2, manifestList1.getSchemaVersion());
    Assert.assertTrue(manifestList1.getManifests().size() > 0);

    // Generic call to ":latest" pulls a manifest list
    ManifestTemplate manifestList2 = registryClient.pullManifest("latest").getManifest();
    Assert.assertEquals(2, manifestList2.getSchemaVersion());
    MatcherAssert.assertThat(manifestList2, CoreMatchers.instanceOf(V22ManifestListTemplate.class));
    Assert.assertTrue(((V22ManifestListTemplate) manifestList2).getManifests().size() > 0);

    // Referencing a manifest list by sha256, should return a manifest list
    ManifestTemplate manifestList3 =
        registryClient.pullManifest(KNOWN_MANIFEST_LIST_SHA).getManifest();
    Assert.assertEquals(2, manifestList3.getSchemaVersion());
    MatcherAssert.assertThat(manifestList3, CoreMatchers.instanceOf(V22ManifestListTemplate.class));
    Assert.assertTrue(((V22ManifestListTemplate) manifestList3).getManifests().size() > 0);

    // Call to ":latest" targeting a manifest pulls a manifest.
    V22ManifestTemplate manifest =
        registryClient.pullManifest("latest", V22ManifestTemplate.class).getManifest();
    Assert.assertEquals(2, manifest.getSchemaVersion());
  }

  @Test
  public void testPull_unknownManifest() throws RegistryException, IOException {
    try {
      RegistryClient registryClient =
          RegistryClient.factory(EventHandlers.NONE, "localhost:5000", "busybox", httpClient)
              .newRegistryClient();
      registryClient.pullManifest("nonexistent-tag");
      Assert.fail("Trying to pull nonexistent image should have errored");

    } catch (RegistryErrorException ex) {
      MatcherAssert.assertThat(
          ex.getMessage(),
          CoreMatchers.containsString(
              "pull image manifest for localhost:5000/busybox:nonexistent-tag"));
    }
  }
}
