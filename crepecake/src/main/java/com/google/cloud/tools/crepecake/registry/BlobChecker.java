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
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.HttpStatusCodes;
import com.google.cloud.tools.crepecake.blob.BlobDescriptor;
import com.google.cloud.tools.crepecake.http.Request;
import com.google.cloud.tools.crepecake.http.Response;
import com.google.cloud.tools.crepecake.image.DescriptorDigest;
import com.google.cloud.tools.crepecake.json.JsonTemplateMapper;
import com.google.cloud.tools.crepecake.registry.json.ErrorEntryTemplate;
import com.google.cloud.tools.crepecake.registry.json.ErrorResponseTemplate;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

/**
 * Checks if an image's BLOB exists on a registry, and retrieves its {@link BlobDescriptor} if it
 * exists.
 */
class BlobChecker implements RegistryEndpointProvider<BlobDescriptor> {

  private final RegistryEndpointProperties registryEndpointProperties;
  private final DescriptorDigest blobDigest;

  BlobChecker(RegistryEndpointProperties registryEndpointProperties, DescriptorDigest blobDigest) {
    this.registryEndpointProperties = registryEndpointProperties;
    this.blobDigest = blobDigest;
  }

  @Override
  public void buildRequest(Request.Builder builder) {}

  /** @return the BLOB's size, if it exists, or {@code null} if it doesn't */
  @Override
  public BlobDescriptor handleResponse(Response response) throws RegistryErrorException {
    long contentLength = response.getContentLength();
    if (contentLength < 0) {
      throw new RegistryErrorExceptionBuilder(getActionDescription())
          .addReason("Did not receive Content-Length header")
          .build();
    }

    return new BlobDescriptor(contentLength, blobDigest);
  }

  @Override
  public BlobDescriptor handleHttpResponseException(HttpResponseException ex) throws IOException {
    if (ex.getStatusCode() != HttpStatusCodes.STATUS_CODE_NOT_FOUND) {
      throw ex;
    }

    // Finds a BLOB_UNKNOWN error response code.
    String errorContent = ex.getContent();
    if (errorContent == null) {
      // TODO: The Google HTTP client gives null content for HEAD requests. Make the content never be null, even for HEAD requests.
      return null;
    } else {
      ErrorResponseTemplate errorResponse =
          JsonTemplateMapper.readJson(errorContent, ErrorResponseTemplate.class);
      List<ErrorEntryTemplate> errors = errorResponse.getErrors();
      if (errors.size() == 1) {
        ErrorCodes errorCode = ErrorCodes.valueOf(errors.get(0).getCode());
        if (errorCode.equals(ErrorCodes.BLOB_UNKNOWN)) {
          return null;
        }
      }
    }

    // BLOB_UNKNOWN was not found as a error response code.
    throw ex;
  }

  @Override
  public URL getApiRoute(String apiRouteBase) throws MalformedURLException {
    return new URL(
        apiRouteBase + registryEndpointProperties.getImageName() + "/blobs/" + blobDigest);
  }

  @Override
  public String getHttpMethod() {
    return HttpMethods.HEAD;
  }

  @Override
  public String getActionDescription() {
    return "check BLOB exists for "
        + registryEndpointProperties.getServerUrl()
        + "/"
        + registryEndpointProperties.getImageName()
        + " with digest "
        + blobDigest;
  }
}
