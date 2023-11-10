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

import com.google.cloud.tools.jib.api.DescriptorDigest;
import com.google.cloud.tools.jib.api.RegistryException;
import com.google.cloud.tools.jib.blob.Blob;
import com.google.cloud.tools.jib.blob.Blobs;
import com.google.cloud.tools.jib.event.EventHandlers;
import com.google.cloud.tools.jib.http.FailoverHttpClient;
import java.io.IOException;
import java.security.DigestException;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;

/** Integration tests for {@link BlobPusher}. */
public class BlobPusherIntegrationTest {

  @ClassRule public static final LocalRegistry localRegistry = new LocalRegistry(5001);

  private final FailoverHttpClient httpClient = new FailoverHttpClient(true, false, ignored -> {});
  private final String dockerHost =
      System.getenv("DOCKER_IP") != null ? System.getenv("DOCKER_IP") : "localhost";

  @Test
  public void testPush() throws DigestException, IOException, RegistryException {
    Blob testBlob = Blobs.from("crepecake");
    // Known digest for 'crepecake'
    DescriptorDigest testBlobDigest =
        DescriptorDigest.fromHash(
            "52a9e4d4ba4333ce593707f98564fee1e6d898db0d3602408c0b2a6a424d357c");

    RegistryClient registryClient =
        RegistryClient.factory(EventHandlers.NONE, dockerHost + ":5001", "testimage", httpClient)
            .newRegistryClient();
    Assert.assertFalse(registryClient.pushBlob(testBlobDigest, testBlob, null, ignored -> {}));
  }
}
