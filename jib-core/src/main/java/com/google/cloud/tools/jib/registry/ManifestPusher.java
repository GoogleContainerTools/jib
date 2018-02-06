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

package com.google.cloud.tools.jib.registry;

import com.google.api.client.http.HttpMethods;
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.HttpStatusCodes;
import com.google.cloud.tools.jib.http.BlobHttpContent;
import com.google.cloud.tools.jib.http.Response;
import com.google.cloud.tools.jib.image.json.V22ManifestTemplate;
import com.google.cloud.tools.jib.json.JsonTemplateMapper;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.List;

/** Pushes an image's manifest. */
class ManifestPusher implements RegistryEndpointProvider<Void> {

  private final RegistryEndpointProperties registryEndpointProperties;
  private final V22ManifestTemplate manifestTemplate;
  private final String imageTag;

  ManifestPusher(
      RegistryEndpointProperties registryEndpointProperties,
      V22ManifestTemplate manifestTemplate,
      String imageTag) {
    this.registryEndpointProperties = registryEndpointProperties;
    this.manifestTemplate = manifestTemplate;
    this.imageTag = imageTag;
  }

  @Override
  public BlobHttpContent getContent() {
    return new BlobHttpContent(
        JsonTemplateMapper.toBlob(manifestTemplate), V22ManifestTemplate.MEDIA_TYPE);
  }

  @Override
  public List<String> getAccept() {
    return Collections.emptyList();
  }

  @Override
  public Void handleResponse(Response response) {
    return null;
  }

  @Override
  public Void handleHttpResponseException(HttpResponseException httpResponseException)
      throws HttpResponseException, RegistryErrorException {
    if (httpResponseException.getStatusCode() == HttpStatusCodes.STATUS_CODE_NOT_FOUND) {
      /*
       * This error could happen if the repository doesn't exist (which should fail at BLOB-push).
       * The more likely reason is that the image is pushing to a tag with backslashes in it, in
       * which part of the tag becomes part of the repository.
       */
      throw new RegistryErrorExceptionBuilder(getActionDescription(), httpResponseException)
          .addReason(
              "repository name not known to registry (make sure that you are using a valid tag - tags cannot contain backslashes)")
          .build();
    }

    throw httpResponseException;
  }

  @Override
  public URL getApiRoute(String apiRouteBase) throws MalformedURLException {
    return new URL(
        apiRouteBase + registryEndpointProperties.getImageName() + "/manifests/" + imageTag);
  }

  @Override
  public String getHttpMethod() {
    return HttpMethods.PUT;
  }

  @Override
  public String getActionDescription() {
    return "push image manifest for "
        + registryEndpointProperties.getServerUrl()
        + "/"
        + registryEndpointProperties.getImageName()
        + ":"
        + imageTag;
  }
}
