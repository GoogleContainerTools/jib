/*
 * Copyright 2018 Google LLC. All rights reserved.
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

import com.google.cloud.tools.jib.builder.BuildLogger;
import com.google.cloud.tools.jib.configuration.Port.Protocol;
import com.google.cloud.tools.jib.image.ImageReference;
import com.google.cloud.tools.jib.image.json.BuildableManifestTemplate;
import com.google.cloud.tools.jib.image.json.OCIManifestTemplate;
import com.google.cloud.tools.jib.image.json.V22ManifestTemplate;
import com.google.cloud.tools.jib.registry.credentials.RegistryCredentials;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

public class BuildConfigurationTest {

  @Test
  public void testBuilder() {
    String expectedBaseImageServerUrl = "someserver";
    String expectedBaseImageName = "baseimage";
    String expectedBaseImageTag = "baseimagetag";
    String expectedBaseImageCredentialHelperName = "credentialhelper";
    RegistryCredentials expectedKnownBaseRegistryCredentials =
        Mockito.mock(RegistryCredentials.class);
    String expectedTargetServerUrl = "someotherserver";
    String expectedTargetImageName = "targetimage";
    String expectedTargetTag = "targettag";
    String expectedTargetImageCredentialHelperName = "anotherCredentialHelper";
    RegistryCredentials expectedKnownTargetRegistryCredentials =
        Mockito.mock(RegistryCredentials.class);
    Instant expectedCreationTime = Instant.ofEpochSecond(10000);
    List<String> expectedEntrypoint = Arrays.asList("some", "entrypoint");
    List<String> expectedJavaArguments = Arrays.asList("arg1", "arg2");
    Map<String, String> expectedEnvironment = ImmutableMap.of("key", "value");
    ImmutableList<Port> expectedExposedPorts =
        ImmutableList.of(new Port(1000, Protocol.TCP), new Port(2000, Protocol.TCP));
    Class<? extends BuildableManifestTemplate> expectedTargetFormat = OCIManifestTemplate.class;
    CacheConfiguration expectedApplicationLayersCacheConfiguration =
        CacheConfiguration.forPath(Paths.get("application/layers"));
    CacheConfiguration expectedBaseImageLayersCacheConfiguration =
        CacheConfiguration.forPath(Paths.get("base/image/layers"));
    List<LayerConfiguration> expectedLayerConfigurations =
        Collections.singletonList(
            LayerConfiguration.builder().addEntry(Collections.emptyList(), "destination").build());

    ImageConfiguration baseImageConfiguration =
        ImageConfiguration.builder()
            .setImage(
                ImageReference.of(
                    expectedBaseImageServerUrl, expectedBaseImageName, expectedBaseImageTag))
            .setCredentialHelper(expectedBaseImageCredentialHelperName)
            .setKnownRegistryCredentials(expectedKnownBaseRegistryCredentials)
            .build();
    ImageConfiguration targetImageConfiguration =
        ImageConfiguration.builder()
            .setImage(
                ImageReference.of(
                    expectedTargetServerUrl, expectedTargetImageName, expectedTargetTag))
            .setCredentialHelper(expectedTargetImageCredentialHelperName)
            .setKnownRegistryCredentials(expectedKnownTargetRegistryCredentials)
            .build();
    ContainerConfiguration containerConfiguration =
        ContainerConfiguration.builder()
            .setCreationTime(expectedCreationTime)
            .setEntrypoint(expectedEntrypoint)
            .setProgramArguments(expectedJavaArguments)
            .setEnvironment(expectedEnvironment)
            .setExposedPorts(expectedExposedPorts)
            .setTargetFormat(OCIManifestTemplate.class)
            .build();
    BuildConfiguration.Builder buildConfigurationBuilder =
        BuildConfiguration.builder(Mockito.mock(BuildLogger.class))
            .setBaseImageConfiguration(baseImageConfiguration)
            .setTargetImageConfiguration(targetImageConfiguration)
            .setContainerConfiguration(containerConfiguration)
            .setApplicationLayersCacheConfiguration(expectedApplicationLayersCacheConfiguration)
            .setBaseImageLayersCacheConfiguration(expectedBaseImageLayersCacheConfiguration)
            .setAllowInsecureRegistries(true)
            .setLayerConfigurations(expectedLayerConfigurations);
    BuildConfiguration buildConfiguration = buildConfigurationBuilder.build();

    Assert.assertEquals(expectedCreationTime, buildConfiguration.getCreationTime());
    Assert.assertEquals(expectedBaseImageServerUrl, buildConfiguration.getBaseImageRegistry());
    Assert.assertEquals(expectedBaseImageName, buildConfiguration.getBaseImageRepository());
    Assert.assertEquals(expectedBaseImageTag, buildConfiguration.getBaseImageTag());
    Assert.assertEquals(
        expectedBaseImageCredentialHelperName,
        buildConfiguration.getBaseImageCredentialHelperName());
    Assert.assertEquals(expectedTargetServerUrl, buildConfiguration.getTargetImageRegistry());
    Assert.assertEquals(expectedTargetImageName, buildConfiguration.getTargetImageRepository());
    Assert.assertEquals(expectedTargetTag, buildConfiguration.getTargetImageTag());
    Assert.assertEquals(
        expectedTargetImageCredentialHelperName,
        buildConfiguration.getTargetImageCredentialHelperName());
    Assert.assertEquals(expectedJavaArguments, buildConfiguration.getMainArguments());
    Assert.assertEquals(expectedEnvironment, buildConfiguration.getEnvironment());
    Assert.assertEquals(expectedExposedPorts, buildConfiguration.getExposedPorts());
    Assert.assertEquals(expectedTargetFormat, buildConfiguration.getTargetFormat());
    Assert.assertEquals(
        expectedApplicationLayersCacheConfiguration,
        buildConfiguration.getApplicationLayersCacheConfiguration());
    Assert.assertEquals(
        expectedBaseImageLayersCacheConfiguration,
        buildConfiguration.getBaseImageLayersCacheConfiguration());
    Assert.assertTrue(buildConfiguration.getAllowInsecureRegistries());
    Assert.assertEquals(expectedLayerConfigurations, buildConfiguration.getLayerConfigurations());
    Assert.assertEquals(expectedEntrypoint, buildConfiguration.getEntrypoint());
  }

  @Test
  public void testBuilder_default() {
    // These are required and don't have defaults.
    String expectedBaseImageServerUrl = "someserver";
    String expectedBaseImageName = "baseimage";
    String expectedBaseImageTag = "baseimagetag";
    String expectedTargetServerUrl = "someotherserver";
    String expectedTargetImageName = "targetimage";
    String expectedTargetTag = "targettag";

    ImageConfiguration baseImageConfiguration =
        ImageConfiguration.builder()
            .setImage(
                ImageReference.of(
                    expectedBaseImageServerUrl, expectedBaseImageName, expectedBaseImageTag))
            .build();
    ImageConfiguration targetImageConfiguration =
        ImageConfiguration.builder()
            .setImage(
                ImageReference.of(
                    expectedTargetServerUrl, expectedTargetImageName, expectedTargetTag))
            .build();
    BuildConfiguration buildConfiguration =
        BuildConfiguration.builder(Mockito.mock(BuildLogger.class))
            .setBaseImageConfiguration(baseImageConfiguration)
            .setTargetImageConfiguration(targetImageConfiguration)
            .build();

    Assert.assertEquals(buildConfiguration.getCreationTime(), Instant.EPOCH);
    Assert.assertNull(buildConfiguration.getBaseImageCredentialHelperName());
    Assert.assertNull(buildConfiguration.getKnownBaseRegistryCredentials());
    Assert.assertNull(buildConfiguration.getTargetImageCredentialHelperName());
    Assert.assertNull(buildConfiguration.getKnownTargetRegistryCredentials());
    Assert.assertNull(buildConfiguration.getMainArguments());
    Assert.assertNull(buildConfiguration.getEnvironment());
    Assert.assertNull(buildConfiguration.getExposedPorts());
    Assert.assertEquals(V22ManifestTemplate.class, buildConfiguration.getTargetFormat());
    Assert.assertNull(buildConfiguration.getApplicationLayersCacheConfiguration());
    Assert.assertNull(buildConfiguration.getBaseImageLayersCacheConfiguration());
    Assert.assertFalse(buildConfiguration.getAllowInsecureRegistries());
    Assert.assertEquals(Collections.emptyList(), buildConfiguration.getLayerConfigurations());
    Assert.assertNull(buildConfiguration.getEntrypoint());
  }

  @Test
  public void testBuilder_missingValues() {
    // Target image is missing
    try {
      BuildConfiguration.builder(Mockito.mock(BuildLogger.class))
          .setBaseImageConfiguration(
              ImageConfiguration.builder().setImage(Mockito.mock(ImageReference.class)).build())
          .build();
      Assert.fail("Build configuration should not be built with missing values");

    } catch (IllegalStateException ex) {
      Assert.assertEquals("target image is required but not set", ex.getMessage());
    }

    // All required fields missing
    try {
      BuildConfiguration.builder(Mockito.mock(BuildLogger.class)).build();
      Assert.fail("Build configuration should not be built with missing values");

    } catch (IllegalStateException ex) {
      Assert.assertEquals(
          "base image is required but not set and target image is required but not set",
          ex.getMessage());
    }
  }

  @Test
  @SuppressWarnings("JdkObsolete")
  public void testBuilder_nullValues() {
    // Java arguments element should not be null.
    try {
      BuildConfiguration.builder(Mockito.mock(BuildLogger.class))
          .setContainerConfiguration(
              ContainerConfiguration.builder()
                  .setProgramArguments(Arrays.asList("first", null))
                  .build());
      Assert.fail("The IllegalArgumentException should be thrown.");
    } catch (IllegalArgumentException ex) {
      Assert.assertNull(ex.getMessage());
    }

    // Entrypoint element should not be null.
    try {
      BuildConfiguration.builder(Mockito.mock(BuildLogger.class))
          .setContainerConfiguration(
              ContainerConfiguration.builder().setEntrypoint(Arrays.asList("first", null)).build());
      Assert.fail("The IllegalArgumentException should be thrown.");
    } catch (IllegalArgumentException ex) {
      Assert.assertNull(ex.getMessage());
    }

    // Exposed ports element should not be null.
    try {
      BuildConfiguration.builder(Mockito.mock(BuildLogger.class))
          .setContainerConfiguration(
              ContainerConfiguration.builder()
                  .setExposedPorts(Arrays.asList(new Port(1000, Protocol.TCP), null))
                  .build());
      Assert.fail("The IllegalArgumentException should be thrown.");
    } catch (IllegalArgumentException ex) {
      Assert.assertNull(ex.getMessage());
    }

    // Environment keys element should not be null.
    Map<String, String> nullKeyMap = new HashMap<>();
    nullKeyMap.put(null, "value");

    try {
      BuildConfiguration.builder(Mockito.mock(BuildLogger.class))
          .setContainerConfiguration(
              ContainerConfiguration.builder().setEnvironment(nullKeyMap).build());
      Assert.fail("The IllegalArgumentException should be thrown.");
    } catch (IllegalArgumentException ex) {
      Assert.assertNull(ex.getMessage());
    }

    // Environment values element should not be null.
    Map<String, String> nullValueMap = new HashMap<>();
    nullValueMap.put("key", null);
    try {
      BuildConfiguration.builder(Mockito.mock(BuildLogger.class))
          .setContainerConfiguration(
              ContainerConfiguration.builder().setEnvironment(nullValueMap).build());
      Assert.fail("The IllegalArgumentException should be thrown.");
    } catch (IllegalArgumentException ex) {
      Assert.assertNull(ex.getMessage());
    }

    // Can accept empty environment.
    BuildConfiguration.builder(Mockito.mock(BuildLogger.class))
        .setContainerConfiguration(
            ContainerConfiguration.builder().setEnvironment(ImmutableMap.of()).build());

    // Environment map can accept TreeMap and Hashtable.
    BuildConfiguration.builder(Mockito.mock(BuildLogger.class))
        .setContainerConfiguration(
            ContainerConfiguration.builder().setEnvironment(new TreeMap<>()).build());
    BuildConfiguration.builder(Mockito.mock(BuildLogger.class))
        .setContainerConfiguration(
            ContainerConfiguration.builder().setEnvironment(new Hashtable<>()).build());
  }

  @Test
  public void testValidJavaClassRegex() {
    Assert.assertTrue(BuildConfiguration.isValidJavaClass("my.Class"));
    Assert.assertTrue(BuildConfiguration.isValidJavaClass("my.java_Class$valid"));
    Assert.assertTrue(BuildConfiguration.isValidJavaClass("multiple.package.items"));
    Assert.assertTrue(BuildConfiguration.isValidJavaClass("is123.valid"));
    Assert.assertFalse(BuildConfiguration.isValidJavaClass("${start-class}"));
    Assert.assertFalse(BuildConfiguration.isValidJavaClass("123not.Valid"));
    Assert.assertFalse(BuildConfiguration.isValidJavaClass("{class}"));
    Assert.assertFalse(BuildConfiguration.isValidJavaClass("not valid"));
  }
}
