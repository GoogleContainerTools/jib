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

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpMethods;
import com.google.cloud.tools.jib.api.DescriptorDigest;
import com.google.cloud.tools.jib.api.RegistryException;
import com.google.cloud.tools.jib.blob.Blob;
import com.google.cloud.tools.jib.http.BlobHttpContent;
import com.google.cloud.tools.jib.http.Response;
import com.google.common.net.MediaType;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import javax.annotation.Nullable;

/**
 * Pushes an image's BLOB (layer or container configuration).
 *
 * <p>The BLOB is pushed in three stages:
 *
 * <ol>
 *   <li>Initialize - Gets a location back to write the BLOB content to
 *   <li>Write BLOB - Write the BLOB content to the received location
 *   <li>Commit BLOB - Commits the BLOB with its digest
 * </ol>
 */
class BlobPusher {

  private final RegistryEndpointRequestProperties registryEndpointRequestProperties;
  private final DescriptorDigest blobDigest;
  private final Blob blob;
  @Nullable private final String sourceRepository;

  /** Initializes the BLOB upload. */
  private class Initializer implements RegistryEndpointProvider<URL> {

    @Nullable
    @Override
    public BlobHttpContent getContent() {
      return null;
    }

    @Override
    public List<String> getAccept() {
      return Collections.emptyList();
    }

    /**
     * @return a URL to continue pushing the BLOB to, or {@code null} if the BLOB already exists on
     *     the registry
     */
    @Nullable
    @Override
    public URL handleResponse(Response response) throws RegistryErrorException {
      switch (response.getStatusCode()) {
        case HttpURLConnection.HTTP_CREATED:
          // The BLOB exists in the registry.
          return null;

        case HttpURLConnection.HTTP_ACCEPTED:
          return getRedirectLocation(response);

        default:
          throw buildRegistryErrorException(
              "Received unrecognized status code " + response.getStatusCode());
      }
    }

    @Override
    public URL getApiRoute(String apiRouteBase) throws MalformedURLException {
      StringBuilder url =
          new StringBuilder(apiRouteBase)
              .append(registryEndpointRequestProperties.getImageName())
              .append("/blobs/uploads/");
      if (sourceRepository != null) {
        url.append("?mount=").append(blobDigest).append("&from=").append(sourceRepository);
      }

      return new URL(url.toString());
    }

    @Override
    public String getHttpMethod() {
      return HttpMethods.POST;
    }

    @Override
    public String getActionDescription() {
      return BlobPusher.this.getActionDescription();
    }
  }

  /** Writes the BLOB content to the upload location. */
  private class Writer implements RegistryEndpointProvider<URL> {

    private final URL location;
    private final Consumer<Long> writtenByteCountListener;

    @Nullable
    @Override
    public BlobHttpContent getContent() {
      return new BlobHttpContent(blob, MediaType.OCTET_STREAM.toString(), writtenByteCountListener);
    }

    @Override
    public List<String> getAccept() {
      return Collections.emptyList();
    }

    /** @return a URL to continue pushing the BLOB to */
    @Override
    public URL handleResponse(Response response) throws RegistryException {
      // TODO: Handle 204 No Content
      return getRedirectLocation(response);
    }

    @Override
    public URL getApiRoute(String apiRouteBase) {
      return location;
    }

    @Override
    public String getHttpMethod() {
      return HttpMethods.PATCH;
    }

    @Override
    public String getActionDescription() {
      return BlobPusher.this.getActionDescription();
    }

    private Writer(URL location, Consumer<Long> writtenByteCountListener) {
      this.location = location;
      this.writtenByteCountListener = writtenByteCountListener;
    }
  }

  /** Commits the written BLOB. */
  private class Committer implements RegistryEndpointProvider<Void> {

    private final URL location;

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
    public Void handleResponse(Response response) {
      return null;
    }

    /** @return {@code location} with query parameter 'digest' set to the BLOB's digest */
    @Override
    public URL getApiRoute(String apiRouteBase) {
      return new GenericUrl(location).set("digest", blobDigest).toURL();
    }

    @Override
    public String getHttpMethod() {
      return HttpMethods.PUT;
    }

    @Override
    public String getActionDescription() {
      return BlobPusher.this.getActionDescription();
    }

    private Committer(URL location) {
      this.location = location;
    }
  }

  BlobPusher(
      RegistryEndpointRequestProperties registryEndpointRequestProperties,
      DescriptorDigest blobDigest,
      Blob blob,
      @Nullable String sourceRepository) {
    this.registryEndpointRequestProperties = registryEndpointRequestProperties;
    this.blobDigest = blobDigest;
    this.blob = blob;
    this.sourceRepository = sourceRepository;
  }

  /**
   * @return a {@link RegistryEndpointProvider} for initializing the BLOB upload with an existence
   *     check
   */
  RegistryEndpointProvider<URL> initializer() {
    return new Initializer();
  }

  /**
   * @param location the upload URL
   * @param blobProgressListener the listener for {@link Blob} push progress
   * @return a {@link RegistryEndpointProvider} for writing the BLOB to an upload location
   */
  RegistryEndpointProvider<URL> writer(URL location, Consumer<Long> writtenByteCountListener) {
    return new Writer(location, writtenByteCountListener);
  }

  /**
   * @param location the upload URL
   * @return a {@link RegistryEndpointProvider} for committing the written BLOB with its digest
   */
  RegistryEndpointProvider<Void> committer(URL location) {
    return new Committer(location);
  }

  private RegistryErrorException buildRegistryErrorException(String reason) {
    RegistryErrorExceptionBuilder registryErrorExceptionBuilder =
        new RegistryErrorExceptionBuilder(getActionDescription());
    registryErrorExceptionBuilder.addReason(reason);
    return registryErrorExceptionBuilder.build();
  }

  /**
   * @return the common action description for {@link Initializer}, {@link Writer}, and {@link
   *     Committer}
   */
  private String getActionDescription() {
    return "push BLOB for "
        + registryEndpointRequestProperties.getServerUrl()
        + "/"
        + registryEndpointRequestProperties.getImageName()
        + " with digest "
        + blobDigest;
  }

  /**
   * Extract the {@code Location} header from the response to get the new location for the next
   * request.
   *
   * <p>The {@code Location} header can be relative or absolute.
   *
   * @see <a
   *     href="https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Location#Directives">https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Location#Directives</a>
   * @param response the response to extract the 'Location' header from
   * @return the new location for the next request
   * @throws RegistryErrorException if there was not a single 'Location' header
   */
  private URL getRedirectLocation(Response response) throws RegistryErrorException {
    // Extracts and returns the 'Location' header.
    List<String> locationHeaders = response.getHeader("Location");
    if (locationHeaders.size() != 1) {
      throw buildRegistryErrorException(
          "Expected 1 'Location' header, but found " + locationHeaders.size());
    }

    String locationHeader = locationHeaders.get(0);
    return response.getRequestUrl().toURL(locationHeader);
  }
}
