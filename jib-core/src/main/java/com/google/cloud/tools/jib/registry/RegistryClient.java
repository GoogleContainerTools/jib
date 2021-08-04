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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.api.client.http.HttpStatusCodes;
import com.google.api.client.util.Base64;
import com.google.cloud.tools.jib.api.Credential;
import com.google.cloud.tools.jib.api.DescriptorDigest;
import com.google.cloud.tools.jib.api.LogEvent;
import com.google.cloud.tools.jib.api.RegistryAuthenticationFailedException;
import com.google.cloud.tools.jib.api.RegistryException;
import com.google.cloud.tools.jib.api.RegistryUnauthorizedException;
import com.google.cloud.tools.jib.blob.Blob;
import com.google.cloud.tools.jib.blob.BlobDescriptor;
import com.google.cloud.tools.jib.blob.Blobs;
import com.google.cloud.tools.jib.builder.TimerEventDispatcher;
import com.google.cloud.tools.jib.event.EventHandlers;
import com.google.cloud.tools.jib.global.JibSystemProperties;
import com.google.cloud.tools.jib.http.Authorization;
import com.google.cloud.tools.jib.http.FailoverHttpClient;
import com.google.cloud.tools.jib.http.Response;
import com.google.cloud.tools.jib.image.json.ManifestTemplate;
import com.google.cloud.tools.jib.json.JsonTemplate;
import com.google.cloud.tools.jib.json.JsonTemplateMapper;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Verify;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Multimap;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;

/** Interfaces with a registry. Thread-safe. */
@ThreadSafe
public class RegistryClient {

  /** Factory for creating {@link RegistryClient}s. */
  public static class Factory {

    private final EventHandlers eventHandlers;
    private final RegistryEndpointRequestProperties registryEndpointRequestProperties;
    private final FailoverHttpClient httpClient;

    @Nullable private String userAgent;
    @Nullable private Credential credential;

    private Factory(
        EventHandlers eventHandlers,
        RegistryEndpointRequestProperties registryEndpointRequestProperties,
        FailoverHttpClient httpClient) {
      this.eventHandlers = eventHandlers;
      this.registryEndpointRequestProperties = registryEndpointRequestProperties;
      this.httpClient = httpClient;
    }

    /**
     * Sets the authentication credentials to use to authenticate with the registry.
     *
     * @param credential the {@link Credential} to access the registry/repository
     * @return this
     */
    public Factory setCredential(@Nullable Credential credential) {
      this.credential = credential;
      return this;
    }

    /**
     * Sets the value of {@code User-Agent} in headers for registry requests.
     *
     * @param userAgent user agent string
     * @return this
     */
    public Factory setUserAgent(@Nullable String userAgent) {
      this.userAgent = userAgent;
      return this;
    }

    /**
     * Creates a new {@link RegistryClient}.
     *
     * @return the new {@link RegistryClient}
     */
    public RegistryClient newRegistryClient() {
      return new RegistryClient(
          eventHandlers, credential, registryEndpointRequestProperties, userAgent, httpClient);
    }
  }

  private static final int MAX_BEARER_TOKEN_REFRESH_TRIES = 5;

  /**
   * Creates a new {@link Factory} for building a {@link RegistryClient}.
   *
   * @param eventHandlers the event handlers used for dispatching log events
   * @param serverUrl the server URL for the registry (for example, {@code gcr.io})
   * @param imageName the image/repository name (also known as, namespace)
   * @param httpClient HTTP client
   * @return the new {@link Factory}
   */
  public static Factory factory(
      EventHandlers eventHandlers,
      String serverUrl,
      String imageName,
      FailoverHttpClient httpClient) {
    return new Factory(
        eventHandlers, new RegistryEndpointRequestProperties(serverUrl, imageName), httpClient);
  }

  /**
   * Creates a new {@link Factory} for building a {@link RegistryClient}.
   *
   * @param eventHandlers the event handlers used for dispatching log events
   * @param serverUrl the server URL for the registry (for example, {@code gcr.io})
   * @param imageName the image/repository name (also known as, namespace)
   * @param sourceImageName additional source image to request pull permission from the registry
   * @param httpClient HTTP client
   * @return the new {@link Factory}
   */
  public static Factory factory(
      EventHandlers eventHandlers,
      String serverUrl,
      String imageName,
      String sourceImageName,
      FailoverHttpClient httpClient) {
    return new Factory(
        eventHandlers,
        new RegistryEndpointRequestProperties(serverUrl, imageName, sourceImageName),
        httpClient);
  }

  /**
   * A simple class representing the payload of a <a
   * href="https://docs.docker.com/registry/spec/auth/jwt/">Docker Registry v2 Bearer Token</a>
   * which lists the set of access claims granted.
   *
   * <pre>
   * {"access":[{"type": "repository","name": "library/openjdk","actions":["push","pull"]}]}
   * </pre>
   *
   * @see AccessClaim
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  private static class TokenPayloadTemplate implements JsonTemplate {

    @Nullable private List<AccessClaim> access;
  }

  /**
   * Represents an access claim for a repository in a Docker Registry Bearer Token payload.
   *
   * <pre>{"type": "repository","name": "library/openjdk","actions":["push","pull"]}</pre>
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  private static class AccessClaim implements JsonTemplate {

    @Nullable private String type;
    @Nullable private String name;
    @Nullable private List<String> actions;
  }

  /**
   * Decode the <a href="https://docs.docker.com/registry/spec/auth/jwt/">Docker Registry v2 Bearer
   * Token</a> to list the granted repositories with their levels of access.
   *
   * @param token a Docker Registry Bearer Token
   * @return a mapping of repository to granted access scopes, or {@code null} if the token is not a
   *     Docker Registry Bearer Token
   */
  @VisibleForTesting
  @Nullable
  static Multimap<String, String> decodeTokenRepositoryGrants(String token) {
    // Docker Registry Bearer Tokens are based on JWT.  A valid JWT is a set of 3 base64-encoded
    // parts (header, payload, signature), collated with a ".".  The header and payload are
    // JSON objects.
    String[] jwtParts = token.split("\\.", -1);
    byte[] payloadData;
    if (jwtParts.length != 3 || (payloadData = Base64.decodeBase64(jwtParts[1])) == null) {
      return null;
    }

    // The payload looks like:
    // {
    //   "access":[{"type":"repository","name":"repository/name","actions":["pull"]}],
    //   "aud":"registry.docker.io",
    //   "iss":"auth.docker.io",
    //   "exp":999,
    //   "iat":999,
    //   "jti":"zzzz",
    //   "nbf":999,
    //   "sub":"e3ae001d-xxx"
    // }
    //
    try {
      TokenPayloadTemplate payload =
          JsonTemplateMapper.readJson(payloadData, TokenPayloadTemplate.class);
      if (payload.access == null) {
        return null;
      }
      return payload
          .access
          .stream()
          .filter(claim -> "repository".equals(claim.type))
          .collect(
              ImmutableSetMultimap.<AccessClaim, String, String>flatteningToImmutableSetMultimap(
                  claim -> claim.name,
                  claim -> claim.actions == null ? Stream.empty() : claim.actions.stream()));
    } catch (IOException ex) {
      return null;
    }
  }

  private final EventHandlers eventHandlers;
  @Nullable private final Credential credential;
  private final RegistryEndpointRequestProperties registryEndpointRequestProperties;
  @Nullable private final String userAgent;
  private final FailoverHttpClient httpClient;

  // mutable
  private final AtomicReference<Authorization> authorization = new AtomicReference<>();
  private boolean readOnlyBearerAuth;
  private final AtomicReference<RegistryAuthenticator> initialBearerAuthenticator =
      new AtomicReference<>();

  /**
   * Instantiate with {@link #factory}.
   *
   * @param eventHandlers the event handlers used for dispatching log events
   * @param credential credential for registry/repository; will not be used unless {@link
   *     #configureBasicAuth} or {@link #doBearerAuth} is called
   * @param registryEndpointRequestProperties properties of registry endpoint requests
   * @param userAgent {@code User-Agent} header to send with the request, can be {@code null}
   * @param httpClient HTTP client
   */
  private RegistryClient(
      EventHandlers eventHandlers,
      @Nullable Credential credential,
      RegistryEndpointRequestProperties registryEndpointRequestProperties,
      @Nullable String userAgent,
      FailoverHttpClient httpClient) {
    this.eventHandlers = eventHandlers;
    this.credential = credential;
    this.registryEndpointRequestProperties = registryEndpointRequestProperties;
    this.userAgent = userAgent;
    this.httpClient = httpClient;
  }

  /** Configure basic authentication on this registry client. */
  public void configureBasicAuth() {
    Preconditions.checkNotNull(credential);
    Preconditions.checkState(!credential.isOAuth2RefreshToken());

    authorization.set(
        Authorization.fromBasicCredentials(credential.getUsername(), credential.getPassword()));

    String registry = registryEndpointRequestProperties.getServerUrl();
    String repository = registryEndpointRequestProperties.getImageName();
    eventHandlers.dispatch(
        LogEvent.debug("configured basic auth for " + registry + "/" + repository));
  }

  /**
   * Attempts bearer authentication for pull.
   *
   * @return {@code true} if bearer authentication succeeded; {@code false} if the server expects
   *     basic authentication (and thus bearer authentication was not attempted)
   * @throws IOException if communicating with the endpoint fails
   * @throws RegistryException if communicating with the endpoint fails
   * @throws RegistryAuthenticationFailedException if authentication fails
   * @throws RegistryCredentialsNotSentException if authentication failed and credentials were not
   *     sent over plain HTTP
   */
  public boolean doPullBearerAuth() throws IOException, RegistryException {
    return doBearerAuth(true);
  }

  /**
   * Attempts bearer authentication for pull and push.
   *
   * @return true if bearer authentication succeeded; false if the server expects basic
   *     authentication (and thus bearer authentication was not attempted)
   * @throws IOException if communicating with the endpoint fails
   * @throws RegistryException if communicating with the endpoint fails
   * @throws RegistryAuthenticationFailedException if authentication fails
   * @throws RegistryCredentialsNotSentException if authentication failed and credentials were not
   *     sent over plain HTTP
   */
  public boolean doPushBearerAuth() throws IOException, RegistryException {
    return doBearerAuth(false);
  }

  private boolean doBearerAuth(boolean readOnlyBearerAuth) throws IOException, RegistryException {
    String registry = registryEndpointRequestProperties.getServerUrl();
    String repository = registryEndpointRequestProperties.getImageName();
    String image = registry + "/" + repository;
    eventHandlers.dispatch(LogEvent.debug("attempting bearer auth for " + image + "..."));

    Optional<RegistryAuthenticator> authenticator =
        callRegistryEndpoint(
            new AuthenticationMethodRetriever(
                registryEndpointRequestProperties, getUserAgent(), httpClient));
    if (!authenticator.isPresent()) {
      eventHandlers.dispatch(LogEvent.debug("server requires basic auth for " + image));
      return false; // server returned "WWW-Authenticate: Basic ..."
    }

    doBearerAuth(readOnlyBearerAuth, authenticator.get());
    return true;
  }

  private void doBearerAuth(boolean readOnlyBearerAuth, RegistryAuthenticator authenticator)
      throws RegistryException {
    initialBearerAuthenticator.set(authenticator);
    if (readOnlyBearerAuth) {
      authorization.set(authenticator.authenticatePull(credential));
    } else {
      authorization.set(authenticator.authenticatePush(credential));
    }
    this.readOnlyBearerAuth = readOnlyBearerAuth;

    eventHandlers.dispatch(
        LogEvent.debug(
            "bearer auth succeeded for "
                + registryEndpointRequestProperties.getServerUrl()
                + "/"
                + registryEndpointRequestProperties.getImageName()));
  }

  private Authorization refreshBearerAuth(@Nullable String wwwAuthenticate)
      throws RegistryAuthenticationFailedException, RegistryCredentialsNotSentException {
    Preconditions.checkState(isBearerAuth(authorization.get()));

    String registry = registryEndpointRequestProperties.getServerUrl();
    String repository = registryEndpointRequestProperties.getImageName();
    eventHandlers.dispatch(
        LogEvent.debug("refreshing bearer auth token for " + registry + "/" + repository + "..."));

    if (wwwAuthenticate != null) {
      Optional<RegistryAuthenticator> authenticator =
          RegistryAuthenticator.fromAuthenticationMethod(
              wwwAuthenticate, registryEndpointRequestProperties, getUserAgent(), httpClient);
      if (authenticator.isPresent()) {
        if (readOnlyBearerAuth) {
          return authenticator.get().authenticatePull(credential);
        }
        return authenticator.get().authenticatePush(credential);
      }
    }

    eventHandlers.dispatch(
        LogEvent.debug(
            "server did not return 'WWW-Authenticate: Bearer' header. Actual: " + wwwAuthenticate));
    if (readOnlyBearerAuth) {
      return Verify.verifyNotNull(initialBearerAuthenticator.get()).authenticatePull(credential);
    }
    return Verify.verifyNotNull(initialBearerAuthenticator.get()).authenticatePush(credential);
  }

  /**
   * Configure basic authentication or attempts bearer authentication for pulling based on the
   * specified authentication method in a server response.
   *
   * @param wwwAuthenticate {@code WWW-Authenticate} HTTP header value from a server response
   *     specifying a required authentication method
   * @throws RegistryException if communicating with the endpoint fails
   * @throws RegistryAuthenticationFailedException if authentication fails
   * @throws RegistryCredentialsNotSentException if authentication failed and credentials were not
   */
  public void authPullByWwwAuthenticate(String wwwAuthenticate) throws RegistryException {
    Optional<RegistryAuthenticator> authenticator =
        RegistryAuthenticator.fromAuthenticationMethod(
            wwwAuthenticate, registryEndpointRequestProperties, getUserAgent(), httpClient);
    if (authenticator.isPresent()) {
      doBearerAuth(true, authenticator.get());
    } else if (credential != null && !credential.isOAuth2RefreshToken()) {
      configureBasicAuth();
    }
  }

  /**
   * Check if a manifest referred to by {@code imageQualifier} (tag or digest) exists on the
   * registry.
   *
   * @param imageQualifier the tag or digest to check for
   * @return the {@link ManifestAndDigest} referred to by {@code imageQualifier} if the manifest
   *     exists on the registry, or {@link Optional#empty()} otherwise
   * @throws IOException if communicating with the endpoint fails
   * @throws RegistryException if communicating with the endpoint fails
   */
  public Optional<ManifestAndDigest<ManifestTemplate>> checkManifest(String imageQualifier)
      throws IOException, RegistryException {
    ManifestChecker<ManifestTemplate> manifestChecker =
        new ManifestChecker<>(
            registryEndpointRequestProperties, imageQualifier, ManifestTemplate.class);
    return callRegistryEndpoint(manifestChecker);
  }

  /**
   * Pulls the image manifest and digest for a specific tag.
   *
   * @param <T> child type of ManifestTemplate
   * @param imageQualifier the tag or digest to pull on
   * @param manifestTemplateClass the specific version of manifest template to pull, or {@link
   *     ManifestTemplate} to pull predefined subclasses; see: {@link
   *     ManifestPuller#handleResponse(Response)}
   * @return the {@link ManifestAndDigest}
   * @throws IOException if communicating with the endpoint fails
   * @throws RegistryException if communicating with the endpoint fails
   */
  public <T extends ManifestTemplate> ManifestAndDigest<T> pullManifest(
      String imageQualifier, Class<T> manifestTemplateClass) throws IOException, RegistryException {
    ManifestPuller<T> manifestPuller =
        new ManifestPuller<>(
            registryEndpointRequestProperties, imageQualifier, manifestTemplateClass);
    return callRegistryEndpoint(manifestPuller);
  }

  public ManifestAndDigest<ManifestTemplate> pullManifest(String imageQualifier)
      throws IOException, RegistryException {
    return pullManifest(imageQualifier, ManifestTemplate.class);
  }

  /**
   * Pushes the image manifest for a specific tag.
   *
   * @param manifestTemplate the image manifest
   * @param imageTag the tag to push on
   * @return the digest of the pushed image
   * @throws IOException if communicating with the endpoint fails
   * @throws RegistryException if communicating with the endpoint fails
   */
  public DescriptorDigest pushManifest(ManifestTemplate manifestTemplate, String imageTag)
      throws IOException, RegistryException {
    if (isBearerAuth(authorization.get()) && readOnlyBearerAuth) {
      throw new IllegalStateException("push may fail with pull-only bearer auth token");
    }

    return callRegistryEndpoint(
        new ManifestPusher(
            registryEndpointRequestProperties, manifestTemplate, imageTag, eventHandlers));
  }

  /**
   * Check if a blob is on the registry.
   *
   * @param blobDigest the blob digest to check for
   * @return the BLOB's {@link BlobDescriptor} if the BLOB exists on the registry, or {@link
   *     Optional#empty()} if it doesn't
   * @throws IOException if communicating with the endpoint fails
   * @throws RegistryException if communicating with the endpoint fails
   */
  public Optional<BlobDescriptor> checkBlob(DescriptorDigest blobDigest)
      throws IOException, RegistryException {
    BlobChecker blobChecker = new BlobChecker(registryEndpointRequestProperties, blobDigest);
    return callRegistryEndpoint(blobChecker);
  }

  /**
   * Gets the BLOB referenced by {@code blobDigest}. Note that the BLOB is only pulled when it is
   * written out.
   *
   * @param blobDigest the digest of the BLOB to download
   * @param blobSizeListener callback to receive the total size of the BLOb to pull
   * @param writtenByteCountListener listens on byte count written to an output stream during the
   *     pull
   * @return a {@link Blob}
   */
  public Blob pullBlob(
      DescriptorDigest blobDigest,
      Consumer<Long> blobSizeListener,
      Consumer<Long> writtenByteCountListener) {
    return Blobs.from(
        outputStream -> {
          try {
            callRegistryEndpoint(
                new BlobPuller(
                    registryEndpointRequestProperties,
                    blobDigest,
                    outputStream,
                    blobSizeListener,
                    writtenByteCountListener));

          } catch (RegistryException ex) {
            throw new IOException(ex);
          }
        },
        false);
  }

  /**
   * Pushes the BLOB. If the {@code sourceRepository} is provided then the remote registry may skip
   * if the BLOB already exists on the registry.
   *
   * @param blobDigest the digest of the BLOB, used for existence-check
   * @param blob the BLOB to push
   * @param sourceRepository if pushing to the same registry then the source image, or {@code null}
   *     otherwise; used to optimize the BLOB push
   * @param writtenByteCountListener listens on byte count written to the registry during the push
   * @return {@code true} if the BLOB already exists on the registry and pushing was skipped; false
   *     if the BLOB was pushed
   * @throws IOException if communicating with the endpoint fails
   * @throws RegistryException if communicating with the endpoint fails
   */
  public boolean pushBlob(
      DescriptorDigest blobDigest,
      Blob blob,
      @Nullable String sourceRepository,
      Consumer<Long> writtenByteCountListener)
      throws IOException, RegistryException {
    if (isBearerAuth(authorization.get()) && readOnlyBearerAuth) {
      throw new IllegalStateException("push may fail with pull-only bearer auth token");
    }

    if (sourceRepository != null
        && !(JibSystemProperties.useCrossRepositoryBlobMounts()
            && canAttemptBlobMount(authorization.get(), sourceRepository))) {
      // don't bother requesting a cross-repository blob-mount if we don't have access
      sourceRepository = null;
    }
    BlobPusher blobPusher =
        new BlobPusher(registryEndpointRequestProperties, blobDigest, blob, sourceRepository);

    try (TimerEventDispatcher timerEventDispatcher =
        new TimerEventDispatcher(eventHandlers, "pushBlob")) {
      try (TimerEventDispatcher timerEventDispatcher2 =
          timerEventDispatcher.subTimer("pushBlob POST " + blobDigest)) {

        // POST /v2/<name>/blobs/uploads/?mount={blob.digest}&from={sourceRepository}
        // POST /v2/<name>/blobs/uploads/
        Optional<URL> patchLocation = callRegistryEndpoint(blobPusher.initializer());
        if (!patchLocation.isPresent()) {
          // The BLOB exists already.
          return true;
        }

        timerEventDispatcher2.lap("pushBlob PATCH " + blobDigest);

        // PATCH <Location> with BLOB
        URL putLocation =
            callRegistryEndpoint(blobPusher.writer(patchLocation.get(), writtenByteCountListener));

        timerEventDispatcher2.lap("pushBlob PUT " + blobDigest);

        // PUT <Location>?digest={blob.digest}
        callRegistryEndpoint(blobPusher.committer(putLocation));

        return false;
      }
    }
  }

  /**
   * Check if the authorization allows using the specified repository can be mounted by the remote
   * registry as a source for blobs. More specifically, we can only check if the repository is not
   * disallowed.
   *
   * @param repository repository in question
   * @return {@code true} if the repository appears to be mountable
   */
  @VisibleForTesting
  static boolean canAttemptBlobMount(@Nullable Authorization authorization, String repository) {
    if (!isBearerAuth(authorization)) {
      // Authorization methods other than the Docker Container Registry Token don't provide
      // information as to which repositories are accessible.  The caller should attempt the mount
      // and rely on the registry fallback as required by the spec.
      // https://docs.docker.com/registry/spec/api/#pushing-an-image
      return true;
    }
    // if null then does not appear to be a DCRT
    Multimap<String, String> repositoryGrants =
        decodeTokenRepositoryGrants(Verify.verifyNotNull(authorization).getToken());
    return repositoryGrants == null || repositoryGrants.containsEntry(repository, "pull");
  }

  private static boolean isBearerAuth(@Nullable Authorization authorization) {
    return authorization != null && "bearer".equalsIgnoreCase(authorization.getScheme());
  }

  @Nullable
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
  private <T> T callRegistryEndpoint(RegistryEndpointProvider<T> registryEndpointProvider)
      throws IOException, RegistryException {
    int bearerTokenRefreshes = 0;
    while (true) {
      try {
        return new RegistryEndpointCaller<>(
                eventHandlers,
                getUserAgent(),
                registryEndpointProvider,
                authorization.get(),
                registryEndpointRequestProperties,
                httpClient)
            .call();

      } catch (RegistryUnauthorizedException ex) {
        if (ex.getHttpResponseException().getStatusCode()
                != HttpStatusCodes.STATUS_CODE_UNAUTHORIZED
            || !isBearerAuth(authorization.get())
            || ++bearerTokenRefreshes >= MAX_BEARER_TOKEN_REFRESH_TRIES) {
          throw ex;
        }

        // Because we successfully did bearer authentication initially, getting 401 here probably
        // means the token was expired.
        String wwwAuthenticate = ex.getHttpResponseException().getHeaders().getAuthenticate();
        authorization.set(refreshBearerAuth(wwwAuthenticate));
      }
    }
  }
}
