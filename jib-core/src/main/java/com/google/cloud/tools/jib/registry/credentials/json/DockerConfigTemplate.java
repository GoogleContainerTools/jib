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

package com.google.cloud.tools.jib.registry.credentials.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.cloud.tools.jib.json.JsonTemplate;
import com.google.common.collect.ImmutableMap;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Template for a Docker config file.
 *
 * <p>Example:
 *
 * <pre>{@code
 * {
 *   "auths": {
 *     "registry": {
 *       "auth": "username:password string in base64",
 *       "identityToken": "..."
 *     },
 *     "anotherregistry": {},
 *     ...
 *   },
 *   "credsStore": "legacy credential helper config acting as a \"default\" helper",
 *   "credHelpers": {
 *     "registry": "credential helper name",
 *     "anotherregistry": "another credential helper name",
 *     ...
 *   }
 * }
 * }</pre>
 *
 * <p>Each entry in {@code credHelpers} is a mapping from a registry to a credential helper that
 * stores the authorization for that registry. This takes precedence over {@code credsStore} if
 * there exists a match.
 *
 * <p>{@code credsStore} is a legacy config that acts to provide a "default" credential helper if
 * there is no match in {@code credHelpers}.
 *
 * <p>If an {@code auth} is defined for a registry, that is a valid {@code Basic} authorization to
 * use for that registry.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DockerConfigTemplate implements JsonTemplate {

  /** Template for an {@code auth} defined for a registry under {@code auths}. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class AuthTemplate implements JsonTemplate {

    @Nullable private String auth;

    // Both "identitytoken" and "identityToken" have been observed. For example,
    // https://github.com/GoogleContainerTools/jib/issues/2488
    // https://github.com/spotify/docker-client/issues/580
    @Nullable private String identityToken;

    @Nullable
    public String getAuth() {
      return auth;
    }

    @Nullable
    public String getIdentityToken() {
      return identityToken;
    }
  }

  /** Maps from registry to its {@link AuthTemplate}. */
  private final Map<String, AuthTemplate> auths;

  @Nullable private String credsStore;

  /** Maps from registry to credential helper name. */
  private final Map<String, String> credHelpers = new HashMap<>();

  public DockerConfigTemplate(Map<String, AuthTemplate> auths) {
    this.auths = ImmutableMap.copyOf(auths);
  }

  @SuppressWarnings("unused")
  private DockerConfigTemplate() {
    auths = new HashMap<>();
  }

  public Map<String, AuthTemplate> getAuths() {
    return auths;
  }

  @Nullable
  public String getCredsStore() {
    return credsStore;
  }

  public Map<String, String> getCredHelpers() {
    return credHelpers;
  }
}
