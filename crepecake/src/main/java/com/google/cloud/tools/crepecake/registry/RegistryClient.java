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

import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.HttpStatusCodes;
import com.google.cloud.tools.crepecake.blob.Blob;
import com.google.cloud.tools.crepecake.http.Authorization;
import com.google.cloud.tools.crepecake.http.Connection;
import com.google.cloud.tools.crepecake.http.Request;
import com.google.cloud.tools.crepecake.http.Response;
import com.google.cloud.tools.crepecake.image.DescriptorDigest;
import com.google.cloud.tools.crepecake.image.json.ManifestTemplate;
import com.google.cloud.tools.crepecake.image.json.V22ManifestTemplate;
import com.google.cloud.tools.crepecake.json.JsonTemplateMapper;
import com.google.cloud.tools.crepecake.registry.json.ErrorEntryTemplate;
import com.google.cloud.tools.crepecake.registry.json.ErrorResponseTemplate;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Path;
import javax.annotation.Nullable;

/** Interfaces with a registry. */
public class RegistryClient {

  // TODO: This should be configurable.
  private static final String PROTOCOL = "http";

  @Nullable private final Authorization authorization;
  private final RegistryEndpointProperties registryEndpointProperties;

  public RegistryClient(@Nullable Authorization authorization, String serverUrl, String imageName) {
    this.authorization = authorization;
    this.registryEndpointProperties = new RegistryEndpointProperties(serverUrl, imageName);
  }

  /** Pulls the image manifest for a specific tag. */
  public ManifestTemplate pullManifest(String imageTag) throws IOException, RegistryException {
    ManifestPuller manifestPuller = new ManifestPuller(registryEndpointProperties, imageTag);
    return callRegistryEndpoint(null, manifestPuller);
  }

  /** Pushes the image manifest for a specific tag. */
  public void pushManifest(V22ManifestTemplate manifestTemplate, String imageTag)
      throws IOException, RegistryException {
    ManifestPusher manifestPusher =
        new ManifestPusher(registryEndpointProperties, manifestTemplate, imageTag);
    callRegistryEndpoint(null, manifestPusher);
  }

  /**
   * Downloads the BLOB to a file.
   *
   * @param blobDigest the digest of the BLOB to download
   * @param destPath the path of the file to write to
   * @return a {@link Blob} backed by the file at {@code destPath}. The file at {@code destPath}
   *     must exist for {@link Blob} to be valid.
   */
  public Blob pullBlob(DescriptorDigest blobDigest, Path destPath)
      throws RegistryException, IOException {
    BlobPuller blobPuller = new BlobPuller(registryEndpointProperties, blobDigest, destPath);
    return callRegistryEndpoint(null, blobPuller);
  }

  private String getApiRouteBase() {
    return PROTOCOL
        + "://"
        + registryEndpointProperties.getServerUrl()
        + "/v2/"
        + registryEndpointProperties.getImageName();
  }

  /**
   * Calls the registry endpoint.
   *
   * @param url the endpoint URL to call, or {@code null} to use default from {@code
   *     registryEndpointProvider}
   * @param registryEndpointProvider the {@link RegistryEndpointProvider} to the endpoint
   */
  private <T> T callRegistryEndpoint(
      @Nullable URL url, RegistryEndpointProvider<T> registryEndpointProvider)
      throws IOException, RegistryException {
    if (url == null) {
      url = registryEndpointProvider.getApiRoute(getApiRouteBase());
    }

    try (Connection connection = new Connection(url)) {
      Request request =
          Request.builder()
              .setAuthorization(authorization)
              .setBody(registryEndpointProvider.getContent())
              .build();
      Response response = connection.send(registryEndpointProvider.getHttpMethod(), request);

      return registryEndpointProvider.handleResponse(response);

    } catch (HttpResponseException ex) {
      if (ex.getStatusCode() == HttpURLConnection.HTTP_BAD_REQUEST
          || ex.getStatusCode() == HttpURLConnection.HTTP_NOT_FOUND
          || ex.getStatusCode() == HttpURLConnection.HTTP_BAD_METHOD) {
        // The name or reference was invalid.
        ErrorResponseTemplate errorResponse =
            JsonTemplateMapper.readJson(ex.getContent(), ErrorResponseTemplate.class);
        RegistryErrorExceptionBuilder registryErrorExceptionBuilder =
            new RegistryErrorExceptionBuilder(registryEndpointProvider.getActionDescription(), ex);
        for (ErrorEntryTemplate errorEntry : errorResponse.getErrors()) {
          registryErrorExceptionBuilder.addErrorEntry(errorEntry);
        }

        throw registryErrorExceptionBuilder.build();

      } else if (ex.getStatusCode() == HttpURLConnection.HTTP_UNAUTHORIZED
          || ex.getStatusCode() == HttpURLConnection.HTTP_FORBIDDEN) {
        throw new RegistryUnauthorizedException(ex);

      } else if (ex.getStatusCode() == HttpStatusCodes.STATUS_CODE_TEMPORARY_REDIRECT) {
        return callRegistryEndpoint(
            new URL(ex.getHeaders().getLocation()), registryEndpointProvider);

      } else {
        // Unknown
        throw ex;
      }
    }
  }
}
