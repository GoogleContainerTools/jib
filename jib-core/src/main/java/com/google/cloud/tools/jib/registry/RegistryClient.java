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

import com.google.cloud.tools.jib.blob.Blob;
import com.google.cloud.tools.jib.blob.BlobDescriptor;
import com.google.cloud.tools.jib.blob.Blobs;
import com.google.cloud.tools.jib.builder.TimerEventDispatcher;
import com.google.cloud.tools.jib.event.EventDispatcher;
import com.google.cloud.tools.jib.global.JibSystemProperties;
import com.google.cloud.tools.jib.http.Authorization;
import com.google.cloud.tools.jib.image.DescriptorDigest;
import com.google.cloud.tools.jib.image.json.BuildableManifestTemplate;
import com.google.cloud.tools.jib.image.json.ManifestTemplate;
import com.google.cloud.tools.jib.image.json.V21ManifestTemplate;
import com.google.cloud.tools.jib.image.json.V22ManifestTemplate;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import java.io.IOException;
import java.net.URL;
import javax.annotation.Nullable;

/** Interfaces with a registry. */
public class RegistryClient {

  /** Factory for creating {@link RegistryClient}s. */
  public static class Factory {

    private final EventDispatcher eventDispatcher;
    private final RegistryEndpointRequestProperties registryEndpointRequestProperties;

    private boolean allowInsecureRegistries = false;
    @Nullable private String userAgentSuffix;
    @Nullable private Authorization authorization;

    private Factory(
        EventDispatcher eventDispatcher,
        RegistryEndpointRequestProperties registryEndpointRequestProperties) {
      this.eventDispatcher = eventDispatcher;
      this.registryEndpointRequestProperties = registryEndpointRequestProperties;
    }

    /**
     * Sets whether or not to allow insecure registries (ignoring certificate validation failure or
     * communicating over HTTP if all else fail).
     *
     * @param allowInsecureRegistries if {@code true}, insecure connections will be allowed
     * @return this
     */
    public Factory setAllowInsecureRegistries(boolean allowInsecureRegistries) {
      this.allowInsecureRegistries = allowInsecureRegistries;
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
     * Sets a suffix to append to {@code User-Agent} headers.
     *
     * @param userAgentSuffix the suffix to append
     * @return this
     */
    public Factory setUserAgentSuffix(@Nullable String userAgentSuffix) {
      this.userAgentSuffix = userAgentSuffix;
      return this;
    }

    /**
     * Creates a new {@link RegistryClient}.
     *
     * @return the new {@link RegistryClient}
     */
    public RegistryClient newRegistryClient() {
      return new RegistryClient(
          eventDispatcher,
          authorization,
          registryEndpointRequestProperties,
          allowInsecureRegistries,
          makeUserAgent());
    }

    /**
     * The {@code User-Agent} is in the form of {@code jib <version> <type>}. For example: {@code
     * jib 0.9.0 jib-maven-plugin}.
     *
     * @return the {@code User-Agent} header to send. The {@code User-Agent} can be disabled by
     *     setting the system property variable {@code _JIB_DISABLE_USER_AGENT} to any non-empty
     *     string.
     */
    private String makeUserAgent() {
      if (!JibSystemProperties.isUserAgentEnabled()) {
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
  }

  /**
   * Creates a new {@link Factory} for building a {@link RegistryClient}.
   *
   * @param eventDispatcher the event dispatcher used for dispatching log events
   * @param serverUrl the server URL for the registry (for example, {@code gcr.io})
   * @param imageName the image/repository name (also known as, namespace)
   * @return the new {@link Factory}
   */
  public static Factory factory(
      EventDispatcher eventDispatcher, String serverUrl, String imageName) {
    return new Factory(
        eventDispatcher, new RegistryEndpointRequestProperties(serverUrl, imageName));
  }

  private final EventDispatcher eventDispatcher;
  @Nullable private final Authorization authorization;
  private final RegistryEndpointRequestProperties registryEndpointRequestProperties;
  private final boolean allowInsecureRegistries;
  private final String userAgent;

  /**
   * Instantiate with {@link #factory}.
   *
   * @param eventDispatcher the event dispatcher used for dispatching log events
   * @param authorization the {@link Authorization} to access the registry/repository
   * @param registryEndpointRequestProperties properties of registry endpoint requests
   * @param allowInsecureRegistries if {@code true}, insecure connections will be allowed
   */
  private RegistryClient(
      EventDispatcher eventDispatcher,
      @Nullable Authorization authorization,
      RegistryEndpointRequestProperties registryEndpointRequestProperties,
      boolean allowInsecureRegistries,
      String userAgent) {
    this.eventDispatcher = eventDispatcher;
    this.authorization = authorization;
    this.registryEndpointRequestProperties = registryEndpointRequestProperties;
    this.allowInsecureRegistries = allowInsecureRegistries;
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
   * Gets the BLOB referenced by {@code blobDigest}. Note that the BLOB is only pulled when it is
   * written out.
   *
   * @param blobDigest the digest of the BLOB to download
   * @return a {@link Blob} backed by the file at {@code destPath}. The file at {@code destPath}
   *     must exist for {@link Blob} to be valid.
   */
  public Blob pullBlob(DescriptorDigest blobDigest) {
    return Blobs.from(
        outputStream -> {
          try {
            callRegistryEndpoint(
                new BlobPuller(registryEndpointRequestProperties, blobDigest, outputStream));

          } catch (RegistryException ex) {
            throw new IOException(ex);
          }
        });
  }

  /**
   * Pushes the BLOB. If the {@code sourceRepository} is provided then the remote registry may skip
   * if the BLOB already exists on the registry.
   *
   * @param blobDigest the digest of the BLOB, used for existence-check
   * @param blob the BLOB to push
   * @param sourceRepository if pushing to the same registry then the source image, or {@code null}
   *     otherwise; used to optimize the BLOB push
   * @return {@code true} if the BLOB already exists on the registry and pushing was skipped; false
   *     if the BLOB was pushed
   * @throws IOException if communicating with the endpoint fails
   * @throws RegistryException if communicating with the endpoint fails
   */
  public boolean pushBlob(DescriptorDigest blobDigest, Blob blob, @Nullable String sourceRepository)
      throws IOException, RegistryException {
    BlobPusher blobPusher =
        new BlobPusher(registryEndpointRequestProperties, blobDigest, blob, sourceRepository);

    try (TimerEventDispatcher timerEventDispatcher =
        new TimerEventDispatcher(eventDispatcher, "pushBlob")) {
      try (TimerEventDispatcher timerEventDispatcher2 =
          timerEventDispatcher.subTimer("pushBlob POST " + blobDigest)) {

        // POST /v2/<name>/blobs/uploads/ OR
        // POST /v2/<name>/blobs/uploads/?mount={blob.digest}&from={sourceRepository}
        URL patchLocation = callRegistryEndpoint(blobPusher.initializer());
        if (patchLocation == null) {
          // The BLOB exists already.
          return true;
        }

        timerEventDispatcher2.lap("pushBlob PATCH " + blobDigest);

        // PATCH <Location> with BLOB
        URL putLocation = callRegistryEndpoint(blobPusher.writer(patchLocation));
        Preconditions.checkNotNull(putLocation);

        timerEventDispatcher2.lap("pushBlob PUT " + blobDigest);

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
            eventDispatcher,
            userAgent,
            getApiRouteBase(),
            registryEndpointProvider,
            authorization,
            registryEndpointRequestProperties,
            allowInsecureRegistries)
        .call();
  }
}
