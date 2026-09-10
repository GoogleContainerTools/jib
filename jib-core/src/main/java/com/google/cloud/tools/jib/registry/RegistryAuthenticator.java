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
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.api.client.http.HttpMethods;
import com.google.api.client.http.HttpStatusCodes;
import com.google.cloud.tools.jib.api.Credential;
import com.google.cloud.tools.jib.api.RegistryAuthenticationFailedException;
import com.google.cloud.tools.jib.blob.Blobs;
import com.google.cloud.tools.jib.global.JibSystemProperties;
import com.google.cloud.tools.jib.http.Authorization;
import com.google.cloud.tools.jib.http.BlobHttpContent;
import com.google.cloud.tools.jib.http.FailoverHttpClient;
import com.google.cloud.tools.jib.http.Request;
import com.google.cloud.tools.jib.http.Response;
import com.google.cloud.tools.jib.http.ResponseException;
import com.google.cloud.tools.jib.json.JsonTemplate;
import com.google.cloud.tools.jib.json.JsonTemplateMapper;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Verify;
import com.google.common.collect.ImmutableMap;
import com.google.common.net.MediaType;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;
import java.util.Optional;
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

  // TODO: Replace with a WWW-Authenticate header parser.
  /**
   * Instantiates from parsing a {@code WWW-Authenticate} header.
   *
   * @param authenticationMethod the {@code WWW-Authenticate} header value
   * @param registryEndpointRequestProperties the registry request properties
   * @param userAgent the {@code User-Agent} header value to use in later authentication calls
   * @param httpClient HTTP client
   * @return a new {@link RegistryAuthenticator} for authenticating with the registry service
   * @throws RegistryAuthenticationFailedException if authentication fails
   * @see <a
   *     href="https://docs.docker.com/registry/spec/auth/token/#how-to-authenticate">https://docs.docker.com/registry/spec/auth/token/#how-to-authenticate</a>
   */
  static Optional<RegistryAuthenticator> fromAuthenticationMethod(
      String authenticationMethod,
      RegistryEndpointRequestProperties registryEndpointRequestProperties,
      @Nullable String userAgent,
      FailoverHttpClient httpClient)
      throws RegistryAuthenticationFailedException {
    // If the authentication method starts with 'basic' (case insensitive), no registry
    // authentication is needed.
    if (authenticationMethod.matches("^(?i)(basic).*")) {
      return Optional.empty();
    }

    String registryUrl = registryEndpointRequestProperties.getServerUrl();
    String imageName = registryEndpointRequestProperties.getImageName();
    // Checks that the authentication method starts with 'bearer ' (case insensitive).
    if (!authenticationMethod.matches("^(?i)(bearer) .*")) {
      throw newRegistryAuthenticationFailedException(
          registryUrl, imageName, authenticationMethod, "Bearer");
    }

    Pattern realmPattern = Pattern.compile("realm=\"(.*?)\"");
    Matcher realmMatcher = realmPattern.matcher(authenticationMethod);
    if (!realmMatcher.find()) {
      throw newRegistryAuthenticationFailedException(
          registryUrl, imageName, authenticationMethod, "realm");
    }
    String realm = realmMatcher.group(1);

    Pattern servicePattern = Pattern.compile("service=\"(.*?)\"");
    Matcher serviceMatcher = servicePattern.matcher(authenticationMethod);
    // use the provided registry location when missing service (e.g., for OpenShift)
    String service = serviceMatcher.find() ? serviceMatcher.group(1) : registryUrl;

    return Optional.of(
        new RegistryAuthenticator(
            realm, service, registryEndpointRequestProperties, userAgent, httpClient));
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
  @VisibleForTesting
  @JsonIgnoreProperties(ignoreUnknown = true)
  static class AuthenticationResponseTemplate implements JsonTemplate {

    @Nullable private String token;

    /**
     * {@code access_token} is accepted as an alias for {@code token}.
     *
     * @see <a
     *     href="https://docs.docker.com/registry/spec/auth/token/#token-response-fields">https://docs.docker.com/registry/spec/auth/token/#token-response-fields</a>
     */
    @Nullable
    @JsonProperty("access_token")
    private String accessToken;

    /** Returns {@link #token} if not null, or {@link #accessToken}. */
    @Nullable
    @VisibleForTesting
    String getToken() {
      if (token != null) {
        return token;
      }
      return accessToken;
    }
  }

  private final RegistryEndpointRequestProperties registryEndpointRequestProperties;
  private final String realm;
  private final String service;
  @Nullable private final String userAgent;
  private final FailoverHttpClient httpClient;

  private RegistryAuthenticator(
      String realm,
      String service,
      RegistryEndpointRequestProperties registryEndpointRequestProperties,
      @Nullable String userAgent,
      FailoverHttpClient httpClient) {
    this.realm = realm;
    this.service = service;
    this.registryEndpointRequestProperties = registryEndpointRequestProperties;
    this.userAgent = userAgent;
    this.httpClient = httpClient;
  }

  /**
   * Authenticates permissions to pull.
   *
   * @param credential the credential used to authenticate
   * @return an {@code Authorization} authenticating the pull
   * @throws RegistryAuthenticationFailedException if authentication fails
   * @throws RegistryCredentialsNotSentException if authentication failed and credentials were not
   *     sent over plain HTTP
   */
  public Authorization authenticatePull(@Nullable Credential credential)
      throws RegistryAuthenticationFailedException, RegistryCredentialsNotSentException {
    return authenticate(credential, "pull");
  }

  /**
   * Authenticates permission to pull and push.
   *
   * @param credential the credential used to authenticate
   * @return an {@code Authorization} authenticating the push
   * @throws RegistryAuthenticationFailedException if authentication fails
   * @throws RegistryCredentialsNotSentException if authentication failed and credentials were not
   *     sent over plain HTTP
   */
  public Authorization authenticatePush(@Nullable Credential credential)
      throws RegistryAuthenticationFailedException, RegistryCredentialsNotSentException {
    return authenticate(credential, "pull,push");
  }

  private String getServiceScopeRequestParameters(Map<String, String> repositoryScopes) {
    StringBuilder parameters = new StringBuilder("service=").append(service);
    for (Map.Entry<String, String> pair : repositoryScopes.entrySet()) {
      parameters
          .append("&scope=repository:")
          .append(pair.getKey())
          .append(":")
          .append(pair.getValue());
    }
    return parameters.toString();
  }

  @VisibleForTesting
  URL getAuthenticationUrl(@Nullable Credential credential, Map<String, String> repositoryScopes)
      throws MalformedURLException {
    return isOAuth2Auth(credential)
        ? new URL(realm) // Required parameters will be sent via POST .
        : new URL(realm + "?" + getServiceScopeRequestParameters(repositoryScopes));
  }

  @VisibleForTesting
  String getAuthRequestParameters(
      @Nullable Credential credential, Map<String, String> repositoryScopes) {
    String serviceScope = getServiceScopeRequestParameters(repositoryScopes);
    return isOAuth2Auth(credential)
        ? serviceScope
            // https://github.com/GoogleContainerTools/jib/pull/1545
            + "&client_id=jib.da031fe481a93ac107a95a96462358f9"
            + "&grant_type=refresh_token&refresh_token="
            // If OAuth2, credential.getPassword() is a refresh token.
            + Verify.verifyNotNull(credential).getPassword()
        : serviceScope;
  }

  @VisibleForTesting
  boolean isOAuth2Auth(@Nullable Credential credential) {
    return credential != null && credential.isOAuth2RefreshToken();
  }

  /**
   * Sends the authentication request and retrieves the Bearer authorization token.
   *
   * @param credential the credential used to authenticate
   * @param scope the scope of permissions to authenticate for
   * @return the {@link Authorization} response
   * @throws RegistryAuthenticationFailedException if authentication fails
   * @throws RegistryCredentialsNotSentException if authentication is failed and credentials were
   *     not sent over plain HTTP
   * @see <a
   *     href="https://docs.docker.com/registry/spec/auth/token/#how-to-authenticate">https://docs.docker.com/registry/spec/auth/token/#how-to-authenticate</a>
   */
  private Authorization authenticate(@Nullable Credential credential, String scope)
      throws RegistryAuthenticationFailedException, RegistryCredentialsNotSentException {
    // try authorizing against both the main repository and the source repository too
    // to enable cross-repository mounts on pushes
    String sourceImageName = registryEndpointRequestProperties.getSourceImageName();
    String imageName = registryEndpointRequestProperties.getImageName();
    if (sourceImageName != null && !sourceImageName.equals(imageName)) {
      try {
        return authenticate(credential, ImmutableMap.of(imageName, scope, sourceImageName, "pull"));
      } catch (RegistryAuthenticationFailedException ex) {
        // Unable to obtain authorization with source image: fall through and try without
      }
    }
    return authenticate(credential, ImmutableMap.of(imageName, scope));
  }

  private Authorization authenticate(
      @Nullable Credential credential, Map<String, String> repositoryScopes)
      throws RegistryAuthenticationFailedException, RegistryCredentialsNotSentException {
    String registryUrl = registryEndpointRequestProperties.getServerUrl();
    String imageName = registryEndpointRequestProperties.getImageName();
    try {
      URL url = getAuthenticationUrl(credential, repositoryScopes);

      Request.Builder requestBuilder =
          Request.builder()
              .setHttpTimeout(JibSystemProperties.getHttpTimeout())
              .setUserAgent(userAgent);

      if (isOAuth2Auth(credential)) {
        String parameters = getAuthRequestParameters(credential, repositoryScopes);
        requestBuilder.setBody(
            new BlobHttpContent(Blobs.from(parameters), MediaType.FORM_DATA.toString()));
      } else if (credential != null) {
        requestBuilder.setAuthorization(
            Authorization.fromBasicCredentials(credential.getUsername(), credential.getPassword()));
      }

      String httpMethod = isOAuth2Auth(credential) ? HttpMethods.POST : HttpMethods.GET;
      try (Response response = httpClient.call(httpMethod, url, requestBuilder.build())) {

        AuthenticationResponseTemplate responseJson =
            JsonTemplateMapper.readJson(response.getBody(), AuthenticationResponseTemplate.class);

        if (responseJson.getToken() == null) {
          throw new RegistryAuthenticationFailedException(
              registryUrl,
              imageName,
              "Did not get token in authentication response from "
                  + getAuthenticationUrl(credential, repositoryScopes)
                  + "; parameters: "
                  + getAuthRequestParameters(credential, repositoryScopes));
        }
        return Authorization.fromBearerToken(responseJson.getToken());
      }

    } catch (ResponseException ex) {
      if (ex.getStatusCode() == HttpStatusCodes.STATUS_CODE_UNAUTHORIZED
          && ex.requestAuthorizationCleared()) {
        throw new RegistryCredentialsNotSentException(registryUrl, imageName);
      }
      throw new RegistryAuthenticationFailedException(registryUrl, imageName, ex);

    } catch (IOException ex) {
      throw new RegistryAuthenticationFailedException(registryUrl, imageName, ex);
    }
  }
}
