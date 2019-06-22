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

package com.google.cloud.tools.jib.http;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.api.client.util.Base64;
import com.google.cloud.tools.jib.json.JsonTemplate;
import com.google.cloud.tools.jib.json.JsonTemplateMapper;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Multimap;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import javax.annotation.Nullable;

/**
 * Holds the credentials for an HTTP {@code Authorization} header.
 *
 * <p>The HTTP {@code Authorization} header is in the format:
 *
 * <pre>{@code Authorization: <scheme> <token>}</pre>
 */
public class Authorization {

  /**
   * @param username the username
   * @param secret the secret
   * @return an {@link Authorization} with a {@code Basic} credentials
   */
  public static Authorization fromBasicCredentials(String username, String secret) {
    String credentials = username + ":" + secret;
    String token = Base64.encodeBase64String(credentials.getBytes(StandardCharsets.UTF_8));
    return new Authorization("Basic", token, null);
  }

  /**
   * @param token the token
   * @return an {@link Authorization} with a base64-encoded {@code username:password} string
   */
  public static Authorization fromBasicToken(String token) {
    return new Authorization("Basic", token, null);
  }

  /**
   * @param token the token
   * @return an {@link Authorization} with a {@code Bearer} token
   */
  public static Authorization fromBearerToken(String token) {
    return new Authorization("Bearer", token, decodeTokenRepositoryGrants(token));
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

  private final String scheme;
  private final String token;

  /**
   * If token is a Docker Registry Bearer Token, then {@link #repositoryGrants} will contain a map
   * of repository to the access grant information extracted from the token. Otherwise, it must be
   * {@code null}, indicating that access to all repositories are permitted.
   */
  @Nullable private final Multimap<String, String> repositoryGrants;

  private Authorization(
      String scheme, String token, @Nullable Multimap<String, String> repositoryGrants) {
    this.scheme = scheme;
    this.token = token;
    this.repositoryGrants = repositoryGrants;
  }

  public String getScheme() {
    return scheme;
  }

  public String getToken() {
    return token;
  }

  /** Return the HTTP {@link Authorization} header value. */
  @Override
  public String toString() {
    return scheme + " " + token;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof Authorization)) {
      return false;
    }
    Authorization otherAuthorization = (Authorization) other;
    return scheme.equals(otherAuthorization.scheme) && token.equals(otherAuthorization.token);
  }

  @Override
  public int hashCode() {
    return Objects.hash(scheme, token);
  }

  /**
   * Check if this authorization allows accessing the specified repository.
   *
   * @param repository repository in question
   * @param access the access scope ("push" or "pull")
   * @return true if the repository was covered
   */
  public boolean canAccess(String repository, String access) {
    // if null then we assume that all repositories are granted
    return repositoryGrants == null || repositoryGrants.containsEntry(repository, access);
  }

  /**
   * A simple class to represent a Docker Registry Bearer Token payload.
   *
   * <pre>
   * {"access":[{"type": "repository","name": "library/openjdk","actions":["push","pull"]}]}
   * </pre>
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
}
