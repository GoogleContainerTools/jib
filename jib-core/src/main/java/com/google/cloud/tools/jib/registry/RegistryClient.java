/*
 * Copyright 2018 Google LLC. All rights reserved.
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

import com.google.cloud.tools.jib.Timer;
import com.google.cloud.tools.jib.blob.Blob;
import com.google.cloud.tools.jib.blob.BlobDescriptor;
import com.google.cloud.tools.jib.builder.BuildLogger;
import com.google.cloud.tools.jib.http.Authorization;
import com.google.cloud.tools.jib.image.DescriptorDigest;
import com.google.cloud.tools.jib.image.json.BuildableManifestTemplate;
import com.google.cloud.tools.jib.image.json.ManifestTemplate;
import com.google.cloud.tools.jib.image.json.V21ManifestTemplate;
import com.google.cloud.tools.jib.image.json.V22ManifestTemplate;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import javax.annotation.Nullable;

/** Interfaces with a registry. */
public class RegistryClient {

  // TODO: Remove
  private Timer parentTimer =
      new Timer(
          new BuildLogger() {
            @Override
            public void debug(CharSequence message) {}

            @Override
            public void info(CharSequence message) {}

            @Override
            public void warn(CharSequence message) {}

            @Override
            public void error(CharSequence message) {}

            @Override
            public void lifecycle(CharSequence message) {}
          },
          "NULL TIMER");

  public RegistryClient setTimer(Timer parentTimer) {
    this.parentTimer = parentTimer;
    return this;
  }

  /** Factory for creating {@link RegistryClient}s. */
  public static class Factory {

    private final RegistryEndpointRequestProperties registryEndpointRequestProperties;

    private boolean allowHttp = false;
    @Nullable private Authorization authorization;

    private Factory(RegistryEndpointRequestProperties registryEndpointRequestProperties) {
      this.registryEndpointRequestProperties = registryEndpointRequestProperties;
    }

    /**
     * Sets whether or not {@code HTTP} should be allowed (credentials should not be sent when set
     * to {@code true}). Defaults to {@code false}.
     *
     * @param allowHttp if {@code true}, allows {@code HTTP} connections; otherwise, only {@code
     *     HTTPS} connections are allowed
     * @return this
     */
    public Factory setAllowHttp(boolean allowHttp) {
      this.allowHttp = allowHttp;
      return this;
    }

    /**
     * Sets the authentication credentials to use to authenticate with the registry.
     *
     * @param authorization the {@link Authorization} to access the registry/repository
     * @return this
     */
    public Factory setAuthorization(@Nullable Authorization authorization) {
      this.authorization = authorization;
      return this;
    }

    /**
     * Creates a new {@link RegistryClient}.
     *
     * @return the new {@link RegistryClient}
     */
    public RegistryClient newRegistryClient() {
      return new RegistryClient(
          authorization, registryEndpointRequestProperties, allowHttp, makeUserAgent());
    }
  }

  @Nullable private static String userAgentSuffix;

  /**
   * @param serverUrl the server URL for the registry (for example, {@code gcr.io})
   * @param imageName the image/repository name (also known as, namespace)
   * @return the new {@link Factory}
   */
  public static Factory factory(String serverUrl, String imageName) {
    return new Factory(new RegistryEndpointRequestProperties(serverUrl, imageName));
  }

  // TODO: Inject via a RegistryClient.Factory.
  /**
   * Sets a suffix to append to {@code User-Agent} headers.
   *
   * @param userAgentSuffix the suffix to append
   */
  public static void setUserAgentSuffix(@Nullable String userAgentSuffix) {
    RegistryClient.userAgentSuffix = userAgentSuffix;
  }

  /**
   * The {@code User-Agent} is in the form of {@code jib <version> <type>}. For example: {@code jib
   * 0.9.0 jib-maven-plugin}.
   *
   * @return the {@code User-Agent} header to send. The {@code User-Agent} can be disabled by
   *     setting the system property variable {@code _JIB_DISABLE_USER_AGENT} to any non-empty
   *     string.
   */
  @VisibleForTesting
  static String makeUserAgent() {
    if (!Strings.isNullOrEmpty(System.getProperty("_JIB_DISABLE_USER_AGENT"))) {
      return "";
    }

    String version = RegistryClient.class.getPackage().getImplementationVersion();
    StringBuilder userAgentBuilder = new StringBuilder();
    userAgentBuilder.append("jib");
    if (version != null) {
      userAgentBuilder.append(" ").append(version);
    }
    if (userAgentSuffix != null) {
      userAgentBuilder.append(" ").append(userAgentSuffix);
    }
    return userAgentBuilder.toString();
  }

  @Nullable private final Authorization authorization;
  private final RegistryEndpointRequestProperties registryEndpointRequestProperties;
  private final boolean allowHttp;
  private final String userAgent;

  /**
   * Instantiate with {@link #factory}.
   *
   * @param authorization the {@link Authorization} to access the registry/repository
   * @param registryEndpointRequestProperties properties of registry endpoint requests
   * @param allowHttp if {@code true}, allows redirects and fallbacks to HTTP; otherwise, only
   *     allows HTTPS
   */
  private RegistryClient(
      @Nullable Authorization authorization,
      RegistryEndpointRequestProperties registryEndpointRequestProperties,
      boolean allowHttp,
      String userAgent) {
    this.authorization = authorization;
    this.registryEndpointRequestProperties = registryEndpointRequestProperties;
    this.allowHttp = allowHttp;
    this.userAgent = userAgent;
  }

  /**
   * @return the {@link RegistryAuthenticator} to authenticate pulls/pushes with the registry, or
   *     {@code null} if no token authentication is necessary
   * @throws IOException if communicating with the endpoint fails
   * @throws RegistryException if communicating with the endpoint fails
   */
  @Nullable
  public RegistryAuthenticator getRegistryAuthenticator() throws IOException, RegistryException {
    // Gets the WWW-Authenticate header (eg. 'WWW-Authenticate: Bearer
    // realm="https://gcr.io/v2/token",service="gcr.io"')
    return callRegistryEndpoint(
        new AuthenticationMethodRetriever(registryEndpointRequestProperties));
  }

  /**
   * Pulls the image manifest for a specific tag.
   *
   * @param <T> child type of ManifestTemplate
   * @param imageTag the tag to pull on
   * @param manifestTemplateClass the specific version of manifest template to pull, or {@link
   *     ManifestTemplate} to pull either {@link V22ManifestTemplate} or {@link V21ManifestTemplate}
   * @return the manifest template
   * @throws IOException if communicating with the endpoint fails
   * @throws RegistryException if communicating with the endpoint fails
   */
  public <T extends ManifestTemplate> T pullManifest(
      String imageTag, Class<T> manifestTemplateClass) throws IOException, RegistryException {
    ManifestPuller<T> manifestPuller =
        new ManifestPuller<>(registryEndpointRequestProperties, imageTag, manifestTemplateClass);
    T manifestTemplate = callRegistryEndpoint(manifestPuller);
    if (manifestTemplate == null) {
      throw new IllegalStateException("ManifestPuller#handleResponse does not return null");
    }
    return manifestTemplate;
  }

  public ManifestTemplate pullManifest(String imageTag) throws IOException, RegistryException {
    return pullManifest(imageTag, ManifestTemplate.class);
  }

  /**
   * Pushes the image manifest for a specific tag.
   *
   * @param manifestTemplate the image manifest
   * @param imageTag the tag to push on
   * @throws IOException if communicating with the endpoint fails
   * @throws RegistryException if communicating with the endpoint fails
   */
  public void pushManifest(BuildableManifestTemplate manifestTemplate, String imageTag)
      throws IOException, RegistryException {
    callRegistryEndpoint(
        new ManifestPusher(registryEndpointRequestProperties, manifestTemplate, imageTag));
  }

  /**
   * @param blobDigest the blob digest to check for
   * @return the BLOB's {@link BlobDescriptor} if the BLOB exists on the registry, or {@code null}
   *     if it doesn't
   * @throws IOException if communicating with the endpoint fails
   * @throws RegistryException if communicating with the endpoint fails
   */
  @Nullable
  public BlobDescriptor checkBlob(DescriptorDigest blobDigest)
      throws IOException, RegistryException {
    BlobChecker blobChecker = new BlobChecker(registryEndpointRequestProperties, blobDigest);
    return callRegistryEndpoint(blobChecker);
  }

  /**
   * Downloads the BLOB to a file.
   *
   * @param blobDigest the digest of the BLOB to download
   * @param destinationOutputStream the {@link OutputStream} to write the BLOB to
   * @return a {@link Blob} backed by the file at {@code destPath}. The file at {@code destPath}
   *     must exist for {@link Blob} to be valid.
   * @throws IOException if communicating with the endpoint fails
   * @throws RegistryException if communicating with the endpoint fails
   */
  public Void pullBlob(DescriptorDigest blobDigest, OutputStream destinationOutputStream)
      throws RegistryException, IOException {
    BlobPuller blobPuller =
        new BlobPuller(registryEndpointRequestProperties, blobDigest, destinationOutputStream);
    return callRegistryEndpoint(blobPuller);
  }

  // TODO: Add mount with 'from' parameter
  /**
   * Pushes the BLOB, or skips if the BLOB already exists on the registry.
   *
   * @param blobDigest the digest of the BLOB, used for existence-check
   * @param blob the BLOB to push
   * @return {@code true} if the BLOB already exists on the registry and pushing was skipped; false
   *     if the BLOB was pushed
   * @throws IOException if communicating with the endpoint fails
   * @throws RegistryException if communicating with the endpoint fails
   */
  public boolean pushBlob(DescriptorDigest blobDigest, Blob blob)
      throws IOException, RegistryException {
    BlobPusher blobPusher = new BlobPusher(registryEndpointRequestProperties, blobDigest, blob);

    try (Timer t = parentTimer.subTimer("pushBlob")) {
      try (Timer t2 = t.subTimer("pushBlob POST " + blobDigest)) {

        // POST /v2/<name>/blobs/uploads/?mount={blob.digest}
        URL patchLocation = callRegistryEndpoint(blobPusher.initializer());
        if (patchLocation == null) {
          // The BLOB exists already.
          return true;
        }

        t2.lap("pushBlob PATCH " + blobDigest);

        // PATCH <Location> with BLOB
        URL putLocation = callRegistryEndpoint(blobPusher.writer(patchLocation));
        Preconditions.checkNotNull(putLocation);

        t2.lap("pushBlob PUT " + blobDigest);

        // PUT <Location>?digest={blob.digest}
        callRegistryEndpoint(blobPusher.committer(putLocation));

        return false;
      }
    }
  }

  /** @return the registry endpoint's API root, without the protocol */
  @VisibleForTesting
  String getApiRouteBase() {
    return registryEndpointRequestProperties.getServerUrl() + "/v2/";
  }

  @VisibleForTesting
  String getUserAgent() {
    return userAgent;
  }

  /**
   * Calls the registry endpoint.
   *
   * @param registryEndpointProvider the {@link RegistryEndpointProvider} to the endpoint
   * @throws IOException if communicating with the endpoint fails
   * @throws RegistryException if communicating with the endpoint fails
   */
  @Nullable
  private <T> T callRegistryEndpoint(RegistryEndpointProvider<T> registryEndpointProvider)
      throws IOException, RegistryException {
    return new RegistryEndpointCaller<>(
            userAgent,
            getApiRouteBase(),
            registryEndpointProvider,
            authorization,
            registryEndpointRequestProperties,
            allowHttp)
        .call();
  }
}
