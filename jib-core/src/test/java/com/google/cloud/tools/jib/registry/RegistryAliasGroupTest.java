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

import com.google.common.collect.Sets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Assert;
import org.junit.jupiter.api.Test;

/** Tests for {@link RegistryAliasGroup}. */
class RegistryAliasGroupTest {

  @Test
  void testGetAliasesGroup_noKnownAliases() {
    List<String> singleton = RegistryAliasGroup.getAliasesGroup("something.gcr.io");
    Assert.assertEquals(1, singleton.size());
    Assert.assertEquals("something.gcr.io", singleton.get(0));
  }

  @Test
  void testGetAliasesGroup_dockerHub() {
    Set<String> aliases =
        Sets.newHashSet(
            "registry.hub.docker.com", "index.docker.io", "registry-1.docker.io", "docker.io");
    for (String alias : aliases) {
      Assert.assertEquals(aliases, new HashSet<>(RegistryAliasGroup.getAliasesGroup(alias)));
    }
  }

  @Test
  void testGetHost_noAlias() {
    String host = RegistryAliasGroup.getHost("something.gcr.io");
    Assert.assertEquals("something.gcr.io", host);
  }

  @Test
  void testGetHost_dockerIo() {
    String host = RegistryAliasGroup.getHost("docker.io");
    Assert.assertEquals("registry-1.docker.io", host);
  }
}
