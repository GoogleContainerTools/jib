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

import com.google.api.client.http.HttpMethods;
import com.google.api.client.http.HttpStatusCodes;
import com.google.cloud.tools.crepecake.blob.Blob;
import com.google.cloud.tools.crepecake.http.Request;
import com.google.cloud.tools.crepecake.http.Response;
import com.google.cloud.tools.crepecake.image.DescriptorDigest;

import javax.annotation.Nullable;
import java.net.HttpURLConnection;
import java.util.List;

/** Pushes an image's blob (layer or container configuration). */
class BlobPusher {

  private final DescriptorDigest blobDigest;
  private final Blob blob;

  private class Initializer implements RegistryEndpointProvider {

    @Override
    public void buildRequest(Request.Builder builder) {}

    /** @return a URL to continue pushing the BLOB to, or {@code null} if the BLOB already exists on the registry */
    @Nullable
    @Override
    public String handleResponse(Response response) throws RegistryErrorException {
      switch (response.getStatusCode()) {
        case HttpStatusCodes.STATUS_CODE_CREATED:
          // The BLOB exists in the registry.
          return null;

        case HttpURLConnection.HTTP_ACCEPTED:
          // Extracts and returns the 'Location' header.
          List<String> locationHeaders = response.getHeader("Location");
          if (locationHeaders.size() != 1) {
            throw buildRegistryErrorException("Expected 1 'Location' header, but found " + locationHeaders.size());
          }

          return response.getHeader("Location").get(0);

        default:
          throw buildRegistryErrorException("Received unrecognized status code " + response.getStatusCode());
      }
    }

    @Override
    public String getApiRouteSuffix() {
      return "/blobs/uploads/?mount=" + blobDigest;
    }

    @Override
    public String getHttpMethod() {
      return HttpMethods.POST;
    }

    @Override
    public String getActionDescription(String serverUrl, String imageName) {
      return BlobPusher.this.getActionDescription(serverUrl, imageName);
    }
  }

  BlobPusher(DescriptorDigest blobDigest, Blob blob) {
    this.blobDigest = blobDigest;
    this.blob = blob;
  }

  RegistryEndpointProvider initializer() {
    return new Initializer();
  }

  private RegistryErrorException buildRegistryErrorException(String reason) {
    RegistryErrorExceptionBuilder registryErrorExceptionBuilder =
        // TODO: Qualify the action description
        new RegistryErrorExceptionBuilder(getActionDescription("", ""));
    registryErrorExceptionBuilder.addReason(reason);
    return registryErrorExceptionBuilder.build();
  }

  private String getActionDescription(String serverUrl, String imageName) {
    return "push BLOB for " + serverUrl + "/" + imageName + " with digest " + blobDigest;
  }
}
