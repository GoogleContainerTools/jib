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

package com.google.cloud.tools.jib.registry.credentials.json;

import com.google.cloud.tools.jib.json.JsonTemplate;
import com.google.common.annotations.VisibleForTesting;
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
 *       "auth": "username:password in base64"
 *     },
 *     "anotherregistry": {},
 *     ...
 *   },
 *   "credsStore": "credential helper name",
 *   "credHelpers": {
 *     "registry": "credential helper name",
 *     "anotherregistry": "another credential helper name",
 *     ...
 *   }
 * }
 * }</pre>
 *
 * If an {@code auth} is defined for a registry, that is a valid {@code Basic} authorization to use
 * for that registry.
 *
 * <p>If {@code credsStore} is defined, is a credential helper that stores authorizations for all
 * registries listed under {@code auths}.
 *
 * <p>Each entry in {@code credHelpers} is a mapping from a registry to a credential helper that
 * stores the authorization for that registry.
 *
 * @see <a
 *     href="https://www.projectatomic.io/blog/2016/03/docker-credentials-store/">https://www.projectatomic.io/blog/2016/03/docker-credentials-store/</a>
 */
public class DockerConfigTemplate implements JsonTemplate {

  /** Template for an {@code auth} defined for a registry under {@code auths}. */
  private static class AuthTemplate implements JsonTemplate {

    private String auth;
  }

  /** Maps from registry to its {@link AuthTemplate}. */
  private final Map<String, AuthTemplate> auths = new HashMap<>();

  private String credsStore;

  /** Maps from registry to credential helper name. */
  private final Map<String, String> credHelpers = new HashMap<>();

  /**
   * @return the base64-encoded {@code Basic} authorization for {@code registry}, or {@code null} if
   *     none exists
   */
  @Nullable
  public String getAuthFor(String registry) {
    if (!auths.containsKey(registry)) {
      return null;
    }
    return auths.get(registry).auth;
  }

  /**
   * @return {@code credsStore} if {@code registry} is present in {@code auths}; otherwise, searches
   *     {@code credHelpers}; otherwise, {@code null} if not found
   */
  @Nullable
  public String getCredentialHelperFor(String registry) {
    if (credsStore != null && auths.containsKey(registry)) {
      return credsStore;
    }
    if (credHelpers.containsKey(registry)) {
      return credHelpers.get(registry);
    }
    return null;
  }

  @VisibleForTesting
  DockerConfigTemplate addAuth(String registry, @Nullable String auth) {
    AuthTemplate authTemplate = new AuthTemplate();
    authTemplate.auth = auth;
    auths.put(registry, authTemplate);
    return this;
  }

  @VisibleForTesting
  DockerConfigTemplate setCredsStore(String credsStore) {
    this.credsStore = credsStore;
    return this;
  }

  @VisibleForTesting
  DockerConfigTemplate addCredHelper(String registry, String credHelper) {
    credHelpers.put(registry, credHelper);
    return this;
  }
}
