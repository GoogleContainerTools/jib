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

import com.google.cloud.tools.jib.builder.steps.BuildResult;
import com.google.cloud.tools.jib.builder.steps.StepsRunner;
import com.google.cloud.tools.jib.configuration.BuildConfiguration;
import com.google.cloud.tools.jib.configuration.ContainerConfiguration;
import com.google.cloud.tools.jib.configuration.ImageConfiguration;
import com.google.cloud.tools.jib.event.EventHandlers;
import com.google.cloud.tools.jib.image.json.OCIManifestTemplate;
import com.google.cloud.tools.jib.image.json.V22ManifestTemplate;
import com.google.cloud.tools.jib.registry.credentials.CredentialRetrievalException;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.IOException;
import java.nio.file.Paths;
import java.security.DigestException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
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
    JibContainerBuilder jibContainerBuilder =
        new JibContainerBuilder(RegistryImage.named("base/image"), spyBuildConfigurationBuilder)
            .setEntrypoint(Arrays.asList("entry", "point"))
            .setEnvironment(ImmutableMap.of("name", "value"))
            .setExposedPorts(ImmutableSet.of(Port.tcp(1234), Port.udp(5678)))
            .setLabels(ImmutableMap.of("key", "value"))
            .setProgramArguments(Arrays.asList("program", "arguments"))
            .setCreationTime(Instant.ofEpochMilli(1000))
            .setUser("user")
            .setWorkingDirectory(AbsoluteUnixPath.get("/working/directory"));

    BuildConfiguration buildConfiguration =
        jibContainerBuilder.toBuildConfiguration(
            Containerizer.to(RegistryImage.named("target/image")),
            MoreExecutors.newDirectExecutorService());
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
    JibContainerBuilder jibContainerBuilder =
        new JibContainerBuilder(RegistryImage.named("base/image"), spyBuildConfigurationBuilder)
            .setEntrypoint("entry", "point")
            .setEnvironment(ImmutableMap.of("name", "value"))
            .addEnvironmentVariable("environment", "variable")
            .setExposedPorts(Port.tcp(1234), Port.udp(5678))
            .addExposedPort(Port.tcp(1337))
            .setLabels(ImmutableMap.of("key", "value"))
            .addLabel("added", "label")
            .setProgramArguments("program", "arguments");

    BuildConfiguration buildConfiguration =
        jibContainerBuilder.toBuildConfiguration(
            Containerizer.to(RegistryImage.named("target/image")),
            MoreExecutors.newDirectExecutorService());
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
    RegistryImage targetImage =
        RegistryImage.named(ImageReference.of("gcr.io", "my-project/my-app", null))
            .addCredential("username", "password");
    Containerizer containerizer =
        Containerizer.to(targetImage)
            .setBaseImageLayersCache(Paths.get("base/image/layers"))
            .setApplicationLayersCache(Paths.get("application/layers"))
            .setExecutorService(mockExecutorService)
            .addEventHandler(mockJibEventConsumer);

    RegistryImage baseImage =
        RegistryImage.named("base/image").addCredentialRetriever(mockCredentialRetriever);
    JibContainerBuilder jibContainerBuilder =
        new JibContainerBuilder(baseImage, spyBuildConfigurationBuilder)
            .setLayers(Arrays.asList(mockLayerConfiguration1, mockLayerConfiguration2));
    BuildConfiguration buildConfiguration =
        jibContainerBuilder.toBuildConfiguration(
            containerizer, containerizer.getExecutorService().get());

    Assert.assertEquals(
        spyBuildConfigurationBuilder.build().getContainerConfiguration(),
        buildConfiguration.getContainerConfiguration());

    Assert.assertEquals(
        "base/image", buildConfiguration.getBaseImageConfiguration().getImage().toString());
    Assert.assertEquals(
        Arrays.asList(mockCredentialRetriever),
        buildConfiguration.getBaseImageConfiguration().getCredentialRetrievers());

    Assert.assertEquals(
        "gcr.io/my-project/my-app",
        buildConfiguration.getTargetImageConfiguration().getImage().toString());
    Assert.assertEquals(
        1, buildConfiguration.getTargetImageConfiguration().getCredentialRetrievers().size());
    Assert.assertEquals(
        Credential.from("username", "password"),
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

    buildConfiguration.getEventHandlers().dispatch(mockJibEvent);
    Mockito.verify(mockJibEventConsumer).accept(mockJibEvent);

    Assert.assertEquals("jib-core", buildConfiguration.getToolName());

    Assert.assertSame(V22ManifestTemplate.class, buildConfiguration.getTargetFormat());

    Assert.assertEquals("jib-core", buildConfiguration.getToolName());

    // Changes jibContainerBuilder.
    buildConfiguration =
        jibContainerBuilder
            .setFormat(ImageFormat.OCI)
            .toBuildConfiguration(
                containerizer
                    .withAdditionalTag("tag1")
                    .withAdditionalTag("tag2")
                    .setToolName("toolName"),
                MoreExecutors.newDirectExecutorService());
    Assert.assertSame(OCIManifestTemplate.class, buildConfiguration.getTargetFormat());
    Assert.assertEquals(
        ImmutableSet.of("latest", "tag1", "tag2"), buildConfiguration.getAllTargetImageTags());
    Assert.assertEquals("toolName", buildConfiguration.getToolName());
  }

  /** Verify that an internally-created ExecutorService is shutdown. */
  @Test
  public void testContainerize_executorCreated() throws Exception {
    JibContainerBuilder jibContainerBuilder =
        new JibContainerBuilder(RegistryImage.named("base/image"), spyBuildConfigurationBuilder)
            .setEntrypoint(Arrays.asList("entry", "point"))
            .setEnvironment(ImmutableMap.of("name", "value"))
            .setExposedPorts(ImmutableSet.of(Port.tcp(1234), Port.udp(5678)))
            .setLabels(ImmutableMap.of("key", "value"))
            .setProgramArguments(Arrays.asList("program", "arguments"))
            .setCreationTime(Instant.ofEpochMilli(1000))
            .setUser("user")
            .setWorkingDirectory(AbsoluteUnixPath.get("/working/directory"));

    Containerizer mockContainerizer = createMockContainerizer();

    jibContainerBuilder.containerize(mockContainerizer, Suppliers.ofInstance(mockExecutorService));

    Mockito.verify(mockExecutorService).shutdown();
  }

  /** Verify that a provided ExecutorService is not shutdown. */
  @Test
  public void testContainerize_configuredExecutor() throws Exception {
    JibContainerBuilder jibContainerBuilder =
        new JibContainerBuilder(RegistryImage.named("base/image"), spyBuildConfigurationBuilder)
            .setEntrypoint(Arrays.asList("entry", "point"))
            .setEnvironment(ImmutableMap.of("name", "value"))
            .setExposedPorts(ImmutableSet.of(Port.tcp(1234), Port.udp(5678)))
            .setLabels(ImmutableMap.of("key", "value"))
            .setProgramArguments(Arrays.asList("program", "arguments"))
            .setCreationTime(Instant.ofEpochMilli(1000))
            .setUser("user")
            .setWorkingDirectory(AbsoluteUnixPath.get("/working/directory"));
    Containerizer mockContainerizer = createMockContainerizer();
    Mockito.when(mockContainerizer.getExecutorService())
        .thenReturn(Optional.of(mockExecutorService));

    jibContainerBuilder.containerize(
        mockContainerizer,
        () -> {
          throw new AssertionError();
        });

    Mockito.verify(mockExecutorService, Mockito.never()).shutdown();
  }

  private Containerizer createMockContainerizer()
      throws CacheDirectoryCreationException, InvalidImageReferenceException, InterruptedException,
          ExecutionException, DigestException {

    ImageReference targetImage = ImageReference.parse("target-image");
    Containerizer mockContainerizer = Mockito.mock(Containerizer.class);
    StepsRunner stepsRunner = Mockito.mock(StepsRunner.class);
    BuildResult mockBuildResult = Mockito.mock(BuildResult.class);

    Mockito.when(mockContainerizer.getImageConfiguration())
        .thenReturn(ImageConfiguration.builder(targetImage).build());
    Mockito.when(mockContainerizer.createStepsRunner(Mockito.any(BuildConfiguration.class)))
        .thenReturn(stepsRunner);
    Mockito.when(stepsRunner.run()).thenReturn(mockBuildResult);
    Mockito.when(mockBuildResult.getImageDigest())
        .thenReturn(
            DescriptorDigest.fromHash(
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"));
    Mockito.when(mockBuildResult.getImageId())
        .thenReturn(
            DescriptorDigest.fromHash(
                "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"));

    Mockito.when(mockContainerizer.getAdditionalTags()).thenReturn(Collections.emptySet());
    Mockito.when(mockContainerizer.getBaseImageLayersCacheDirectory()).thenReturn(Paths.get("/"));
    Mockito.when(mockContainerizer.getApplicationLayersCacheDirectory()).thenReturn(Paths.get("/"));
    Mockito.when(mockContainerizer.getAllowInsecureRegistries()).thenReturn(false);
    Mockito.when(mockContainerizer.getToolName()).thenReturn("mocktool");
    Mockito.when(mockContainerizer.getExecutorService()).thenReturn(Optional.empty());
    Mockito.when(mockContainerizer.buildEventHandlers()).thenReturn(EventHandlers.NONE);
    return mockContainerizer;
  }
}
