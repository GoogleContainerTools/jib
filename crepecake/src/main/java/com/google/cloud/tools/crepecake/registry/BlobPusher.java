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

import com.google.api.client.http.HttpMethods;
import com.google.api.client.http.HttpStatusCodes;
import com.google.cloud.tools.crepecake.blob.Blob;
import com.google.cloud.tools.crepecake.http.Request;
import com.google.cloud.tools.crepecake.http.Response;
import com.google.cloud.tools.crepecake.image.DescriptorDigest;
import com.google.common.net.MediaType;
import java.net.HttpURLConnection;
import java.util.List;
import javax.annotation.Nullable;

/** Pushes an image's blob (layer or container configuration). */
class BlobPusher {

  private final DescriptorDigest blobDigest;
  private final Blob blob;

  private class Initializer implements RegistryEndpointProvider<String> {

    @Override
    public void buildRequest(Request.Builder builder) {}

    /**
     * @return a URL to continue pushing the BLOB to, or {@code null} if the BLOB already exists on
     *     the registry
     */
    @Nullable
    @Override
    public String handleResponse(Response response) throws RegistryErrorException {
      switch (response.getStatusCode()) {
        case HttpStatusCodes.STATUS_CODE_CREATED:
          // The BLOB exists in the registry.
          return null;

        case HttpURLConnection.HTTP_ACCEPTED:
          return extractLocationHeader(response);

        default:
          throw buildRegistryErrorException(
              "Received unrecognized status code " + response.getStatusCode());
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

  private class Writer implements RegistryEndpointProvider<String> {

    @Override
    public void buildRequest(Request.Builder builder) {
      builder.setContentType(MediaType.OCTET_STREAM.toString());
      builder.setBody(blob);
    }

    /**
     * @return a URL to continue pushing the BLOB to, or {@code null} if the BLOB already exists on
     *     the registry
     */
    @Override
    public String handleResponse(Response response) throws RegistryException {
      // TODO: Handle 204 No Content
      return extractLocationHeader(response);
    }

    @Override
    public String getApiRouteSuffix() {
      return null;
    }

    @Override
    public String getHttpMethod() {
      return HttpMethods.PATCH;
    }

    @Override
    public String getActionDescription(String serverUrl, String imageName) {
      return BlobPusher.this.getActionDescription(serverUrl, imageName);
    }
  }

  private class Committer implements RegistryEndpointProvider<Void> {

    @Override
    public void buildRequest(Request.Builder builder) {}

    @Override
    public Void handleResponse(Response response) {
      return null;
    }

    @Override
    public String getApiRouteSuffix() {
      return null;
    }

    @Override
    public String getHttpMethod() {
      return null;
    }

    @Override
    public String getActionDescription(String serverUrl, String imageName) {
      return null;
    }
  }

  BlobPusher(DescriptorDigest blobDigest, Blob blob) {
    this.blobDigest = blobDigest;
    this.blob = blob;
  }

  /** */
  RegistryEndpointProvider initializer() {
    return new Initializer();
  }

  /** */
  RegistryEndpointProvider writer() {
    return new Writer();
  }

  /** */
  RegistryEndpointProvider committer() {
    return new Committer();
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

  private String extractLocationHeader(Response response) throws RegistryErrorException {
    // Extracts and returns the 'Location' header.
    List<String> locationHeaders = response.getHeader("Location");
    if (locationHeaders.size() != 1) {
      throw buildRegistryErrorException(
          "Expected 1 'Location' header, but found " + locationHeaders.size());
    }

    return locationHeaders.get(0);
  }
}
