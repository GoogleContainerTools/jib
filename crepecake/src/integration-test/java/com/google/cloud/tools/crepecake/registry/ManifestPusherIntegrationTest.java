/*
 * Copyright 2017 Google Inc.
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

package com.google.cloud.tools.crepecake.registry;

import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.HttpStatusCodes;
import com.google.cloud.tools.crepecake.image.json.ManifestTemplate;
import com.google.cloud.tools.crepecake.image.json.V22ManifestTemplate;
import java.io.IOException;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;

/** Integration tests for {@link ManifestPusher}. */
public class ManifestPusherIntegrationTest {

  @ClassRule public static LocalRegistry localRegistry = new LocalRegistry(5000);

  @Test
  public void testPush() throws IOException, RegistryException {
    RegistryClient registryClient = new RegistryClient(null, "gcr.io", "distroless/java");
    ManifestTemplate manifestTemplate = registryClient.pullManifest("latest");

    registryClient = new RegistryClient(null, "localhost:5000", "busybox");
    try {
      registryClient.pushManifest((V22ManifestTemplate) manifestTemplate, "latest");
      Assert.fail("Pushing manifest without its BLOBs should fail");

    } catch (RegistryErrorException ex) {
      HttpResponseException httpResponseException = (HttpResponseException) ex.getCause();
      Assert.assertEquals(
          HttpStatusCodes.STATUS_CODE_BAD_REQUEST, httpResponseException.getStatusCode());
    }
  }

  // TODO: Add test to push valid manifest after BLOB-pushing is implemented
}
