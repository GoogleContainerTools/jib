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

import com.google.cloud.tools.jib.api.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.Credential;
import com.google.cloud.tools.jib.api.CredentialRetriever;
import com.google.cloud.tools.jib.api.ImageFormat;
import com.google.cloud.tools.jib.api.ImageReference;
import com.google.cloud.tools.jib.api.LayerConfiguration;
import com.google.cloud.tools.jib.api.Port;
import com.google.cloud.tools.jib.image.json.BuildableManifestTemplate;
import com.google.cloud.tools.jib.image.json.OCIManifestTemplate;
import com.google.cloud.tools.jib.image.json.V22ManifestTemplate;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

/** Tests for {@link BuildConfiguration}. */
public class BuildConfigurationTest {

  @Test
  public void testBuilder() throws Exception {
    String expectedBaseImageServerUrl = "someserver";
    String expectedBaseImageName = "baseimage";
    String expectedBaseImageTag = "baseimagetag";
    String expectedTargetServerUrl = "someotherserver";
    String expectedTargetImageName = "targetimage";
    String expectedTargetTag = "targettag";
    Set<String> additionalTargetImageTags = ImmutableSet.of("tag1", "tag2", "tag3");
    Set<String> expectedTargetImageTags = ImmutableSet.of("targettag", "tag1", "tag2", "tag3");
    List<CredentialRetriever> credentialRetrievers =
        Collections.singletonList(() -> Optional.of(Credential.from("username", "password")));
    Instant expectedCreationTime = Instant.ofEpochSecond(10000);
    List<String> expectedEntrypoint = Arrays.asList("some", "entrypoint");
    List<String> expectedProgramArguments = Arrays.asList("arg1", "arg2");
    Map<String, String> expectedEnvironment = ImmutableMap.of("key", "value");
    ImmutableSet<Port> expectedExposedPorts = ImmutableSet.of(Port.tcp(1000), Port.tcp(2000));
    Map<String, String> expectedLabels = ImmutableMap.of("key1", "value1", "key2", "value2");
    Class<? extends BuildableManifestTemplate> expectedTargetFormat = OCIManifestTemplate.class;
    Path expectedApplicationLayersCacheDirectory = Paths.get("application/layers");
    Path expectedBaseImageLayersCacheDirectory = Paths.get("base/image/layers");
    List<LayerConfiguration> expectedLayerConfigurations =
        Collections.singletonList(
            LayerConfiguration.builder()
                .addEntry(Paths.get("sourceFile"), AbsoluteUnixPath.get("/path/in/container"))
                .build());
    String expectedCreatedBy = "createdBy";

    ImageConfiguration baseImageConfiguration =
        ImageConfiguration.builder(
                ImageReference.of(
                    expectedBaseImageServerUrl, expectedBaseImageName, expectedBaseImageTag))
            .build();
    ImageConfiguration targetImageConfiguration =
        ImageConfiguration.builder(
                ImageReference.of(
                    expectedTargetServerUrl, expectedTargetImageName, expectedTargetTag))
            .setCredentialRetrievers(credentialRetrievers)
            .build();
    ContainerConfiguration containerConfiguration =
        ContainerConfiguration.builder()
            .setCreationTime(expectedCreationTime)
            .setEntrypoint(expectedEntrypoint)
            .setProgramArguments(expectedProgramArguments)
            .setEnvironment(expectedEnvironment)
            .setExposedPorts(expectedExposedPorts)
            .setLabels(expectedLabels)
            .build();
    BuildConfiguration.Builder buildConfigurationBuilder =
        BuildConfiguration.builder()
            .setBaseImageConfiguration(baseImageConfiguration)
            .setTargetImageConfiguration(targetImageConfiguration)
            .setAdditionalTargetImageTags(additionalTargetImageTags)
            .setContainerConfiguration(containerConfiguration)
            .setApplicationLayersCacheDirectory(expectedApplicationLayersCacheDirectory)
            .setBaseImageLayersCacheDirectory(expectedBaseImageLayersCacheDirectory)
            .setTargetFormat(ImageFormat.OCI)
            .setAllowInsecureRegistries(true)
            .setLayerConfigurations(expectedLayerConfigurations)
            .setToolName(expectedCreatedBy)
            .setExecutorService(MoreExecutors.newDirectExecutorService());
    BuildConfiguration buildConfiguration = buildConfigurationBuilder.build();

    Assert.assertNotNull(buildConfiguration.getContainerConfiguration());
    Assert.assertEquals(
        expectedCreationTime, buildConfiguration.getContainerConfiguration().getCreationTime());
    Assert.assertEquals(
        expectedBaseImageServerUrl,
        buildConfiguration.getBaseImageConfiguration().getImageRegistry());
    Assert.assertEquals(
        expectedBaseImageName, buildConfiguration.getBaseImageConfiguration().getImageRepository());
    Assert.assertEquals(
        expectedBaseImageTag, buildConfiguration.getBaseImageConfiguration().getImageTag());
    Assert.assertEquals(
        expectedTargetServerUrl,
        buildConfiguration.getTargetImageConfiguration().getImageRegistry());
    Assert.assertEquals(
        expectedTargetImageName,
        buildConfiguration.getTargetImageConfiguration().getImageRepository());
    Assert.assertEquals(
        expectedTargetTag, buildConfiguration.getTargetImageConfiguration().getImageTag());
    Assert.assertEquals(expectedTargetImageTags, buildConfiguration.getAllTargetImageTags());
    Assert.assertEquals(
        Credential.from("username", "password"),
        buildConfiguration
            .getTargetImageConfiguration()
            .getCredentialRetrievers()
            .get(0)
            .retrieve()
            .orElseThrow(AssertionError::new));
    Assert.assertEquals(
        expectedProgramArguments,
        buildConfiguration.getContainerConfiguration().getProgramArguments());
    Assert.assertEquals(
        expectedEnvironment, buildConfiguration.getContainerConfiguration().getEnvironmentMap());
    Assert.assertEquals(
        expectedExposedPorts, buildConfiguration.getContainerConfiguration().getExposedPorts());
    Assert.assertEquals(expectedLabels, buildConfiguration.getContainerConfiguration().getLabels());
    Assert.assertEquals(expectedTargetFormat, buildConfiguration.getTargetFormat());
    Assert.assertEquals(
        expectedApplicationLayersCacheDirectory,
        buildConfigurationBuilder.getApplicationLayersCacheDirectory());
    Assert.assertEquals(
        expectedBaseImageLayersCacheDirectory,
        buildConfigurationBuilder.getBaseImageLayersCacheDirectory());
    Assert.assertTrue(buildConfiguration.getAllowInsecureRegistries());
    Assert.assertEquals(expectedLayerConfigurations, buildConfiguration.getLayerConfigurations());
    Assert.assertEquals(
        expectedEntrypoint, buildConfiguration.getContainerConfiguration().getEntrypoint());
    Assert.assertEquals(expectedCreatedBy, buildConfiguration.getToolName());
    Assert.assertNotNull(buildConfiguration.getExecutorService());
  }

  @Test
  public void testBuilder_default() throws IOException {
    // These are required and don't have defaults.
    String expectedBaseImageServerUrl = "someserver";
    String expectedBaseImageName = "baseimage";
    String expectedBaseImageTag = "baseimagetag";
    String expectedTargetServerUrl = "someotherserver";
    String expectedTargetImageName = "targetimage";
    String expectedTargetTag = "targettag";

    ImageConfiguration baseImageConfiguration =
        ImageConfiguration.builder(
                ImageReference.of(
                    expectedBaseImageServerUrl, expectedBaseImageName, expectedBaseImageTag))
            .build();
    ImageConfiguration targetImageConfiguration =
        ImageConfiguration.builder(
                ImageReference.of(
                    expectedTargetServerUrl, expectedTargetImageName, expectedTargetTag))
            .build();
    BuildConfiguration.Builder buildConfigurationBuilder =
        BuildConfiguration.builder()
            .setBaseImageConfiguration(baseImageConfiguration)
            .setTargetImageConfiguration(targetImageConfiguration)
            .setBaseImageLayersCacheDirectory(Paths.get("ignored"))
            .setApplicationLayersCacheDirectory(Paths.get("ignored"))
            .setExecutorService(MoreExecutors.newDirectExecutorService());
    BuildConfiguration buildConfiguration = buildConfigurationBuilder.build();

    Assert.assertEquals(ImmutableSet.of("targettag"), buildConfiguration.getAllTargetImageTags());
    Assert.assertEquals(V22ManifestTemplate.class, buildConfiguration.getTargetFormat());
    Assert.assertNotNull(buildConfigurationBuilder.getApplicationLayersCacheDirectory());
    Assert.assertEquals(
        Paths.get("ignored"), buildConfigurationBuilder.getApplicationLayersCacheDirectory());
    Assert.assertNotNull(buildConfigurationBuilder.getBaseImageLayersCacheDirectory());
    Assert.assertEquals(
        Paths.get("ignored"), buildConfigurationBuilder.getBaseImageLayersCacheDirectory());
    Assert.assertNull(buildConfiguration.getContainerConfiguration());
    Assert.assertFalse(buildConfiguration.getAllowInsecureRegistries());
    Assert.assertEquals(Collections.emptyList(), buildConfiguration.getLayerConfigurations());
    Assert.assertEquals("jib", buildConfiguration.getToolName());
  }

  @Test
  public void testBuilder_missingValues() throws IOException {
    // Target image is missing
    try {
      BuildConfiguration.builder()
          .setBaseImageConfiguration(
              ImageConfiguration.builder(Mockito.mock(ImageReference.class)).build())
          .setBaseImageLayersCacheDirectory(Paths.get("ignored"))
          .setApplicationLayersCacheDirectory(Paths.get("ignored"))
          .setExecutorService(MoreExecutors.newDirectExecutorService())
          .build();
      Assert.fail("Build configuration should not be built with missing values");

    } catch (IllegalStateException ex) {
      Assert.assertEquals("target image configuration is required but not set", ex.getMessage());
    }

    // Two required fields missing
    try {
      BuildConfiguration.builder()
          .setBaseImageLayersCacheDirectory(Paths.get("ignored"))
          .setApplicationLayersCacheDirectory(Paths.get("ignored"))
          .setExecutorService(MoreExecutors.newDirectExecutorService())
          .build();
      Assert.fail("Build configuration should not be built with missing values");

    } catch (IllegalStateException ex) {
      Assert.assertEquals(
          "base image configuration and target image configuration are required but not set",
          ex.getMessage());
    }

    // All required fields missing
    try {
      BuildConfiguration.builder().build();
      Assert.fail("Build configuration should not be built with missing values");

    } catch (IllegalStateException ex) {
      Assert.assertEquals(
          "base image configuration, target image configuration, base image layers cache directory, "
              + "application layers cache directory, and executor service are required but not set",
          ex.getMessage());
    }
  }
}
