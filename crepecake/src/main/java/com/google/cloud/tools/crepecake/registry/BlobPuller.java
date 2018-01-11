/*
 * Copyright 2018 Google Inc.
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

import com.google.cloud.tools.crepecake.blob.Blob;
import com.google.cloud.tools.crepecake.blob.BlobDescriptor;
import com.google.cloud.tools.crepecake.blob.Blobs;
import com.google.cloud.tools.crepecake.http.Response;
import com.google.cloud.tools.crepecake.image.DescriptorDigest;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

/** Pulls an image's blob (layer or container configuration). */
class BlobPuller implements RegistryEndpointProvider<Blob> {

  private final RegistryEndpointProperties registryEndpointProperties;
  private final DescriptorDigest blobDigest;
  private final Path destPath;

  BlobPuller(
      RegistryEndpointProperties registryEndpointProperties,
      DescriptorDigest blobDigest,
      Path destPath) {
    this.registryEndpointProperties = registryEndpointProperties;
    this.blobDigest = blobDigest;
    this.destPath = destPath;
  }

  @Override
  public Blob handleResponse(Response response) throws IOException, UnexpectedBlobDigestException {
    try (OutputStream fileOutputStream =
        new BufferedOutputStream(Files.newOutputStream(destPath))) {
      BlobDescriptor receivedBlobDescriptor = response.getBody().writeTo(fileOutputStream);

      if (!blobDigest.equals(receivedBlobDescriptor.getDigest())) {
        throw new UnexpectedBlobDigestException(
            "The pulled BLOB has digest '"
                + receivedBlobDescriptor.getDigest()
                + "', but the request digest was '"
                + blobDigest
                + "'");
      }

      return Blobs.from(destPath);
    }
  }

  @Override
  public URL getApiRoute(String apiRouteBase) throws MalformedURLException {
    return new URL(apiRouteBase + "/blobs/" + blobDigest);
  }

  @Override
  public String getActionDescription() {
    return "pull BLOB for "
        + registryEndpointProperties.getServerUrl()
        + "/"
        + registryEndpointProperties.getImageName()
        + " with digest "
        + blobDigest;
  }
}
