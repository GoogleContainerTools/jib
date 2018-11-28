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

package com.google.cloud.tools.jib.api;

import com.google.cloud.tools.jib.configuration.BuildConfiguration;
import com.google.cloud.tools.jib.configuration.CacheDirectoryCreationException;
import com.google.cloud.tools.jib.configuration.ContainerConfiguration;
import com.google.cloud.tools.jib.configuration.LayerConfiguration;
import com.google.cloud.tools.jib.configuration.Port;
import com.google.cloud.tools.jib.configuration.credentials.Credential;
import com.google.cloud.tools.jib.configuration.credentials.CredentialRetriever;
import com.google.cloud.tools.jib.event.EventHandlers;
import com.google.cloud.tools.jib.event.JibEvent;
import com.google.cloud.tools.jib.filesystem.AbsoluteUnixPath;
import com.google.cloud.tools.jib.image.ImageFormat;
import com.google.cloud.tools.jib.image.ImageReference;
import com.google.cloud.tools.jib.image.InvalidImageReferenceException;
import com.google.cloud.tools.jib.registry.credentials.CredentialRetrievalException;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests for {@link JibContainerBuilder}. */
@RunWith(MockitoJUnitRunner.class)
public class JibContainerBuilderTest {

  @Spy private BuildConfiguration.Builder spyBuildConfigurationBuilder;
  @Mock private LayerConfiguration mockLayerConfiguration1;
  @Mock private LayerConfiguration mockLayerConfiguration2;
  @Mock private CredentialRetriever mockCredentialRetriever;
  @Mock private ExecutorService mockExecutorService;
  @Mock private Consumer<JibEvent> mockJibEventConsumer;
  @Mock private JibEvent mockJibEvent;

  @Test
  public void testToBuildConfiguration_containerConfigurationSet()
      throws InvalidImageReferenceException, CacheDirectoryCreationException, IOException {
    RegistryImage baseImage = RegistryImage.named("test-image");
    JibContainerBuilder jibContainerBuilder =
        new JibContainerBuilder(baseImage, spyBuildConfigurationBuilder)
            .setEntrypoint(Arrays.asList("entry", "point"))
            .setEnvironment(ImmutableMap.of("name", "value"))
            .setExposedPorts(ImmutableSet.of(Port.tcp(1234), Port.udp(5678)))
            .setLabels(ImmutableMap.of("key", "value"))
            .setProgramArguments(Arrays.asList("program", "arguments"))
            .setCreationTime(Instant.ofEpochMilli(1000))
            .setUser("user")
            .setWorkingDirectory(AbsoluteUnixPath.get("/working/directory"));

    BuildConfiguration buildConfiguration =
        jibContainerBuilder.toBuildConfiguration(Containerizer.to(baseImage));
    ContainerConfiguration containerConfiguration = buildConfiguration.getContainerConfiguration();
    Assert.assertEquals(Arrays.asList("entry", "point"), containerConfiguration.getEntrypoint());
    Assert.assertEquals(
        ImmutableMap.of("name", "value"), containerConfiguration.getEnvironmentMap());
    Assert.assertEquals(
        ImmutableSet.of(Port.tcp(1234), Port.udp(5678)), containerConfiguration.getExposedPorts());
    Assert.assertEquals(ImmutableMap.of("key", "value"), containerConfiguration.getLabels());
    Assert.assertEquals(
        Arrays.asList("program", "arguments"), containerConfiguration.getProgramArguments());
    Assert.assertEquals(Instant.ofEpochMilli(1000), containerConfiguration.getCreationTime());
    Assert.assertEquals("user", containerConfiguration.getUser());
    Assert.assertEquals(
        AbsoluteUnixPath.get("/working/directory"), containerConfiguration.getWorkingDirectory());
  }

  @Test
  public void testToBuildConfiguration_containerConfigurationAdd()
      throws InvalidImageReferenceException, CacheDirectoryCreationException, IOException {
    RegistryImage baseImage = RegistryImage.named("test-image");
    JibContainerBuilder jibContainerBuilder =
        new JibContainerBuilder(baseImage, spyBuildConfigurationBuilder)
            .setEntrypoint("entry", "point")
            .setEnvironment(ImmutableMap.of("name", "value"))
            .addEnvironmentVariable("environment", "variable")
            .setExposedPorts(Port.tcp(1234), Port.udp(5678))
            .addExposedPort(Port.tcp(1337))
            .setLabels(ImmutableMap.of("key", "value"))
            .addLabel("added", "label")
            .setProgramArguments("program", "arguments");

    BuildConfiguration buildConfiguration =
        jibContainerBuilder.toBuildConfiguration(Containerizer.to(baseImage));
    ContainerConfiguration containerConfiguration = buildConfiguration.getContainerConfiguration();
    Assert.assertEquals(Arrays.asList("entry", "point"), containerConfiguration.getEntrypoint());
    Assert.assertEquals(
        ImmutableMap.of("name", "value", "environment", "variable"),
        containerConfiguration.getEnvironmentMap());
    Assert.assertEquals(
        ImmutableSet.of(Port.tcp(1234), Port.udp(5678), Port.tcp(1337)),
        containerConfiguration.getExposedPorts());
    Assert.assertEquals(
        ImmutableMap.of("key", "value", "added", "label"), containerConfiguration.getLabels());
    Assert.assertEquals(
        Arrays.asList("program", "arguments"), containerConfiguration.getProgramArguments());
    Assert.assertEquals(Instant.EPOCH, containerConfiguration.getCreationTime());
  }

  @Test
  public void testToBuildConfiguration()
      throws InvalidImageReferenceException, CredentialRetrievalException, IOException,
          CacheDirectoryCreationException {
    RegistryImage baseImage =
        RegistryImage.named("base/image").addCredentialRetriever(mockCredentialRetriever);
    RegistryImage targetImage =
        RegistryImage.named(ImageReference.of("gcr.io", "my-project/my-app", null))
            .addCredential("username", "password");
    Containerizer containerizer =
        Containerizer.to(targetImage)
            .setBaseImageLayersCache(Paths.get("base/image/layers"))
            .setApplicationLayersCache(Paths.get("application/layers"))
            .setExecutorService(mockExecutorService)
            .setEventHandlers(new EventHandlers().add(mockJibEventConsumer));

    JibContainerBuilder jibContainerBuilder =
        new JibContainerBuilder(baseImage, spyBuildConfigurationBuilder)
            .setLayers(Arrays.asList(mockLayerConfiguration1, mockLayerConfiguration2));
    BuildConfiguration buildConfiguration = jibContainerBuilder.toBuildConfiguration(containerizer);

    Assert.assertEquals(
        spyBuildConfigurationBuilder.build().getContainerConfiguration(),
        buildConfiguration.getContainerConfiguration());

    Assert.assertEquals(
        baseImage.toImageConfiguration().getImage().toString(),
        buildConfiguration.getBaseImageConfiguration().getImage().toString());
    Assert.assertEquals(
        1, buildConfiguration.getBaseImageConfiguration().getCredentialRetrievers().size());
    Assert.assertSame(
        mockCredentialRetriever,
        buildConfiguration.getBaseImageConfiguration().getCredentialRetrievers().get(0));

    Assert.assertEquals(
        targetImage.toImageConfiguration().getImage().toString(),
        buildConfiguration.getTargetImageConfiguration().getImage().toString());
    Assert.assertEquals(
        1, buildConfiguration.getTargetImageConfiguration().getCredentialRetrievers().size());
    Assert.assertEquals(
        Credential.basic("username", "password"),
        buildConfiguration
            .getTargetImageConfiguration()
            .getCredentialRetrievers()
            .get(0)
            .retrieve()
            .orElseThrow(AssertionError::new));

    Assert.assertEquals(ImmutableSet.of("latest"), buildConfiguration.getAllTargetImageTags());

    Mockito.verify(spyBuildConfigurationBuilder)
        .setBaseImageLayersCacheDirectory(Paths.get("base/image/layers"));
    Mockito.verify(spyBuildConfigurationBuilder)
        .setApplicationLayersCacheDirectory(Paths.get("application/layers"));

    Assert.assertEquals(
        Arrays.asList(mockLayerConfiguration1, mockLayerConfiguration2),
        buildConfiguration.getLayerConfigurations());

    Assert.assertEquals(mockExecutorService, buildConfiguration.getExecutorService());

    buildConfiguration.getEventDispatcher().dispatch(mockJibEvent);
    Mockito.verify(mockJibEventConsumer).accept(mockJibEvent);

    Assert.assertEquals("jib-core", buildConfiguration.getToolName());

    Assert.assertSame(
        ImageFormat.Docker.getManifestTemplateClass(), buildConfiguration.getTargetFormat());

    Assert.assertEquals("jib-core", buildConfiguration.getToolName());

    // Changes jibContainerBuilder.
    buildConfiguration =
        jibContainerBuilder
            .setFormat(ImageFormat.OCI)
            .toBuildConfiguration(
                containerizer
                    .withAdditionalTag("tag1")
                    .withAdditionalTag("tag2")
                    .setToolName("toolName"));
    Assert.assertSame(
        ImageFormat.OCI.getManifestTemplateClass(), buildConfiguration.getTargetFormat());
    Assert.assertEquals(
        ImmutableSet.of("latest", "tag1", "tag2"), buildConfiguration.getAllTargetImageTags());
    Assert.assertEquals("toolName", buildConfiguration.getToolName());
  }
}
