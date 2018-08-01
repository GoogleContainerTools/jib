package com.google.cloud.tools.jib.configuration;

import static org.junit.Assert.*;

import com.google.cloud.tools.jib.configuration.Port.Protocol;
import com.google.common.collect.ImmutableMap;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.junit.Assert;
import org.junit.Test;

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
    try {
      ContainerConfiguration.builder()
          .setExposedPorts(Arrays.asList(new Port(1000, Protocol.TCP), null));
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

    // Can accept empty environment.
    ContainerConfiguration.builder().setEnvironment(ImmutableMap.of()).build();
  }
}
