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

import com.google.api.client.http.HttpMethods;
import com.google.api.client.http.HttpStatusCodes;
import com.google.cloud.tools.jib.api.DescriptorDigest;
import com.google.cloud.tools.jib.blob.BlobDescriptor;
import com.google.cloud.tools.jib.http.BlobHttpContent;
import com.google.cloud.tools.jib.http.Response;
import com.google.cloud.tools.jib.http.ResponseException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * Checks if an image's BLOB exists on a registry, and retrieves its {@link BlobDescriptor} if it
 * exists.
 */
class BlobChecker implements RegistryEndpointProvider<Optional<BlobDescriptor>> {

  private final RegistryEndpointRequestProperties registryEndpointRequestProperties;
  private final DescriptorDigest blobDigest;

  BlobChecker(
      RegistryEndpointRequestProperties registryEndpointRequestProperties,
      DescriptorDigest blobDigest) {
    this.registryEndpointRequestProperties = registryEndpointRequestProperties;
    this.blobDigest = blobDigest;
  }

  /** Returns the BLOB's content descriptor. */
  @Override
  public Optional<BlobDescriptor> handleResponse(Response response) throws RegistryErrorException {
    long contentLength = response.getContentLength();
    if (contentLength < 0) {
      throw new RegistryErrorExceptionBuilder(getActionDescription())
          .addReason("Did not receive Content-Length header")
          .build();
    }

    return Optional.of(new BlobDescriptor(contentLength, blobDigest));
  }

  @Override
  public Optional<BlobDescriptor> handleHttpResponseException(ResponseException responseException)
      throws ResponseException {
    if (responseException.getStatusCode() != HttpStatusCodes.STATUS_CODE_NOT_FOUND) {
      throw responseException;
    }

    if (responseException.getContent() == null) {
      // TODO: The Google HTTP client gives null content for HEAD requests. Make the content never
      // be null, even for HEAD requests.
      return Optional.empty();
    }

    // Find a BLOB_UNKNOWN error response code.
    ErrorCodes errorCode = ErrorResponseUtil.getErrorCode(responseException);
    if (errorCode == ErrorCodes.BLOB_UNKNOWN) {
      return Optional.empty();
    }

    // BLOB_UNKNOWN was not found as a error response code.
    throw responseException;
  }

  @Override
  public URL getApiRoute(String apiRouteBase) throws MalformedURLException {
    return new URL(
        apiRouteBase + registryEndpointRequestProperties.getImageName() + "/blobs/" + blobDigest);
  }

  @Nullable
  @Override
  public BlobHttpContent getContent() {
    return null;
  }

  @Override
  public List<String> getAccept() {
    return Collections.emptyList();
  }

  @Override
  public String getHttpMethod() {
    return HttpMethods.HEAD;
  }

  @Override
  public String getActionDescription() {
    return "check BLOB exists for "
        + registryEndpointRequestProperties.getServerUrl()
        + "/"
        + registryEndpointRequestProperties.getImageName()
        + " with digest "
        + blobDigest;
  }
}
