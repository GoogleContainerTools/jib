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

package com.google.cloud.tools.jib.builder;

import com.google.cloud.tools.jib.image.ImageReference;
import com.google.cloud.tools.jib.image.json.BuildableManifestTemplate;
import com.google.cloud.tools.jib.image.json.OCIManifestTemplate;
import com.google.cloud.tools.jib.image.json.V22ManifestTemplate;
import com.google.cloud.tools.jib.registry.credentials.RegistryCredentials;
import com.google.common.collect.ImmutableMap;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

public class BuildConfigurationTest {

  @Test
  public void testBuilder() {
    String expectedBaseImageServerUrl = "someserver";
    String expectedBaseImageName = "baseimage";
    String expectedBaseImageTag = "baseimagetag";
    String expectedTargetServerUrl = "someotherserver";
    String expectedTargetImageName = "targetimage";
    String expectedTargetTag = "targettag";
    List<String> expectedCredentialHelperNames =
        Arrays.asList("credentialhelper", "anotherCredentialHelper");
    String expectedMainClass = "mainclass";
    RegistryCredentials expectedKnownRegistryCredentials = Mockito.mock(RegistryCredentials.class);
    boolean expectedEnableReproducibleBuilds = false;
    List<String> expectedJvmFlags = Arrays.asList("some", "jvm", "flags");
    Map<String, String> expectedEnvironment = ImmutableMap.of("key", "value");
    Class<? extends BuildableManifestTemplate> expectedTargetFormat = OCIManifestTemplate.class;

    BuildConfiguration.Builder buildConfigurationBuilder =
        BuildConfiguration.builder(Mockito.mock(BuildLogger.class))
            .setBaseImage(
                ImageReference.of(
                    expectedBaseImageServerUrl, expectedBaseImageName, expectedBaseImageTag))
            .setTargetImage(
                ImageReference.of(
                    expectedTargetServerUrl, expectedTargetImageName, expectedTargetTag))
            .setCredentialHelperNames(expectedCredentialHelperNames)
            .setKnownRegistryCredentials(expectedKnownRegistryCredentials)
            .setEnableReproducibleBuilds(expectedEnableReproducibleBuilds)
            .setMainClass(expectedMainClass)
            .setJvmFlags(expectedJvmFlags)
            .setEnvironment(expectedEnvironment)
            .setTargetFormat(OCIManifestTemplate.class);
    BuildConfiguration buildConfiguration = buildConfigurationBuilder.build();

    Assert.assertEquals(expectedBaseImageServerUrl, buildConfiguration.getBaseImageRegistry());
    Assert.assertEquals(expectedBaseImageName, buildConfiguration.getBaseImageRepository());
    Assert.assertEquals(expectedBaseImageTag, buildConfiguration.getBaseImageTag());
    Assert.assertEquals(expectedTargetServerUrl, buildConfiguration.getTargetRegistry());
    Assert.assertEquals(expectedTargetImageName, buildConfiguration.getTargetRepository());
    Assert.assertEquals(expectedTargetTag, buildConfiguration.getTargetTag());
    Assert.assertEquals(
        expectedCredentialHelperNames, buildConfiguration.getCredentialHelperNames());
    Assert.assertEquals(
        expectedKnownRegistryCredentials, buildConfiguration.getKnownRegistryCredentials());
    Assert.assertEquals(
        expectedEnableReproducibleBuilds, buildConfiguration.getEnableReproducibleBuilds());
    Assert.assertEquals(expectedMainClass, buildConfiguration.getMainClass());
    Assert.assertEquals(expectedJvmFlags, buildConfiguration.getJvmFlags());
    Assert.assertEquals(expectedEnvironment, buildConfiguration.getEnvironment());
    Assert.assertEquals(expectedTargetFormat, buildConfiguration.getTargetFormat());
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
    String expectedMainClass = "mainclass";

    BuildConfiguration buildConfiguration =
        BuildConfiguration.builder(Mockito.mock(BuildLogger.class))
            .setBaseImage(
                ImageReference.of(
                    expectedBaseImageServerUrl, expectedBaseImageName, expectedBaseImageTag))
            .setTargetImage(
                ImageReference.of(
                    expectedTargetServerUrl, expectedTargetImageName, expectedTargetTag))
            .setMainClass(expectedMainClass)
            .build();

    Assert.assertEquals(Collections.emptyList(), buildConfiguration.getCredentialHelperNames());
    Assert.assertEquals(
        RegistryCredentials.none(), buildConfiguration.getKnownRegistryCredentials());
    Assert.assertTrue(buildConfiguration.getEnableReproducibleBuilds());
    Assert.assertEquals(Collections.emptyList(), buildConfiguration.getJvmFlags());
    Assert.assertEquals(Collections.emptyMap(), buildConfiguration.getEnvironment());
    Assert.assertEquals(V22ManifestTemplate.class, buildConfiguration.getTargetFormat());
  }

  @Test
  public void testBuilder_missingValues() {
    // Main class is missing
    try {
      BuildConfiguration.builder(Mockito.mock(BuildLogger.class))
          .setBaseImage(Mockito.mock(ImageReference.class))
          .setTargetImage(Mockito.mock(ImageReference.class))
          .build();
      Assert.fail("Build configuration should not be built with missing values");

    } catch (IllegalStateException ex) {
      Assert.assertEquals("main class is required but not set", ex.getMessage());
    }

    // Main class and target image are missing
    try {
      BuildConfiguration.builder(Mockito.mock(BuildLogger.class))
          .setBaseImage(Mockito.mock(ImageReference.class))
          .build();
      Assert.fail("Build configuration should not be built with missing values");

    } catch (IllegalStateException ex) {
      Assert.assertEquals(
          "target image is required but not set and main class is required but not set",
          ex.getMessage());
    }

    // All required fields missing
    try {
      BuildConfiguration.builder(Mockito.mock(BuildLogger.class)).build();
      Assert.fail("Build configuration should not be built with missing values");

    } catch (IllegalStateException ex) {
      Assert.assertEquals(
          "base image is required but not set, target image is required but not set, and main class is required but not set",
          ex.getMessage());
    }
  }
}
