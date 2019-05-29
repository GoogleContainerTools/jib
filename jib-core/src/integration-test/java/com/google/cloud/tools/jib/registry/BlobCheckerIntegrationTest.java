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

import com.google.cloud.tools.jib.api.DescriptorDigest;
import com.google.cloud.tools.jib.api.RegistryException;
import com.google.cloud.tools.jib.event.EventHandlers;
import com.google.cloud.tools.jib.image.json.V22ManifestTemplate;
import java.io.IOException;
import java.security.DigestException;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;

/** Integration tests for {@link BlobChecker}. */
public class BlobCheckerIntegrationTest {

  @ClassRule public static LocalRegistry localRegistry = new LocalRegistry(5000);

  @Test
  public void testCheck_exists() throws IOException, RegistryException, InterruptedException {
    localRegistry.pullAndPushToLocal("busybox", "busybox");
    RegistryClient registryClient =
        RegistryClient.factory(EventHandlers.NONE, "localhost:5000", "busybox")
            .setAllowInsecureRegistries(true)
            .newRegistryClient();
    V22ManifestTemplate manifestTemplate =
        registryClient.pullManifest("latest", V22ManifestTemplate.class);
    DescriptorDigest blobDigest = manifestTemplate.getLayers().get(0).getDigest();

    Assert.assertEquals(blobDigest, registryClient.checkBlob(blobDigest).getDigest());
  }

  @Test
  public void testCheck_doesNotExist()
      throws IOException, RegistryException, DigestException, InterruptedException {
    localRegistry.pullAndPushToLocal("busybox", "busybox");
    RegistryClient registryClient =
        RegistryClient.factory(EventHandlers.NONE, "localhost:5000", "busybox")
            .setAllowInsecureRegistries(true)
            .newRegistryClient();
    DescriptorDigest fakeBlobDigest =
        DescriptorDigest.fromHash(
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");

    Assert.assertNull(registryClient.checkBlob(fakeBlobDigest));
  }
}
