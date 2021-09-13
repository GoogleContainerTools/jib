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

package com.google.cloud.tools.jib.registry.credentials;

import com.google.cloud.tools.jib.registry.credentials.json.DockerConfigTemplate;
import com.google.cloud.tools.jib.registry.credentials.json.DockerConfigTemplate.AuthTemplate;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import javax.annotation.Nullable;

/** Handles getting useful information from a {@link DockerConfigTemplate}. */
class DockerConfig {

  /**
   * Returns the first entry matching the given key predicates (short-circuiting in the order of
   * predicates).
   */
  @Nullable
  private static <K, T> Map.Entry<K, T> findFirstInMapByKey(
      Map<K, T> map, List<Predicate<K>> keyMatches) {
    return keyMatches.stream()
        .map(keyMatch -> findFirstInMapByKey(map, keyMatch))
        .filter(Objects::nonNull)
        .findFirst()
        .orElse(null);
  }

  /** Returns the first entry matching the given key predicate. */
  @Nullable
  private static <K, T> Map.Entry<K, T> findFirstInMapByKey(Map<K, T> map, Predicate<K> keyMatch) {
    return map.entrySet().stream()
        .filter(entry -> keyMatch.test(entry.getKey()))
        .findFirst()
        .orElse(null);
  }

  private final DockerConfigTemplate dockerConfigTemplate;

  DockerConfig(DockerConfigTemplate dockerConfigTemplate) {
    this.dockerConfigTemplate = dockerConfigTemplate;
  }

  /**
   * Returns the base64-encoded {@code Basic} authorization for {@code registry}, or {@code null} if
   * none exists. The order of lookup preference:
   *
   * <ol>
   *   <li>Exact registry name
   *   <li>https:// + registry name
   *   <li>registry name + / + arbitrary suffix
   *   <li>https:// + registry name + / arbitrary suffix
   * </ol>
   *
   * @param registry the registry to get the authorization for
   * @return the base64-encoded {@code Basic} authorization for {@code registry}, or {@code null} if
   *     none exists
   */
  @Nullable
  AuthTemplate getAuthFor(String registry) {
    Map.Entry<String, AuthTemplate> authEntry =
        findFirstInMapByKey(dockerConfigTemplate.getAuths(), getRegistryMatchersFor(registry));
    return authEntry != null ? authEntry.getValue() : null;
  }

  /**
   * Determines a {@link DockerCredentialHelper} to use for {@code registry}.
   *
   * <p>If there exists a matching registry entry (or its aliases) in {@code credHelpers}, returns
   * the corresponding credential helper. Otherwise, returns the credential helper defined by {@code
   * credStore}.
   *
   * <p>See {@link #getRegistryMatchersFor} for the alias lookup order.
   *
   * @param registry the registry to get the credential helpers for
   * @return the {@link DockerCredentialHelper} or {@code null} if none is found for the given
   *     registry
   */
  @Nullable
  DockerCredentialHelper getCredentialHelperFor(String registry) {
    List<Predicate<String>> registryMatchers = getRegistryMatchersFor(registry);

    Map.Entry<String, String> firstCredHelperMatch =
        findFirstInMapByKey(dockerConfigTemplate.getCredHelpers(), registryMatchers);
    if (firstCredHelperMatch != null) {
      return new DockerCredentialHelper(
          firstCredHelperMatch.getKey(),
          Paths.get("docker-credential-" + firstCredHelperMatch.getValue()));
    }

    if (dockerConfigTemplate.getCredsStore() != null) {
      return new DockerCredentialHelper(
          registry, Paths.get("docker-credential-" + dockerConfigTemplate.getCredsStore()));
    }

    return null;
  }

  /**
   * Gets registry matchers for a registry.
   *
   * <p>Matches are determined in the following order:
   *
   * <ol>
   *   <li>Exact registry name
   *   <li>https:// + registry name
   *   <li>registry name + / + arbitrary suffix
   *   <li>https:// + registry name + / + arbitrary suffix
   * </ol>
   *
   * @param registry the registry to get matchers for
   * @return the list of predicates to match possible aliases
   */
  private List<Predicate<String>> getRegistryMatchersFor(String registry) {
    Predicate<String> exactMatch = registry::equals;
    Predicate<String> withHttps = ("https://" + registry)::equals;
    Predicate<String> withSuffix = name -> name.startsWith(registry + "/");
    Predicate<String> withHttpsAndSuffix = name -> name.startsWith("https://" + registry + "/");
    return Arrays.asList(exactMatch, withHttps, withSuffix, withHttpsAndSuffix);
  }
}
