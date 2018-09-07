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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Provides known aliases for a given registry. */
public class RegistryAliasGroup {

  private static final ImmutableList<ImmutableSet<String>> REGISTRY_ALIAS_GROUPS =
      ImmutableList.of(
          // Docker Hub alias group
          ImmutableSet.of("registry.hub.docker.com", "index.docker.io"));

  /**
   * Returns the list of registry aliases for the given {@code registry}, including {@code registry}
   * as the first element.
   *
   * @param registry the registry for which the alias group is requested
   * @return non-empty list of registries where {@code registry} is the first element
   */
  public static List<String> getAliasesGroup(String registry) {
    for (ImmutableSet<String> aliasGroup : REGISTRY_ALIAS_GROUPS) {
      if (aliasGroup.contains(registry)) {
        // Found a group. Move the requested "registry" to the front before returning it.
        Stream<String> self = Stream.of(registry);
        Stream<String> withoutSelf = aliasGroup.stream().filter(alias -> !registry.equals(alias));
        return Stream.concat(self, withoutSelf).collect(Collectors.toList());
      }
    }

    return Collections.singletonList(registry);
  }
}
