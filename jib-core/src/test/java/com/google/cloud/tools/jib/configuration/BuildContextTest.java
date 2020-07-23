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

import com.google.cloud.tools.jib.api.CacheDirectoryCreationException;
import com.google.cloud.tools.jib.api.Credential;
import com.google.cloud.tools.jib.api.CredentialRetriever;
import com.google.cloud.tools.jib.api.ImageReference;
import com.google.cloud.tools.jib.api.InvalidImageReferenceException;
import com.google.cloud.tools.jib.api.LogEvent;
import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.buildplan.FileEntriesLayer;
import com.google.cloud.tools.jib.api.buildplan.ImageFormat;
import com.google.cloud.tools.jib.api.buildplan.Port;
import com.google.cloud.tools.jib.event.EventHandlers;
import com.google.cloud.tools.jib.global.JibSystemProperties;
import com.google.cloud.tools.jib.image.json.BuildableManifestTemplate;
import com.google.cloud.tools.jib.image.json.OciManifestTemplate;
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
import java.util.concurrent.ExecutorService;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.RestoreSystemProperties;
import org.mockito.Mockito;

/** Tests for {@link BuildContext}. */
public class BuildContextTest {

  @Rule public final RestoreSystemProperties systemPropertyRestorer = new RestoreSystemProperties();

  private static BuildContext.Builder createBasicTestBuilder() {
    return BuildContext.builder()
        .setBaseImageConfiguration(
            ImageConfiguration.builder(Mockito.mock(ImageReference.class)).build())
        .setTargetImageConfiguration(
            ImageConfiguration.builder(Mockito.mock(ImageReference.class)).build())
        .setContainerConfiguration(ContainerConfiguration.builder().build())
        .setBaseImageLayersCacheDirectory(Paths.get("ignored"))
        .setApplicationLayersCacheDirectory(Paths.get("ignored"));
  }

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
    Class<? extends BuildableManifestTemplate> expectedTargetFormat = OciManifestTemplate.class;
    Path expectedApplicationLayersCacheDirectory = Paths.get("application/layers");
    Path expectedBaseImageLayersCacheDirectory = Paths.get("base/image/layers");
    List<FileEntriesLayer> expectedLayerConfigurations =
        Collections.singletonList(
            FileEntriesLayer.builder()
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
    BuildContext.Builder buildContextBuilder =
        BuildContext.builder()
            .setBaseImageConfiguration(baseImageConfiguration)
            .setTargetImageConfiguration(targetImageConfiguration)
            .setAdditionalTargetImageTags(additionalTargetImageTags)
            .setContainerConfiguration(containerConfiguration)
            .setApplicationLayersCacheDirectory(expectedApplicationLayersCacheDirectory)
            .setBaseImageLayersCacheDirectory(expectedBaseImageLayersCacheDirectory)
            .setTargetFormat(ImageFormat.OCI)
            .setAllowInsecureRegistries(true)
            .setLayerConfigurations(expectedLayerConfigurations)
            .setToolName(expectedCreatedBy);
    BuildContext buildContext = buildContextBuilder.build();

    Assert.assertEquals(
        expectedCreationTime, buildContext.getContainerConfiguration().getCreationTime());
    Assert.assertEquals(
        expectedBaseImageServerUrl, buildContext.getBaseImageConfiguration().getImageRegistry());
    Assert.assertEquals(
        expectedBaseImageName, buildContext.getBaseImageConfiguration().getImageRepository());
    Assert.assertEquals(
        expectedBaseImageTag, buildContext.getBaseImageConfiguration().getImageQualifier());
    Assert.assertEquals(
        expectedTargetServerUrl, buildContext.getTargetImageConfiguration().getImageRegistry());
    Assert.assertEquals(
        expectedTargetImageName, buildContext.getTargetImageConfiguration().getImageRepository());
    Assert.assertEquals(
        expectedTargetTag, buildContext.getTargetImageConfiguration().getImageQualifier());
    Assert.assertEquals(expectedTargetImageTags, buildContext.getAllTargetImageTags());
    Assert.assertEquals(
        Credential.from("username", "password"),
        buildContext
            .getTargetImageConfiguration()
            .getCredentialRetrievers()
            .get(0)
            .retrieve()
            .orElseThrow(AssertionError::new));
    Assert.assertEquals(
        expectedProgramArguments, buildContext.getContainerConfiguration().getProgramArguments());
    Assert.assertEquals(
        expectedEnvironment, buildContext.getContainerConfiguration().getEnvironmentMap());
    Assert.assertEquals(
        expectedExposedPorts, buildContext.getContainerConfiguration().getExposedPorts());
    Assert.assertEquals(expectedLabels, buildContext.getContainerConfiguration().getLabels());
    Assert.assertEquals(expectedTargetFormat, buildContext.getTargetFormat());
    Assert.assertEquals(
        expectedApplicationLayersCacheDirectory,
        buildContextBuilder.getApplicationLayersCacheDirectory());
    Assert.assertEquals(
        expectedBaseImageLayersCacheDirectory,
        buildContextBuilder.getBaseImageLayersCacheDirectory());
    Assert.assertEquals(expectedLayerConfigurations, buildContext.getLayerConfigurations());
    Assert.assertEquals(
        expectedEntrypoint, buildContext.getContainerConfiguration().getEntrypoint());
    Assert.assertEquals(expectedCreatedBy, buildContext.getToolName());
    Assert.assertNotNull(buildContext.getExecutorService());
  }

  @Test
  public void testBuilder_default() throws CacheDirectoryCreationException {
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
    BuildContext.Builder buildContextBuilder =
        BuildContext.builder()
            .setBaseImageConfiguration(baseImageConfiguration)
            .setTargetImageConfiguration(targetImageConfiguration)
            .setContainerConfiguration(ContainerConfiguration.builder().setUser("12345").build())
            .setBaseImageLayersCacheDirectory(Paths.get("ignored"))
            .setApplicationLayersCacheDirectory(Paths.get("ignored"));
    BuildContext buildContext = buildContextBuilder.build();

    Assert.assertEquals(ImmutableSet.of("targettag"), buildContext.getAllTargetImageTags());
    Assert.assertEquals(V22ManifestTemplate.class, buildContext.getTargetFormat());
    Assert.assertNotNull(buildContextBuilder.getApplicationLayersCacheDirectory());
    Assert.assertEquals(
        Paths.get("ignored"), buildContextBuilder.getApplicationLayersCacheDirectory());
    Assert.assertNotNull(buildContextBuilder.getBaseImageLayersCacheDirectory());
    Assert.assertEquals(
        Paths.get("ignored"), buildContextBuilder.getBaseImageLayersCacheDirectory());
    Assert.assertEquals("12345", buildContext.getContainerConfiguration().getUser());
    Assert.assertEquals(Collections.emptyList(), buildContext.getLayerConfigurations());
    Assert.assertEquals("jib", buildContext.getToolName());
  }

  @Test
  public void testBuilder_missingValues() throws CacheDirectoryCreationException {
    // Target image is missing
    try {
      BuildContext.builder()
          .setBaseImageConfiguration(
              ImageConfiguration.builder(Mockito.mock(ImageReference.class)).build())
          .setBaseImageLayersCacheDirectory(Paths.get("ignored"))
          .setContainerConfiguration(ContainerConfiguration.builder().build())
          .setApplicationLayersCacheDirectory(Paths.get("ignored"))
          .build();
      Assert.fail("BuildContext should not be built with missing values");

    } catch (IllegalStateException ex) {
      Assert.assertEquals("target image configuration is required but not set", ex.getMessage());
    }

    // Two required fields missing
    try {
      BuildContext.builder()
          .setBaseImageLayersCacheDirectory(Paths.get("ignored"))
          .setApplicationLayersCacheDirectory(Paths.get("ignored"))
          .setContainerConfiguration(ContainerConfiguration.builder().build())
          .build();
      Assert.fail("BuildContext should not be built with missing values");

    } catch (IllegalStateException ex) {
      Assert.assertEquals(
          "base image configuration and target image configuration are required but not set",
          ex.getMessage());
    }

    // All required fields missing
    try {
      BuildContext.builder().build();
      Assert.fail("BuildContext should not be built with missing values");

    } catch (IllegalStateException ex) {
      Assert.assertEquals(
          "base image configuration, target image configuration, container configuration, base "
              + "image layers cache directory, and application layers cache directory are required "
              + "but not set",
          ex.getMessage());
    }
  }

  @Test
  public void testBuilder_digestWarning()
      throws CacheDirectoryCreationException, InvalidImageReferenceException {
    EventHandlers mockEventHandlers = Mockito.mock(EventHandlers.class);
    BuildContext.Builder builder = createBasicTestBuilder().setEventHandlers(mockEventHandlers);

    builder
        .setBaseImageConfiguration(
            ImageConfiguration.builder(
                    ImageReference.parse(
                        "image@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"))
                .build())
        .build();
    Mockito.verify(mockEventHandlers, Mockito.never()).dispatch(LogEvent.warn(Mockito.anyString()));

    builder
        .setBaseImageConfiguration(
            ImageConfiguration.builder(ImageReference.parse("image:tag")).build())
        .build();
    Mockito.verify(mockEventHandlers)
        .dispatch(
            LogEvent.warn(
                "Base image 'image:tag' does not use a specific image digest - build may not be reproducible"));
  }

  @Test
  public void testClose_shutDownInternalExecutorService()
      throws IOException, CacheDirectoryCreationException {

    BuildContext buildContext = createBasicTestBuilder().build();
    buildContext.close();

    Assert.assertTrue(buildContext.getExecutorService().isShutdown());
  }

  @Test
  public void testClose_doNotShutDownProvidedExecutorService()
      throws IOException, CacheDirectoryCreationException {
    ExecutorService executorService = MoreExecutors.newDirectExecutorService();
    BuildContext buildContext =
        createBasicTestBuilder().setExecutorService(executorService).build();
    buildContext.close();

    Assert.assertSame(executorService, buildContext.getExecutorService());
    Assert.assertFalse(buildContext.getExecutorService().isShutdown());
  }

  @Test
  public void testGetUserAgent_unset() throws CacheDirectoryCreationException {
    BuildContext buildContext = createBasicTestBuilder().build();

    String generatedUserAgent = buildContext.makeUserAgent();

    Assert.assertEquals("jib null jib", generatedUserAgent);
  }

  @Test
  public void testGetUserAgent_withValues() throws CacheDirectoryCreationException {
    BuildContext buildContext =
        createBasicTestBuilder().setToolName("test-name").setToolVersion("test-version").build();

    String generatedUserAgent = buildContext.makeUserAgent();

    Assert.assertEquals("jib test-version test-name", generatedUserAgent);
  }

  @Test
  public void testGetUserAgentWithUpstreamClient() throws CacheDirectoryCreationException {
    System.setProperty(JibSystemProperties.UPSTREAM_CLIENT, "skaffold/0.34.0");
    BuildContext buildContext =
        createBasicTestBuilder().setToolName("test-name").setToolVersion("test-version").build();

    String generatedUserAgent = buildContext.makeUserAgent();

    Assert.assertEquals("jib test-version test-name skaffold/0.34.0", generatedUserAgent);
  }
}
