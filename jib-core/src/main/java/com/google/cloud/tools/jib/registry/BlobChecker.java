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
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.HttpStatusCodes;
import com.google.cloud.tools.jib.blob.BlobDescriptor;
import com.google.cloud.tools.jib.http.BlobHttpContent;
import com.google.cloud.tools.jib.http.Response;
import com.google.cloud.tools.jib.image.DescriptorDigest;
import com.google.cloud.tools.jib.json.JsonTemplateMapper;
import com.google.cloud.tools.jib.registry.json.ErrorEntryTemplate;
import com.google.cloud.tools.jib.registry.json.ErrorResponseTemplate;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;

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

  /** @return the BLOB's content descriptor */
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
  @Nullable
  public BlobDescriptor handleHttpResponseException(HttpResponseException httpResponseException)
      throws RegistryErrorException, HttpResponseException {
    if (httpResponseException.getStatusCode() != HttpStatusCodes.STATUS_CODE_NOT_FOUND) {
      throw httpResponseException;
    }

    // Finds a BLOB_UNKNOWN error response code.
    String errorContent = httpResponseException.getContent();
    if (errorContent == null) {
      // TODO: The Google HTTP client gives null content for HEAD requests. Make the content never
      // be null, even for HEAD requests.
      return null;

    } else {
      try {
        ErrorResponseTemplate errorResponse =
            JsonTemplateMapper.readJson(errorContent, ErrorResponseTemplate.class);
        List<ErrorEntryTemplate> errors = errorResponse.getErrors();
        if (errors.size() == 1) {
          String errorCodeString = errors.get(0).getCode();
          if (errorCodeString == null) {
            // Did not get an error code back.
            throw httpResponseException;
          }
          ErrorCodes errorCode = ErrorCodes.valueOf(errorCodeString);
          if (errorCode.equals(ErrorCodes.BLOB_UNKNOWN)) {
            return null;
          }
        }

      } catch (IOException ex) {
        throw new RegistryErrorExceptionBuilder(getActionDescription(), ex)
            .addReason("Failed to parse registry error response body")
            .build();
      }
    }

    // BLOB_UNKNOWN was not found as a error response code.
    throw httpResponseException;
  }

  @Override
  public URL getApiRoute(String apiRouteBase) throws MalformedURLException {
    return new URL(
        apiRouteBase + registryEndpointProperties.getImageName() + "/blobs/" + blobDigest);
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
        + registryEndpointProperties.getServerUrl()
        + "/"
        + registryEndpointProperties.getImageName()
        + " with digest "
        + blobDigest;
  }
}
