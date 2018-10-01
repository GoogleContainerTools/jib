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

import com.google.api.client.http.HttpMethods;
import com.google.api.client.http.HttpResponseException;
import com.google.cloud.tools.jib.http.BlobHttpContent;
import com.google.cloud.tools.jib.http.Response;
import com.google.cloud.tools.jib.image.DescriptorDigest;
import com.google.cloud.tools.jib.image.json.BuildableManifestTemplate;
import com.google.cloud.tools.jib.json.JsonTemplateMapper;
import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.DigestException;
import java.util.Collections;
import java.util.List;
import org.apache.http.HttpStatus;

/** Pushes an image's manifest. */
class ManifestPusher implements RegistryEndpointProvider<DescriptorDigest> {

  /** Response header containing digest of pushed image. */
  private static final String RESPONSE_DIGEST_HEADER = "Docker-Content-Digest";

  private final RegistryEndpointRequestProperties registryEndpointRequestProperties;
  private final BuildableManifestTemplate manifestTemplate;
  private final String imageTag;

  ManifestPusher(
      RegistryEndpointRequestProperties registryEndpointRequestProperties,
      BuildableManifestTemplate manifestTemplate,
      String imageTag) {
    this.registryEndpointRequestProperties = registryEndpointRequestProperties;
    this.manifestTemplate = manifestTemplate;
    this.imageTag = imageTag;
  }

  @Override
  public BlobHttpContent getContent() {
    return new BlobHttpContent(
        JsonTemplateMapper.toBlob(manifestTemplate), manifestTemplate.getManifestMediaType());
  }

  @Override
  public List<String> getAccept() {
    return Collections.emptyList();
  }

  @Override
  public DescriptorDigest handleHttpResponseException(HttpResponseException httpResponseException)
      throws HttpResponseException, RegistryErrorException {
    // docker registry 2.0 and 2.1 returns:
    //   400 Bad Request
    //   {"errors":[{"code":"TAG_INVALID","message":"manifest tag did not match URI"}]}
    // docker registry:2.2 returns:
    //   400 Bad Request
    //   {"errors":[{"code":"MANIFEST_INVALID","message":"manifest invalid","detail":{}}]}
    // quay.io returns:
    //   415 UNSUPPORTED MEDIA TYPE
    //   {"errors":[{"code":"MANIFEST_INVALID","detail":
    //   {"message":"manifest schema version not supported"},"message":"manifest invalid"}]}

    if (httpResponseException.getStatusCode() != HttpStatus.SC_BAD_REQUEST
        && httpResponseException.getStatusCode() != HttpStatus.SC_UNSUPPORTED_MEDIA_TYPE) {
      throw httpResponseException;
    }

    ErrorCodes errorCode = ErrorResponseUtil.getErrorCode(httpResponseException);
    if (errorCode == ErrorCodes.MANIFEST_INVALID || errorCode == ErrorCodes.TAG_INVALID) {
      throw new RegistryErrorExceptionBuilder(getActionDescription(), httpResponseException)
          .addReason("Registry may not support Image Manifest Version 2, Schema 2")
          .build();
    }
    // rethrow: unhandled error response code.
    throw httpResponseException;
  }

  @Override
  public DescriptorDigest handleResponse(Response response)
      throws IOException, UnexpectedImageDigestException {
    // Checks if the image digest is as expected.
    DescriptorDigest expectedDigest =
        JsonTemplateMapper.toBlob(manifestTemplate)
            .writeTo(ByteStreams.nullOutputStream())
            .getDigest();

    List<String> receivedDigests = response.getHeader(RESPONSE_DIGEST_HEADER);
    if (receivedDigests.size() == 1) {
      try {
        DescriptorDigest receivedDigest = DescriptorDigest.fromDigest(receivedDigests.get(0));
        if (expectedDigest.equals(receivedDigest)) {
          return expectedDigest;
        }

      } catch (DigestException ex) {
        // Invalid digest.
      }
    }

    throw new UnexpectedImageDigestException(expectedDigest, receivedDigests);
  }

  @Override
  public URL getApiRoute(String apiRouteBase) throws MalformedURLException {
    return new URL(
        apiRouteBase + registryEndpointRequestProperties.getImageName() + "/manifests/" + imageTag);
  }

  @Override
  public String getHttpMethod() {
    return HttpMethods.PUT;
  }

  @Override
  public String getActionDescription() {
    return "push image manifest for "
        + registryEndpointRequestProperties.getServerUrl()
        + "/"
        + registryEndpointRequestProperties.getImageName()
        + ":"
        + imageTag;
  }
}
