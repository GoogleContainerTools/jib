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
import com.google.cloud.tools.jib.api.DescriptorDigest;
import com.google.cloud.tools.jib.api.LogEvent;
import com.google.cloud.tools.jib.blob.Blobs;
import com.google.cloud.tools.jib.event.EventHandlers;
import com.google.cloud.tools.jib.hash.Digests;
import com.google.cloud.tools.jib.http.BlobHttpContent;
import com.google.cloud.tools.jib.http.Response;
import com.google.cloud.tools.jib.http.ResponseException;
import com.google.cloud.tools.jib.image.json.ManifestTemplate;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.DigestException;
import java.util.Collections;
import java.util.List;
import java.util.StringJoiner;
import org.apache.http.HttpStatus;

/** Pushes an image's manifest. */
class ManifestPusher implements RegistryEndpointProvider<DescriptorDigest> {

  /** Response header containing digest of pushed image. */
  private static final String RESPONSE_DIGEST_HEADER = "Docker-Content-Digest";

  /**
   * Makes the warning for when the registry responds with an image digest that is not the expected
   * digest of the image.
   *
   * @param expectedDigest the expected image digest
   * @param receivedDigests the received image digests
   * @return the warning message
   */
  private static String makeUnexpectedImageDigestWarning(
      DescriptorDigest expectedDigest, List<String> receivedDigests) {
    if (receivedDigests.isEmpty()) {
      return "Expected image digest " + expectedDigest + ", but received none";
    }

    StringJoiner message =
        new StringJoiner(", ", "Expected image digest " + expectedDigest + ", but received: ", "");
    for (String receivedDigest : receivedDigests) {
      message.add(receivedDigest);
    }
    return message.toString();
  }

  private final RegistryEndpointRequestProperties registryEndpointRequestProperties;
  private final ManifestTemplate manifestTemplate;
  private final String imageTag;
  private final EventHandlers eventHandlers;

  ManifestPusher(
      RegistryEndpointRequestProperties registryEndpointRequestProperties,
      ManifestTemplate manifestTemplate,
      String imageTag,
      EventHandlers eventHandlers) {
    this.registryEndpointRequestProperties = registryEndpointRequestProperties;
    this.manifestTemplate = manifestTemplate;
    this.imageTag = imageTag;
    this.eventHandlers = eventHandlers;
  }

  @Override
  public BlobHttpContent getContent() {
    // TODO: Consider giving progress on manifest push as well?
    return new BlobHttpContent(
        Blobs.from(manifestTemplate), manifestTemplate.getManifestMediaType());
  }

  @Override
  public List<String> getAccept() {
    return Collections.emptyList();
  }

  @Override
  public DescriptorDigest handleHttpResponseException(ResponseException responseException)
      throws ResponseException, RegistryErrorException {
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

    if (responseException.getStatusCode() != HttpStatus.SC_BAD_REQUEST
        && responseException.getStatusCode() != HttpStatus.SC_UNSUPPORTED_MEDIA_TYPE) {
      throw responseException;
    }

    ErrorCodes errorCode = ErrorResponseUtil.getErrorCode(responseException);
    if (errorCode == ErrorCodes.MANIFEST_INVALID || errorCode == ErrorCodes.TAG_INVALID) {
      throw new RegistryErrorExceptionBuilder(getActionDescription(), responseException)
          .addReason(
              "Registry may not support pushing OCI Manifest or "
                  + "Docker Image Manifest Version 2, Schema 2")
          .build();
    }
    // rethrow: unhandled error response code.
    throw responseException;
  }

  @Override
  public DescriptorDigest handleResponse(Response response) throws IOException {
    // Checks if the image digest is as expected.
    DescriptorDigest expectedDigest = Digests.computeJsonDigest(manifestTemplate);

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

    // The received digest is not as expected. Warns about this.
    eventHandlers.dispatch(
        LogEvent.warn(makeUnexpectedImageDigestWarning(expectedDigest, receivedDigests)));
    return expectedDigest;
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
