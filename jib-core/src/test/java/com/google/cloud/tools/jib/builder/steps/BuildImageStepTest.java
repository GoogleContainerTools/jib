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

package com.google.cloud.tools.jib.builder.steps;

import com.google.cloud.tools.jib.api.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.DescriptorDigest;
import com.google.cloud.tools.jib.api.Port;
import com.google.cloud.tools.jib.blob.BlobDescriptor;
import com.google.cloud.tools.jib.builder.ProgressEventDispatcher;
import com.google.cloud.tools.jib.cache.CachedLayer;
import com.google.cloud.tools.jib.configuration.BuildConfiguration;
import com.google.cloud.tools.jib.configuration.ContainerConfiguration;
import com.google.cloud.tools.jib.configuration.DockerHealthCheck;
import com.google.cloud.tools.jib.event.EventHandlers;
import com.google.cloud.tools.jib.image.Image;
import com.google.cloud.tools.jib.image.json.HistoryEntry;
import com.google.cloud.tools.jib.image.json.V22ManifestTemplate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import java.security.DigestException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.stream.Stream;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests for {@link BuildImageStep}. */
@RunWith(MockitoJUnitRunner.class)
public class BuildImageStepTest {

  @Mock private EventHandlers mockEventHandlers;
  @Mock private BuildConfiguration mockBuildConfiguration;
  @Mock private ContainerConfiguration mockContainerConfiguration;
  @Mock private PullBaseImageStep mockPullBaseImageStep;
  @Mock private PullAndCacheBaseImageLayersStep mockPullAndCacheBaseImageLayersStep;
  @Mock private PullAndCacheBaseImageLayerStep mockPullAndCacheBaseImageLayerStep;
  @Mock private BuildAndCacheApplicationLayerStep mockBuildAndCacheApplicationLayerStepDependencies;
  @Mock private BuildAndCacheApplicationLayerStep mockBuildAndCacheApplicationLayerStepResources;
  @Mock private BuildAndCacheApplicationLayerStep mockBuildAndCacheApplicationLayerStepClasses;
  @Mock private BuildAndCacheApplicationLayerStep mockBuildAndCacheApplicationLayerStepExtraFiles;
  @Mock private CachedLayer mockCachedLayer;
  private DescriptorDigest testDescriptorDigest;
  private HistoryEntry nonEmptyLayerHistory;
  private HistoryEntry emptyLayerHistory;

  @Before
  public void setUp() throws DigestException {
    testDescriptorDigest =
        DescriptorDigest.fromHash(
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");

    Mockito.when(mockBuildConfiguration.getEventHandlers()).thenReturn(mockEventHandlers);
    Mockito.when(mockBuildConfiguration.getContainerConfiguration())
        .thenReturn(mockContainerConfiguration);
    Mockito.when(mockBuildConfiguration.getToolName()).thenReturn("jib");
    Mockito.when(mockContainerConfiguration.getCreationTime()).thenReturn(Instant.EPOCH);
    Mockito.when(mockContainerConfiguration.getEnvironmentMap()).thenReturn(ImmutableMap.of());
    Mockito.when(mockContainerConfiguration.getProgramArguments()).thenReturn(ImmutableList.of());
    Mockito.when(mockContainerConfiguration.getExposedPorts()).thenReturn(ImmutableSet.of());
    Mockito.when(mockContainerConfiguration.getEntrypoint()).thenReturn(ImmutableList.of());
    Mockito.when(mockContainerConfiguration.getUser()).thenReturn("root");
    Mockito.when(mockCachedLayer.getBlobDescriptor())
        .thenReturn(new BlobDescriptor(0, testDescriptorDigest));

    nonEmptyLayerHistory =
        HistoryEntry.builder()
            .setCreationTimestamp(Instant.EPOCH)
            .setAuthor("JibBase")
            .setCreatedBy("jib-test")
            .build();
    emptyLayerHistory =
        HistoryEntry.builder()
            .setCreationTimestamp(Instant.EPOCH)
            .setAuthor("JibBase")
            .setCreatedBy("jib-test")
            .setEmptyLayer(true)
            .build();

    Image baseImage =
        Image.builder(V22ManifestTemplate.class)
            .setArchitecture("wasm")
            .setOs("js")
            .addEnvironment(ImmutableMap.of("BASE_ENV", "BASE_ENV_VALUE", "BASE_ENV_2", "DEFAULT"))
            .addLabel("base.label", "base.label.value")
            .addLabel("base.label.2", "default")
            .setWorkingDirectory("/base/working/directory")
            .setEntrypoint(ImmutableList.of("baseImageEntrypoint"))
            .setProgramArguments(ImmutableList.of("catalina.sh", "run"))
            .setHealthCheck(
                DockerHealthCheck.fromCommand(ImmutableList.of("CMD-SHELL", "echo hi"))
                    .setInterval(Duration.ofSeconds(3))
                    .setTimeout(Duration.ofSeconds(2))
                    .setStartPeriod(Duration.ofSeconds(1))
                    .setRetries(20)
                    .build())
            .addExposedPorts(ImmutableSet.of(Port.tcp(1000), Port.udp(2000)))
            .addVolumes(
                ImmutableSet.of(
                    AbsoluteUnixPath.get("/base/path1"), AbsoluteUnixPath.get("/base/path2")))
            .addHistory(nonEmptyLayerHistory)
            .addHistory(emptyLayerHistory)
            .addHistory(emptyLayerHistory)
            .build();
    Mockito.when(mockPullAndCacheBaseImageLayerStep.getFuture())
        .thenReturn(Futures.immediateFuture(mockCachedLayer));
    Mockito.when(mockPullAndCacheBaseImageLayersStep.getFuture())
        .thenReturn(
            Futures.immediateFuture(
                ImmutableList.of(
                    mockPullAndCacheBaseImageLayerStep,
                    mockPullAndCacheBaseImageLayerStep,
                    mockPullAndCacheBaseImageLayerStep)));
    Mockito.when(mockPullBaseImageStep.getFuture())
        .thenReturn(
            Futures.immediateFuture(
                new PullBaseImageStep.BaseImageWithAuthorization(baseImage, null)));

    Stream.of(
            mockBuildAndCacheApplicationLayerStepClasses,
            mockBuildAndCacheApplicationLayerStepDependencies,
            mockBuildAndCacheApplicationLayerStepExtraFiles,
            mockBuildAndCacheApplicationLayerStepResources)
        .forEach(
            layerStep ->
                Mockito.when(layerStep.getFuture())
                    .thenReturn(Futures.immediateFuture(mockCachedLayer)));

    Mockito.when(mockBuildAndCacheApplicationLayerStepClasses.getLayerType()).thenReturn("classes");
    Mockito.when(mockBuildAndCacheApplicationLayerStepDependencies.getLayerType())
        .thenReturn("dependencies");
    Mockito.when(mockBuildAndCacheApplicationLayerStepExtraFiles.getLayerType())
        .thenReturn("extra files");
    Mockito.when(mockBuildAndCacheApplicationLayerStepResources.getLayerType())
        .thenReturn("resources");
  }

  @Test
  public void test_validateAsyncDependencies() throws ExecutionException, InterruptedException {
    BuildImageStep buildImageStep =
        new BuildImageStep(
            MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor()),
            mockBuildConfiguration,
            ProgressEventDispatcher.newRoot(mockEventHandlers, "ignored", 1).newChildProducer(),
            mockPullBaseImageStep,
            mockPullAndCacheBaseImageLayersStep,
            ImmutableList.of(
                mockBuildAndCacheApplicationLayerStepDependencies,
                mockBuildAndCacheApplicationLayerStepResources,
                mockBuildAndCacheApplicationLayerStepClasses));
    Image image = buildImageStep.getFuture().get().getFuture().get();
    Assert.assertEquals(
        testDescriptorDigest, image.getLayers().asList().get(0).getBlobDescriptor().getDigest());
  }

  @Test
  public void test_propagateBaseImageConfiguration()
      throws ExecutionException, InterruptedException {
    Mockito.when(mockContainerConfiguration.getEnvironmentMap())
        .thenReturn(ImmutableMap.of("MY_ENV", "MY_ENV_VALUE", "BASE_ENV_2", "NEW_VALUE"));
    Mockito.when(mockContainerConfiguration.getLabels())
        .thenReturn(ImmutableMap.of("my.label", "my.label.value", "base.label.2", "new.value"));
    Mockito.when(mockContainerConfiguration.getExposedPorts())
        .thenReturn(ImmutableSet.of(Port.tcp(3000), Port.udp(4000)));
    Mockito.when(mockContainerConfiguration.getVolumes())
        .thenReturn(
            ImmutableSet.of(
                AbsoluteUnixPath.get("/new/path1"), AbsoluteUnixPath.get("/new/path2")));
    BuildImageStep buildImageStep =
        new BuildImageStep(
            MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor()),
            mockBuildConfiguration,
            ProgressEventDispatcher.newRoot(mockEventHandlers, "ignored", 1).newChildProducer(),
            mockPullBaseImageStep,
            mockPullAndCacheBaseImageLayersStep,
            ImmutableList.of(
                mockBuildAndCacheApplicationLayerStepDependencies,
                mockBuildAndCacheApplicationLayerStepResources,
                mockBuildAndCacheApplicationLayerStepClasses));
    Image image = buildImageStep.getFuture().get().getFuture().get();
    Assert.assertEquals("wasm", image.getArchitecture());
    Assert.assertEquals("js", image.getOs());
    Assert.assertEquals(
        ImmutableMap.of(
            "BASE_ENV", "BASE_ENV_VALUE", "MY_ENV", "MY_ENV_VALUE", "BASE_ENV_2", "NEW_VALUE"),
        image.getEnvironment());
    Assert.assertEquals(
        ImmutableMap.of(
            "base.label",
            "base.label.value",
            "my.label",
            "my.label.value",
            "base.label.2",
            "new.value"),
        image.getLabels());
    Assert.assertNotNull(image.getHealthCheck());
    Assert.assertEquals(
        ImmutableList.of("CMD-SHELL", "echo hi"), image.getHealthCheck().getCommand());
    Assert.assertTrue(image.getHealthCheck().getInterval().isPresent());
    Assert.assertEquals(Duration.ofSeconds(3), image.getHealthCheck().getInterval().get());
    Assert.assertTrue(image.getHealthCheck().getTimeout().isPresent());
    Assert.assertEquals(Duration.ofSeconds(2), image.getHealthCheck().getTimeout().get());
    Assert.assertTrue(image.getHealthCheck().getStartPeriod().isPresent());
    Assert.assertEquals(Duration.ofSeconds(1), image.getHealthCheck().getStartPeriod().get());
    Assert.assertTrue(image.getHealthCheck().getRetries().isPresent());
    Assert.assertEquals(20, (int) image.getHealthCheck().getRetries().get());
    Assert.assertEquals(
        ImmutableSet.of(Port.tcp(1000), Port.udp(2000), Port.tcp(3000), Port.udp(4000)),
        image.getExposedPorts());
    Assert.assertEquals(
        ImmutableSet.of(
            AbsoluteUnixPath.get("/base/path1"),
            AbsoluteUnixPath.get("/base/path2"),
            AbsoluteUnixPath.get("/new/path1"),
            AbsoluteUnixPath.get("/new/path2")),
        image.getVolumes());
    Assert.assertEquals("/base/working/directory", image.getWorkingDirectory());
    Assert.assertEquals("root", image.getUser());

    Assert.assertEquals(image.getHistory().get(0), nonEmptyLayerHistory);
    Assert.assertEquals(image.getHistory().get(1), emptyLayerHistory);
    Assert.assertEquals(image.getHistory().get(2), emptyLayerHistory);
    Assert.assertEquals(ImmutableList.of(), image.getEntrypoint());
    Assert.assertEquals(ImmutableList.of(), image.getProgramArguments());
  }

  @Test
  public void testOverrideWorkingDirectory() throws InterruptedException, ExecutionException {
    Mockito.when(mockContainerConfiguration.getWorkingDirectory())
        .thenReturn(AbsoluteUnixPath.get("/my/directory"));

    BuildImageStep buildImageStep =
        new BuildImageStep(
            MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor()),
            mockBuildConfiguration,
            ProgressEventDispatcher.newRoot(mockEventHandlers, "ignored", 1).newChildProducer(),
            mockPullBaseImageStep,
            mockPullAndCacheBaseImageLayersStep,
            ImmutableList.of(
                mockBuildAndCacheApplicationLayerStepDependencies,
                mockBuildAndCacheApplicationLayerStepResources,
                mockBuildAndCacheApplicationLayerStepClasses));
    Image image = buildImageStep.getFuture().get().getFuture().get();

    Assert.assertEquals("/my/directory", image.getWorkingDirectory());
  }

  @Test
  public void test_inheritedEntrypoint() throws ExecutionException, InterruptedException {
    Mockito.when(mockContainerConfiguration.getEntrypoint()).thenReturn(null);
    Mockito.when(mockContainerConfiguration.getProgramArguments())
        .thenReturn(ImmutableList.of("test"));

    BuildImageStep buildImageStep =
        new BuildImageStep(
            MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor()),
            mockBuildConfiguration,
            ProgressEventDispatcher.newRoot(mockEventHandlers, "ignored", 1).newChildProducer(),
            mockPullBaseImageStep,
            mockPullAndCacheBaseImageLayersStep,
            ImmutableList.of(
                mockBuildAndCacheApplicationLayerStepDependencies,
                mockBuildAndCacheApplicationLayerStepResources,
                mockBuildAndCacheApplicationLayerStepClasses));
    Image image = buildImageStep.getFuture().get().getFuture().get();

    Assert.assertEquals(ImmutableList.of("baseImageEntrypoint"), image.getEntrypoint());
    Assert.assertEquals(ImmutableList.of("test"), image.getProgramArguments());
  }

  @Test
  public void test_inheritedEntrypointAndProgramArguments()
      throws ExecutionException, InterruptedException {
    Mockito.when(mockContainerConfiguration.getEntrypoint()).thenReturn(null);
    Mockito.when(mockContainerConfiguration.getProgramArguments()).thenReturn(null);

    BuildImageStep buildImageStep =
        new BuildImageStep(
            MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor()),
            mockBuildConfiguration,
            ProgressEventDispatcher.newRoot(mockEventHandlers, "ignored", 1).newChildProducer(),
            mockPullBaseImageStep,
            mockPullAndCacheBaseImageLayersStep,
            ImmutableList.of(
                mockBuildAndCacheApplicationLayerStepDependencies,
                mockBuildAndCacheApplicationLayerStepResources,
                mockBuildAndCacheApplicationLayerStepClasses));
    Image image = buildImageStep.getFuture().get().getFuture().get();

    Assert.assertEquals(ImmutableList.of("baseImageEntrypoint"), image.getEntrypoint());
    Assert.assertEquals(ImmutableList.of("catalina.sh", "run"), image.getProgramArguments());
  }

  @Test
  public void test_notInheritedProgramArguments() throws ExecutionException, InterruptedException {
    Mockito.when(mockContainerConfiguration.getEntrypoint())
        .thenReturn(ImmutableList.of("myEntrypoint"));
    Mockito.when(mockContainerConfiguration.getProgramArguments()).thenReturn(null);

    BuildImageStep buildImageStep =
        new BuildImageStep(
            MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor()),
            mockBuildConfiguration,
            ProgressEventDispatcher.newRoot(mockEventHandlers, "ignored", 1).newChildProducer(),
            mockPullBaseImageStep,
            mockPullAndCacheBaseImageLayersStep,
            ImmutableList.of(
                mockBuildAndCacheApplicationLayerStepDependencies,
                mockBuildAndCacheApplicationLayerStepResources,
                mockBuildAndCacheApplicationLayerStepClasses));
    Image image = buildImageStep.getFuture().get().getFuture().get();

    Assert.assertEquals(ImmutableList.of("myEntrypoint"), image.getEntrypoint());
    Assert.assertNull(image.getProgramArguments());
  }

  @Test
  public void test_generateHistoryObjects() throws ExecutionException, InterruptedException {
    BuildImageStep buildImageStep =
        new BuildImageStep(
            MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor()),
            mockBuildConfiguration,
            ProgressEventDispatcher.newRoot(mockEventHandlers, "ignored", 1).newChildProducer(),
            mockPullBaseImageStep,
            mockPullAndCacheBaseImageLayersStep,
            ImmutableList.of(
                mockBuildAndCacheApplicationLayerStepDependencies,
                mockBuildAndCacheApplicationLayerStepResources,
                mockBuildAndCacheApplicationLayerStepClasses,
                mockBuildAndCacheApplicationLayerStepExtraFiles));
    Image image = buildImageStep.getFuture().get().getFuture().get();

    // Make sure history is as expected
    HistoryEntry expectedAddedBaseLayerHistory =
        HistoryEntry.builder()
            .setCreationTimestamp(Instant.EPOCH)
            .setComment("auto-generated by Jib")
            .build();

    HistoryEntry expectedApplicationLayerHistoryDependencies =
        HistoryEntry.builder()
            .setCreationTimestamp(Instant.EPOCH)
            .setAuthor("Jib")
            .setCreatedBy("jib:null")
            .setComment("dependencies")
            .build();

    HistoryEntry expectedApplicationLayerHistoryResources =
        HistoryEntry.builder()
            .setCreationTimestamp(Instant.EPOCH)
            .setAuthor("Jib")
            .setCreatedBy("jib:null")
            .setComment("resources")
            .build();

    HistoryEntry expectedApplicationLayerHistoryClasses =
        HistoryEntry.builder()
            .setCreationTimestamp(Instant.EPOCH)
            .setAuthor("Jib")
            .setCreatedBy("jib:null")
            .setComment("classes")
            .build();

    HistoryEntry expectedApplicationLayerHistoryExtrafiles =
        HistoryEntry.builder()
            .setCreationTimestamp(Instant.EPOCH)
            .setAuthor("Jib")
            .setCreatedBy("jib:null")
            .setComment("extra files")
            .build();

    // Base layers (1 non-empty propagated, 2 empty propagated, 2 non-empty generated)
    Assert.assertEquals(nonEmptyLayerHistory, image.getHistory().get(0));
    Assert.assertEquals(emptyLayerHistory, image.getHistory().get(1));
    Assert.assertEquals(emptyLayerHistory, image.getHistory().get(2));
    Assert.assertEquals(expectedAddedBaseLayerHistory, image.getHistory().get(3));
    Assert.assertEquals(expectedAddedBaseLayerHistory, image.getHistory().get(4));

    // Application layers (4 generated)
    Assert.assertEquals(expectedApplicationLayerHistoryDependencies, image.getHistory().get(5));
    Assert.assertEquals(expectedApplicationLayerHistoryResources, image.getHistory().get(6));
    Assert.assertEquals(expectedApplicationLayerHistoryClasses, image.getHistory().get(7));
    Assert.assertEquals(expectedApplicationLayerHistoryExtrafiles, image.getHistory().get(8));

    // Should be exactly 9 total
    Assert.assertEquals(9, image.getHistory().size());
  }
}
