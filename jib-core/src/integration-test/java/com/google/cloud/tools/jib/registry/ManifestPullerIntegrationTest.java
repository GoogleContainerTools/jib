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

  /** A known manifest list sha for openjdk:11-jre-slim. */
  public static final String KNOWN_MANIFEST_LIST_SHA =
      "sha256:8ab7b3078b01ba66b937b7fbe0b9eccf60449cc101c42e99aeefaba0e1781155";

  @ClassRule public static LocalRegistry localRegistry = new LocalRegistry(5000);

  @BeforeClass
  public static void setUp() throws IOException, InterruptedException {
    localRegistry.pullAndPushToLocal("busybox", "busybox");
  }

  private final FailoverHttpClient httpClient = new FailoverHttpClient(true, false, ignored -> {});

  @Test
  public void testPull_v21() throws IOException, RegistryException {
    RegistryClient registryClient =
        RegistryClient.factory(EventHandlers.NONE, "localhost:5000", "busybox", httpClient)
            .newRegistryClient();
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
        RegistryClient.factory(
                EventHandlers.NONE, "registry-1.docker.io", "library/openjdk", httpClient)
            .newRegistryClient();
    registryClient.doPullBearerAuth();

    // Ensure 11-jre-slim is a manifest list
    V22ManifestListTemplate manifestListTemplate =
        registryClient.pullManifest("11-jre-slim", V22ManifestListTemplate.class).getManifest();
    Assert.assertEquals(2, manifestListTemplate.getSchemaVersion());
    Assert.assertTrue(manifestListTemplate.getManifests().size() > 0);

    // Generic call to 11-jre-slim pulls a manifest list
    ManifestTemplate manifestTemplate = registryClient.pullManifest("11-jre-slim").getManifest();
    Assert.assertEquals(2, manifestTemplate.getSchemaVersion());
    MatcherAssert.assertThat(
        manifestTemplate, CoreMatchers.instanceOf(V22ManifestListTemplate.class));

    // Make sure we can't cast a v22ManifestTemplate to v22ManifestListTemplate in ManifestPuller
    try {
      registryClient.pullManifest(KNOWN_MANIFEST_LIST_SHA, V22ManifestTemplate.class);
      Assert.fail();
    } catch (ClassCastException ex) {
      // pass
    }

    // Referencing a manifest list by sha256, should return a manifest list
    ManifestTemplate sha256ManifestList =
        registryClient.pullManifest(KNOWN_MANIFEST_LIST_SHA).getManifest();
    Assert.assertEquals(2, sha256ManifestList.getSchemaVersion());
    MatcherAssert.assertThat(
        sha256ManifestList, CoreMatchers.instanceOf(V22ManifestListTemplate.class));
    Assert.assertTrue(((V22ManifestListTemplate) sha256ManifestList).getManifests().size() > 0);
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
