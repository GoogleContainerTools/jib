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

package com.google.cloud.tools.jib.registry;

import com.google.api.client.http.HttpMethods;
import com.google.cloud.tools.jib.blob.BlobDescriptor;
import com.google.cloud.tools.jib.http.BlobHttpContent;
import com.google.cloud.tools.jib.http.Response;
import com.google.cloud.tools.jib.image.DescriptorDigest;
import java.io.IOException;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;

/** Pulls an image's BLOB (layer or container configuration). */
class BlobPuller implements RegistryEndpointProvider<Void> {

  private final RegistryEndpointProperties registryEndpointProperties;

  /** The digest of the BLOB to pull. */
  private final DescriptorDigest blobDigest;

  /**
   * The {@link OutputStream} to write the BLOB to. Closes the {@link OutputStream} after writing.
   */
  private final OutputStream destinationOutputStream;

  BlobPuller(
      RegistryEndpointProperties registryEndpointProperties,
      DescriptorDigest blobDigest,
      OutputStream destinationOutputStream) {
    this.registryEndpointProperties = registryEndpointProperties;
    this.blobDigest = blobDigest;
    this.destinationOutputStream = destinationOutputStream;
  }

  @Override
  public Void handleResponse(Response response) throws IOException, UnexpectedBlobDigestException {
    try (OutputStream outputStream = destinationOutputStream) {
      BlobDescriptor receivedBlobDescriptor = response.getBody().writeTo(outputStream);

      if (!blobDigest.equals(receivedBlobDescriptor.getDigest())) {
        throw new UnexpectedBlobDigestException(
            "The pulled BLOB has digest '"
                + receivedBlobDescriptor.getDigest()
                + "', but the request digest was '"
                + blobDigest
                + "'");
      }
    }

    return null;
  }

  @Override
  @Nullable
  public BlobHttpContent getContent() {
    return null;
  }

  @Override
  public List<String> getAccept() {
    return Collections.emptyList();
  }

  @Override
  public URL getApiRoute(String apiRouteBase) throws MalformedURLException {
    return new URL(
        apiRouteBase + registryEndpointProperties.getImageName() + "/blobs/" + blobDigest);
  }

  @Override
  public String getHttpMethod() {
    return HttpMethods.GET;
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
