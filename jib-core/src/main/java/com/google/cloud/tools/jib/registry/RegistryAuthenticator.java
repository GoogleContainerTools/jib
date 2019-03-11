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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.cloud.tools.jib.blob.Blobs;
import com.google.cloud.tools.jib.configuration.credentials.Credential;
import com.google.cloud.tools.jib.event.EventDispatcher;
import com.google.cloud.tools.jib.global.JibSystemProperties;
import com.google.cloud.tools.jib.http.Authorization;
import com.google.cloud.tools.jib.http.Authorizations;
import com.google.cloud.tools.jib.http.BlobHttpContent;
import com.google.cloud.tools.jib.http.Connection;
import com.google.cloud.tools.jib.http.Request;
import com.google.cloud.tools.jib.http.Response;
import com.google.cloud.tools.jib.json.JsonTemplate;
import com.google.cloud.tools.jib.json.JsonTemplateMapper;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Verify;
import com.google.common.net.MediaType;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/**
 * Authenticates push/pull access with a registry service.
 *
 * @see <a
 *     href="https://docs.docker.com/registry/spec/auth/token/">https://docs.docker.com/registry/spec/auth/token/</a>
 */
public class RegistryAuthenticator {

  /** Initializer for {@link RegistryAuthenticator}. */
  public static class Initializer {

    private final EventDispatcher eventDispatcher;
    private final String serverUrl;
    private final String repository;
    private boolean allowInsecureRegistries = false;

    /**
     * Instantiates a new initializer for {@link RegistryAuthenticator}.
     *
     * @param eventDispatcher the event dispatcher used for dispatching log events
     * @param serverUrl the server URL for the registry (for example, {@code gcr.io})
     * @param repository the image/repository name (also known as, namespace)
     */
    private Initializer(EventDispatcher eventDispatcher, String serverUrl, String repository) {
      this.eventDispatcher = eventDispatcher;
      this.serverUrl = serverUrl;
      this.repository = repository;
    }

    public Initializer setAllowInsecureRegistries(boolean allowInsecureRegistries) {
      this.allowInsecureRegistries = allowInsecureRegistries;
      return this;
    }

    /**
     * Gets a {@link RegistryAuthenticator} for a custom registry server and repository.
     *
     * @return the {@link RegistryAuthenticator} to authenticate pulls/pushes with the registry, or
     *     {@code null} if no token authentication is necessary
     * @throws RegistryAuthenticationFailedException if failed to create the registry authenticator
     * @throws IOException if communicating with the endpoint fails
     * @throws RegistryException if communicating with the endpoint fails
     */
    @Nullable
    public RegistryAuthenticator initialize()
        throws RegistryAuthenticationFailedException, IOException, RegistryException {
      try {
        return RegistryClient.factory(eventDispatcher, serverUrl, repository)
            .setAllowInsecureRegistries(allowInsecureRegistries)
            .newRegistryClient()
            .getRegistryAuthenticator();

      } catch (MalformedURLException ex) {
        throw new RegistryAuthenticationFailedException(serverUrl, repository, ex);

      } catch (InsecureRegistryException ex) {
        // Cannot skip certificate validation or use HTTP, so just return null.
        return null;
      }
    }
  }

  /**
   * Sets a {@code Credential} to help the authentication.
   *
   * @param credential the credential used to authenticate.
   * @return this
   */
  public RegistryAuthenticator setCredential(@Nullable Credential credential) {
    this.credential = credential;
    return this;
  }

  /**
   * Gets a new initializer for {@link RegistryAuthenticator}.
   *
   * @param eventDispatcher the event dispatcher used for dispatching log events
   * @param serverUrl the server URL for the registry (for example, {@code gcr.io})
   * @param repository the image/repository name (also known as, namespace)
   * @return the new {@link Initializer}
   */
  public static Initializer initializer(
      EventDispatcher eventDispatcher, String serverUrl, String repository) {
    return new Initializer(eventDispatcher, serverUrl, repository);
  }

  // TODO: Replace with a WWW-Authenticate header parser.
  /**
   * Instantiates from parsing a {@code WWW-Authenticate} header.
   *
   * @param authenticationMethod the {@code WWW-Authenticate} header value
   * @param registryEndpointRequestProperties the registry request properties
   * @return a new {@link RegistryAuthenticator} for authenticating with the registry service
   * @throws RegistryAuthenticationFailedException if authentication fails
   * @see <a
   *     href="https://docs.docker.com/registry/spec/auth/token/#how-to-authenticate">https://docs.docker.com/registry/spec/auth/token/#how-to-authenticate</a>
   */
  @Nullable
  static RegistryAuthenticator fromAuthenticationMethod(
      String authenticationMethod,
      RegistryEndpointRequestProperties registryEndpointRequestProperties)
      throws RegistryAuthenticationFailedException {
    // If the authentication method starts with 'basic ' (case insensitive), no registry
    // authentication is needed.
    if (authenticationMethod.matches("^(?i)(basic) .*")) {
      return null;
    }

    // Checks that the authentication method starts with 'bearer ' (case insensitive).
    if (!authenticationMethod.matches("^(?i)(bearer) .*")) {
      throw newRegistryAuthenticationFailedException(
          registryEndpointRequestProperties.getServerUrl(),
          registryEndpointRequestProperties.getImageName(),
          authenticationMethod,
          "Bearer");
    }

    Pattern realmPattern = Pattern.compile("realm=\"(.*?)\"");
    Matcher realmMatcher = realmPattern.matcher(authenticationMethod);
    if (!realmMatcher.find()) {
      throw newRegistryAuthenticationFailedException(
          registryEndpointRequestProperties.getServerUrl(),
          registryEndpointRequestProperties.getImageName(),
          authenticationMethod,
          "realm");
    }
    String realm = realmMatcher.group(1);

    Pattern servicePattern = Pattern.compile("service=\"(.*?)\"");
    Matcher serviceMatcher = servicePattern.matcher(authenticationMethod);
    // use the provided registry location when missing service (e.g., for OpenShift)
    String service =
        serviceMatcher.find()
            ? serviceMatcher.group(1)
            : registryEndpointRequestProperties.getServerUrl();

    return new RegistryAuthenticator(realm, service, registryEndpointRequestProperties);
  }

  private static RegistryAuthenticationFailedException newRegistryAuthenticationFailedException(
      String registry, String repository, String authenticationMethod, String authParam) {
    return new RegistryAuthenticationFailedException(
        registry,
        repository,
        "'"
            + authParam
            + "' was not found in the 'WWW-Authenticate' header, tried to parse: "
            + authenticationMethod);
  }

  /** Template for the authentication response JSON. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  private static class AuthenticationResponseTemplate implements JsonTemplate {

    @Nullable private String token;

    /**
     * {@code access_token} is accepted as an alias for {@code token}.
     *
     * @see <a
     *     href="https://docs.docker.com/registry/spec/auth/token/#token-response-fields">https://docs.docker.com/registry/spec/auth/token/#token-response-fields</a>
     */
    @Nullable private String access_token;

    /** @return {@link #token} if not null, or {@link #access_token} */
    @Nullable
    private String getToken() {
      if (token != null) {
        return token;
      }
      return access_token;
    }
  }

  private final RegistryEndpointRequestProperties registryEndpointRequestProperties;
  private final String realm;
  private final String service;
  @Nullable private Credential credential;

  RegistryAuthenticator(
      String realm,
      String service,
      RegistryEndpointRequestProperties registryEndpointRequestProperties) {
    this.realm = realm;
    this.service = service;
    this.registryEndpointRequestProperties = registryEndpointRequestProperties;
  }

  /**
   * Authenticates permissions to pull.
   *
   * @return an {@code Authorization} authenticating the pull
   * @throws RegistryAuthenticationFailedException if authentication fails
   */
  public Authorization authenticatePull() throws RegistryAuthenticationFailedException {
    return authenticate("pull");
  }

  /**
   * Authenticates permission to pull and push.
   *
   * @return an {@code Authorization} authenticating the push
   * @throws RegistryAuthenticationFailedException if authentication fails
   */
  public Authorization authenticatePush() throws RegistryAuthenticationFailedException {
    return authenticate("pull,push");
  }

  @VisibleForTesting
  String getServiceScopeRequestParameters(String scope) {
    return "service="
        + service
        + "&scope=repository:"
        + registryEndpointRequestProperties.getImageName()
        + ":"
        + scope;
  }

  @VisibleForTesting
  URL getAuthenticationUrl(String scope) throws MalformedURLException {
    return isOAuth2Auth()
        ? new URL(realm) // Required parameters will be sent via POST .
        : new URL(realm + "?" + getServiceScopeRequestParameters(scope));
  }

  @VisibleForTesting
  String getAuthRequestParameters(String scope) {
    String serviceScope = getServiceScopeRequestParameters(scope);
    return isOAuth2Auth()
        ? serviceScope
            // https://github.com/GoogleContainerTools/jib/pull/1545
            + "&client_id=jib.da031fe481a93ac107a95a96462358f9"
            + "&grant_type=refresh_token&refresh_token="
            // If OAuth2, credential.getPassword() is a refresh token.
            + Verify.verifyNotNull(credential).getPassword()
        : serviceScope;
  }

  @VisibleForTesting
  boolean isOAuth2Auth() {
    return credential != null && credential.isOAuth2RefreshToken();
  }

  /**
   * Sends the authentication request and retrieves the Bearer authorization token.
   *
   * @param scope the scope of permissions to authenticate for
   * @return the {@link Authorization} response
   * @throws RegistryAuthenticationFailedException if authentication fails
   * @see <a
   *     href="https://docs.docker.com/registry/spec/auth/token/#how-to-authenticate">https://docs.docker.com/registry/spec/auth/token/#how-to-authenticate</a>
   */
  private Authorization authenticate(String scope) throws RegistryAuthenticationFailedException {
    try (Connection connection =
        Connection.getConnectionFactory().apply(getAuthenticationUrl(scope))) {
      Request.Builder requestBuilder =
          Request.builder().setHttpTimeout(JibSystemProperties.getHttpTimeout());

      if (isOAuth2Auth()) {
        String parameters = getAuthRequestParameters(scope);
        requestBuilder.setBody(
            new BlobHttpContent(Blobs.from(parameters), MediaType.FORM_DATA.toString(), null));
      } else if (credential != null) {
        requestBuilder.setAuthorization(
            Authorizations.withBasicCredentials(
                credential.getUsername(), credential.getPassword()));
      }

      Request request = requestBuilder.build();
      Response response = isOAuth2Auth() ? connection.post(request) : connection.get(request);
      String responseString = Blobs.writeToString(response.getBody());

      AuthenticationResponseTemplate responseJson =
          JsonTemplateMapper.readJson(responseString, AuthenticationResponseTemplate.class);

      if (responseJson.getToken() == null) {
        throw new RegistryAuthenticationFailedException(
            registryEndpointRequestProperties.getServerUrl(),
            registryEndpointRequestProperties.getImageName(),
            "Did not get token in authentication response from "
                + getAuthenticationUrl(scope)
                + "; parameters: "
                + getAuthRequestParameters(scope));
      }
      return Authorizations.withBearerToken(responseJson.getToken());

    } catch (IOException ex) {
      throw new RegistryAuthenticationFailedException(
          registryEndpointRequestProperties.getServerUrl(),
          registryEndpointRequestProperties.getImageName(),
          ex);
    }
  }
}
