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

import com.google.common.collect.ImmutableMap;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.Assert;
import org.junit.Test;

/** Tests for {@link ContainerConfiguration}. */
public class ContainerConfigurationTest {

  @Test
  public void testBuilder_nullValues() {
    // Java arguments element should not be null.
    try {
      ContainerConfiguration.builder().setProgramArguments(Arrays.asList("first", null));
      Assert.fail("The IllegalArgumentException should be thrown.");
    } catch (IllegalArgumentException ex) {
      Assert.assertNull(ex.getMessage());
    }

    // Entrypoint element should not be null.
    try {
      ContainerConfiguration.builder().setEntrypoint(Arrays.asList("first", null));
      Assert.fail("The IllegalArgumentException should be thrown.");
    } catch (IllegalArgumentException ex) {
      Assert.assertNull(ex.getMessage());
    }

    // Exposed ports element should not be null.
    List<Port> badPorts = Arrays.asList(Port.tcp(1000), null);
    try {
      ContainerConfiguration.builder().setExposedPorts(badPorts);
      Assert.fail("The IllegalArgumentException should be thrown.");
    } catch (IllegalArgumentException ex) {
      Assert.assertNull(ex.getMessage());
    }

    // Labels element should not be null.
    Map<String, String> badLabels = new HashMap<>();
    badLabels.put("label-key", null);
    try {
      ContainerConfiguration.builder().setLabels(badLabels);
      Assert.fail("The IllegalArgumentException should be thrown.");
    } catch (IllegalArgumentException ex) {
      Assert.assertNull(ex.getMessage());
    }

    // Environment keys element should not be null.
    Map<String, String> nullKeyMap = new HashMap<>();
    nullKeyMap.put(null, "value");

    try {
      ContainerConfiguration.builder().setEnvironment(nullKeyMap);
      Assert.fail("The IllegalArgumentException should be thrown.");
    } catch (IllegalArgumentException ex) {
      Assert.assertNull(ex.getMessage());
    }

    // Environment values element should not be null.
    Map<String, String> nullValueMap = new HashMap<>();
    nullValueMap.put("key", null);
    try {
      ContainerConfiguration.builder().setEnvironment(nullValueMap);
      Assert.fail("The IllegalArgumentException should be thrown.");
    } catch (IllegalArgumentException ex) {
      Assert.assertNull(ex.getMessage());
    }
  }

  @Test
  @SuppressWarnings("JdkObsolete") // for hashtable
  public void testBuilder_environmentMapTypes() {
    // Can accept empty environment.
    ContainerConfiguration.builder().setEnvironment(ImmutableMap.of()).build();

    // Can handle other map types (https://github.com/GoogleContainerTools/jib/issues/632)
    ContainerConfiguration.builder().setEnvironment(new TreeMap<>());
    ContainerConfiguration.builder().setEnvironment(new Hashtable<>());
  }
}
