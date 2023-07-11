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

package com.google.cloud.tools.jib.configuration;

import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.buildplan.Platform;
import com.google.cloud.tools.jib.api.buildplan.Port;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.junit.Assert;
import org.junit.jupiter.api.Test;

/** Tests for {@link ContainerConfiguration}. */
class ContainerConfigurationTest {

  @Test
  void testBuilder_nullValues() {
    // Java arguments element should not be null.
    try {
      ContainerConfiguration.builder().setProgramArguments(Arrays.asList("first", null));
      Assert.fail();
    } catch (IllegalArgumentException ex) {
      Assert.assertEquals("program arguments list contains null elements", ex.getMessage());
    }

    // Entrypoint element should not be null.
    try {
      ContainerConfiguration.builder().setEntrypoint(Arrays.asList("first", null));
      Assert.fail();
    } catch (IllegalArgumentException ex) {
      Assert.assertEquals("entrypoint contains null elements", ex.getMessage());
    }

    // Exposed ports element should not be null.
    Set<Port> badPorts = new HashSet<>(Arrays.asList(Port.tcp(1000), null));
    try {
      ContainerConfiguration.builder().setExposedPorts(badPorts);
      Assert.fail();
    } catch (IllegalArgumentException ex) {
      Assert.assertEquals("ports list contains null elements", ex.getMessage());
    }

    // Volume element should not be null.
    Set<AbsoluteUnixPath> badVolumes =
        new HashSet<>(Arrays.asList(AbsoluteUnixPath.get("/"), null));
    try {
      ContainerConfiguration.builder().setVolumes(badVolumes);
      Assert.fail();
    } catch (IllegalArgumentException ex) {
      Assert.assertEquals("volumes list contains null elements", ex.getMessage());
    }

    Map<String, String> nullKeyMap = new HashMap<>();
    nullKeyMap.put(null, "value");
    Map<String, String> nullValueMap = new HashMap<>();
    nullValueMap.put("key", null);

    // Label keys should not be null.
    try {
      ContainerConfiguration.builder().setLabels(nullKeyMap);
      Assert.fail();
    } catch (IllegalArgumentException ex) {
      Assert.assertEquals("labels map contains null keys", ex.getMessage());
    }

    // Labels values should not be null.
    try {
      ContainerConfiguration.builder().setLabels(nullValueMap);
      Assert.fail();
    } catch (IllegalArgumentException ex) {
      Assert.assertEquals("labels map contains null values", ex.getMessage());
    }

    // Environment keys should not be null.
    try {
      ContainerConfiguration.builder().setEnvironment(nullKeyMap);
      Assert.fail();
    } catch (IllegalArgumentException ex) {
      Assert.assertEquals("environment map contains null keys", ex.getMessage());
    }

    // Environment values should not be null.
    try {
      ContainerConfiguration.builder().setEnvironment(nullValueMap);
      Assert.fail();
    } catch (IllegalArgumentException ex) {
      Assert.assertEquals("environment map contains null values for key(s): key", ex.getMessage());
    }
  }

  @Test
  @SuppressWarnings("JdkObsolete") // for hashtable
  void testBuilder_environmentMapTypes() {
    // Can accept empty environment.
    Assert.assertNotNull(
        ContainerConfiguration.builder().setEnvironment(ImmutableMap.of()).build());
    // Can handle other map types (https://github.com/GoogleContainerTools/jib/issues/632)
    Assert.assertNotNull(ContainerConfiguration.builder().setEnvironment(new TreeMap<>()));
    Assert.assertNotNull(ContainerConfiguration.builder().setEnvironment(new Hashtable<>()));
  }

  @Test
  void testBuilder_user() {
    ContainerConfiguration configuration = ContainerConfiguration.builder().setUser("john").build();
    Assert.assertEquals("john", configuration.getUser());
  }

  @Test
  void testBuilder_workingDirectory() {
    ContainerConfiguration configuration =
        ContainerConfiguration.builder().setWorkingDirectory(AbsoluteUnixPath.get("/path")).build();
    Assert.assertEquals(AbsoluteUnixPath.get("/path"), configuration.getWorkingDirectory());
  }

  @Test
  void testSetPlatforms_emptySet() {
    try {
      ContainerConfiguration.builder().setPlatforms(Collections.emptySet()).build();
      Assert.fail();
    } catch (IllegalArgumentException ex) {
      Assert.assertEquals("platforms set cannot be empty", ex.getMessage());
    }
  }

  @Test
  void testAddPlatform_duplicatePlatforms() {
    ContainerConfiguration configuration =
        ContainerConfiguration.builder()
            .addPlatform("testArchitecture", "testOS")
            .addPlatform("testArchitecture", "testOS")
            .build();
    Assert.assertEquals(
        ImmutableSet.of(new Platform("amd64", "linux"), new Platform("testArchitecture", "testOS")),
        configuration.getPlatforms());
  }
}
