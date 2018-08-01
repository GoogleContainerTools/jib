/*
 * Copyright 2017 Google LLC. All rights reserved.
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
import com.google.cloud.tools.jib.blob.Blob;
import com.google.cloud.tools.jib.blob.Blobs;
import com.google.cloud.tools.jib.image.DescriptorDigest;
import java.io.IOException;
import java.security.DigestException;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;

/** Integration tests for {@link BlobPusher}. */
public class BlobPusherIntegrationTest {

  @ClassRule public static LocalRegistry localRegistry = new LocalRegistry(5000);
  private static final EmptyJibLogger BUILD_LOGGER = new EmptyJibLogger();

  @Test
  public void testPush() throws DigestException, IOException, RegistryException {
    Blob testBlob = Blobs.from("crepecake");
    // Known digest for 'crepecake'
    DescriptorDigest testBlobDigest =
        DescriptorDigest.fromHash(
            "52a9e4d4ba4333ce593707f98564fee1e6d898db0d3602408c0b2a6a424d357c");

    RegistryClient registryClient =
        RegistryClient.factory(BUILD_LOGGER, "localhost:5000", "testimage")
            .setAllowInsecureRegistries(true)
            .newRegistryClient();
    Assert.assertFalse(registryClient.pushBlob(testBlobDigest, testBlob, null));
  }
}
