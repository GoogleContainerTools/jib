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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.cloud.tools.jib.blob.Blobs;
import com.google.cloud.tools.jib.http.Authorization;
import com.google.cloud.tools.jib.http.Authorizations;
import com.google.cloud.tools.jib.http.Connection;
import com.google.cloud.tools.jib.http.Request;
import com.google.cloud.tools.jib.http.Response;
import com.google.cloud.tools.jib.json.JsonTemplate;
import com.google.cloud.tools.jib.json.JsonTemplateMapper;
import com.google.common.annotations.VisibleForTesting;
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

  // TODO: Replace with a WWW-Authenticate header parser.
  /**
   * Instantiates from parsing a {@code WWW-Authenticate} header.
   *
   * @param authenticationMethod the {@code WWW-Authenticate} header value
   * @param repository the repository/image name
   * @see <a
   *     href="https://docs.docker.com/registry/spec/auth/token/#how-to-authenticate">https://docs.docker.com/registry/spec/auth/token/#how-to-authenticate</a>
   */
  @Nullable
  static RegistryAuthenticator fromAuthenticationMethod(
      String authenticationMethod, String repository)
      throws RegistryAuthenticationFailedException, MalformedURLException {
    // If the authentication method starts with 'Basic ', no registry authentication is needed.
    if (authenticationMethod.matches("^Basic .*")) {
      return null;
    }

    // Checks that the authentication method starts with 'Bearer '.
    if (!authenticationMethod.matches("^Bearer .*")) {
      throw newRegistryAuthenticationFailedException(authenticationMethod, "Bearer");
    }

    Pattern realmPattern = Pattern.compile("realm=\"(.*?)\"");
    Matcher realmMatcher = realmPattern.matcher(authenticationMethod);
    if (!realmMatcher.find()) {
      throw newRegistryAuthenticationFailedException(authenticationMethod, "realm");
    }
    String realm = realmMatcher.group(1);

    Pattern servicePattern = Pattern.compile("service=\"(.*?)\"");
    Matcher serviceMatcher = servicePattern.matcher(authenticationMethod);
    if (!serviceMatcher.find()) {
      throw newRegistryAuthenticationFailedException(authenticationMethod, "service");
    }
    String service = serviceMatcher.group(1);

    return new RegistryAuthenticator(realm, service, repository);
  }

  private static RegistryAuthenticationFailedException newRegistryAuthenticationFailedException(
      String authenticationMethod, String authParam) {
    return new RegistryAuthenticationFailedException(
        "'"
            + authParam
            + "' was not found in the 'WWW-Authenticate' header, tried to parse: "
            + authenticationMethod);
  }

  /** Template for the authentication response JSON. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  private static class AuthenticationResponseTemplate implements JsonTemplate {

    private String token;
  }

  private final String authenticationUrlBase;
  @Nullable private Authorization authorization;

  RegistryAuthenticator(String realm, String service, String repository)
      throws MalformedURLException {
    authenticationUrlBase = realm + "?service=" + service + "&scope=repository:" + repository + ":";
  }

  /** Sets an {@code Authorization} header to authenticate with. */
  public RegistryAuthenticator setAuthorization(@Nullable Authorization authorization) {
    this.authorization = authorization;
    return this;
  }

  /** Authenticates permissions to pull. */
  public Authorization authenticatePull() throws RegistryAuthenticationFailedException {
    return authenticate("pull");
  }

  /** Authenticates permission to pull and push. */
  public Authorization authenticatePush() throws RegistryAuthenticationFailedException {
    return authenticate("pull,push");
  }

  @VisibleForTesting
  URL getAuthenticationUrl(String scope) throws MalformedURLException {
    return new URL(authenticationUrlBase + scope);
  }

  /**
   * Sends the authentication request and retrieves the Bearer authorization token.
   *
   * @param scope the scope of permissions to authenticate for
   * @see <a
   *     href="https://docs.docker.com/registry/spec/auth/token/#how-to-authenticate">https://docs.docker.com/registry/spec/auth/token/#how-to-authenticate</a>
   */
  private Authorization authenticate(String scope) throws RegistryAuthenticationFailedException {
    try (Connection connection = new Connection(getAuthenticationUrl(scope))) {
      Request.Builder requestBuilder = Request.builder();
      if (authorization != null) {
        requestBuilder.setAuthorization(authorization);
      }
      Response response = connection.get(requestBuilder.build());
      String responseString = Blobs.writeToString(response.getBody());

      AuthenticationResponseTemplate responseJson =
          JsonTemplateMapper.readJson(responseString, AuthenticationResponseTemplate.class);
      return Authorizations.withBearerToken(responseJson.token);

    } catch (IOException ex) {
      throw new RegistryAuthenticationFailedException(ex);
    }
  }
}
