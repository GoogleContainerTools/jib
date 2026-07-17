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

import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.buildplan.ContainerBuildPlan;
import com.google.cloud.tools.jib.api.buildplan.FileEntriesLayer;
import com.google.cloud.tools.jib.api.buildplan.FileEntry;
import com.google.cloud.tools.jib.api.buildplan.FilePermissions;
import com.google.cloud.tools.jib.api.buildplan.ImageFormat;
import com.google.cloud.tools.jib.api.buildplan.Platform;
import com.google.cloud.tools.jib.api.buildplan.Port;
import com.google.cloud.tools.jib.configuration.BuildContext;
import com.google.cloud.tools.jib.configuration.ContainerConfiguration;
import com.google.cloud.tools.jib.configuration.ImageConfiguration;
import com.google.cloud.tools.jib.image.json.OciManifestTemplate;
import com.google.cloud.tools.jib.image.json.V22ManifestTemplate;
import com.google.cloud.tools.jib.registry.credentials.CredentialRetrievalException;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.IOException;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
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

  @Spy private BuildContext.Builder spyBuildContextBuilder;
  @Mock private FileEntriesLayer mockLayerConfiguration1;
  @Mock private FileEntriesLayer mockLayerConfiguration2;
  @Mock private CredentialRetriever mockCredentialRetriever;
  @Mock private Consumer<JibEvent> mockJibEventConsumer;
  @Mock private JibEvent mockJibEvent;

  @Test
  public void testToBuildContext_containerConfigurationSet()
      throws InvalidImageReferenceException, CacheDirectoryCreationException {
    ImageConfiguration imageConfiguration =
        ImageConfiguration.builder(ImageReference.parse("base/image")).build();
    JibContainerBuilder jibContainerBuilder =
        new JibContainerBuilder(imageConfiguration, spyBuildContextBuilder)
            .setPlatforms(ImmutableSet.of(new Platform("testArchitecture", "testOS")))
            .setEntrypoint(Arrays.asList("entry", "point"))
            .setEnvironment(ImmutableMap.of("name", "value"))
            .setExposedPorts(ImmutableSet.of(Port.tcp(1234), Port.udp(5678)))
            .setLabels(ImmutableMap.of("key", "value"))
            .setProgramArguments(Arrays.asList("program", "arguments"))
            .setCreationTime(Instant.ofEpochMilli(1000))
            .setUser("user")
            .setWorkingDirectory(AbsoluteUnixPath.get("/working/directory"));

    BuildContext buildContext =
        jibContainerBuilder.toBuildContext(Containerizer.to(RegistryImage.named("target/image")));
    ContainerConfiguration containerConfiguration = buildContext.getContainerConfiguration();
    Assert.assertEquals(
        ImmutableSet.of(new Platform("testArchitecture", "testOS")),
        containerConfiguration.getPlatforms());
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
  public void testToBuildContext_containerConfigurationAdd()
      throws InvalidImageReferenceException, CacheDirectoryCreationException {
    ImageConfiguration imageConfiguration =
        ImageConfiguration.builder(ImageReference.parse("base/image")).build();
    JibContainerBuilder jibContainerBuilder =
        new JibContainerBuilder(imageConfiguration, spyBuildContextBuilder)
            .addPlatform("testArchitecture", "testOS")
            .setEntrypoint("entry", "point")
            .setEnvironment(ImmutableMap.of("name", "value"))
            .addEnvironmentVariable("environment", "variable")
            .setExposedPorts(Port.tcp(1234), Port.udp(5678))
            .addExposedPort(Port.tcp(1337))
            .setLabels(ImmutableMap.of("key", "value"))
            .addLabel("added", "label")
            .setProgramArguments("program", "arguments");

    BuildContext buildContext =
        jibContainerBuilder.toBuildContext(Containerizer.to(RegistryImage.named("target/image")));
    ContainerConfiguration containerConfiguration = buildContext.getContainerConfiguration();
    Assert.assertEquals(
        ImmutableSet.of(new Platform("testArchitecture", "testOS"), new Platform("amd64", "linux")),
        containerConfiguration.getPlatforms());
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
  public void testToBuildContext()
      throws InvalidImageReferenceException, CredentialRetrievalException,
          CacheDirectoryCreationException {
    ExecutorService executorService = MoreExecutors.newDirectExecutorService();
    RegistryImage targetImage =
        RegistryImage.named(ImageReference.of("gcr.io", "my-project/my-app", null))
            .addCredential("username", "password");
    Containerizer containerizer =
        Containerizer.to(targetImage)
            .setBaseImageLayersCache(Paths.get("base/image/layers"))
            .setApplicationLayersCache(Paths.get("application/layers"))
            .setExecutorService(executorService)
            .addEventHandler(mockJibEventConsumer)
            .setAlwaysCacheBaseImage(false);

    ImageConfiguration baseImageConfiguration =
        ImageConfiguration.builder(ImageReference.parse("base/image"))
            .setCredentialRetrievers(Collections.singletonList(mockCredentialRetriever))
            .build();
    JibContainerBuilder jibContainerBuilder =
        new JibContainerBuilder(baseImageConfiguration, spyBuildContextBuilder)
            .setFileEntriesLayers(Arrays.asList(mockLayerConfiguration1, mockLayerConfiguration2));
    BuildContext buildContext = jibContainerBuilder.toBuildContext(containerizer);

    Assert.assertEquals(
        spyBuildContextBuilder.build().getContainerConfiguration(),
        buildContext.getContainerConfiguration());

    Assert.assertEquals(
        "base/image", buildContext.getBaseImageConfiguration().getImage().toString());
    Assert.assertEquals(
        Arrays.asList(mockCredentialRetriever),
        buildContext.getBaseImageConfiguration().getCredentialRetrievers());

    Assert.assertEquals(
        "gcr.io/my-project/my-app",
        buildContext.getTargetImageConfiguration().getImage().toString());
    Assert.assertEquals(
        1, buildContext.getTargetImageConfiguration().getCredentialRetrievers().size());
    Assert.assertEquals(
        Credential.from("username", "password"),
        buildContext
            .getTargetImageConfiguration()
            .getCredentialRetrievers()
            .get(0)
            .retrieve()
            .orElseThrow(AssertionError::new));

    Assert.assertEquals(ImmutableSet.of("latest"), buildContext.getAllTargetImageTags());

    Mockito.verify(spyBuildContextBuilder)
        .setBaseImageLayersCacheDirectory(Paths.get("base/image/layers"));
    Mockito.verify(spyBuildContextBuilder)
        .setApplicationLayersCacheDirectory(Paths.get("application/layers"));

    Assert.assertEquals(
        Arrays.asList(mockLayerConfiguration1, mockLayerConfiguration2),
        buildContext.getLayerConfigurations());

    Assert.assertSame(executorService, buildContext.getExecutorService());

    buildContext.getEventHandlers().dispatch(mockJibEvent);
    Mockito.verify(mockJibEventConsumer).accept(mockJibEvent);

    Assert.assertEquals("jib-core", buildContext.getToolName());

    Assert.assertSame(V22ManifestTemplate.class, buildContext.getTargetFormat());

    Assert.assertEquals("jib-core", buildContext.getToolName());

    // Changes jibContainerBuilder.
    buildContext =
        jibContainerBuilder
            .setFormat(ImageFormat.OCI)
            .toBuildContext(
                containerizer
                    .withAdditionalTag("tag1")
                    .withAdditionalTag("tag2")
                    .setToolName("toolName"));
    Assert.assertSame(OciManifestTemplate.class, buildContext.getTargetFormat());
    Assert.assertEquals(
        ImmutableSet.of("latest", "tag1", "tag2"), buildContext.getAllTargetImageTags());
    Assert.assertEquals("toolName", buildContext.getToolName());
    Assert.assertFalse(buildContext.getAlwaysCacheBaseImage());
  }

  @Test
  public void testToContainerBuildPlan_default() throws InvalidImageReferenceException {
    ImageConfiguration imageConfiguration =
        ImageConfiguration.builder(ImageReference.parse("base/image")).build();
    JibContainerBuilder containerBuilder =
        new JibContainerBuilder(imageConfiguration, spyBuildContextBuilder);

    ContainerBuildPlan buildPlan = containerBuilder.toContainerBuildPlan();
    Assert.assertEquals("base/image", buildPlan.getBaseImage());
    Assert.assertEquals(ImmutableSet.of(new Platform("amd64", "linux")), buildPlan.getPlatforms());
    Assert.assertEquals(Instant.EPOCH, buildPlan.getCreationTime());
    Assert.assertEquals(ImageFormat.Docker, buildPlan.getFormat());
    Assert.assertEquals(Collections.emptyMap(), buildPlan.getEnvironment());
    Assert.assertEquals(Collections.emptyMap(), buildPlan.getLabels());
    Assert.assertEquals(Collections.emptySet(), buildPlan.getVolumes());
    Assert.assertEquals(Collections.emptySet(), buildPlan.getExposedPorts());
    Assert.assertNull(buildPlan.getUser());
    Assert.assertNull(buildPlan.getWorkingDirectory());
    Assert.assertNull(buildPlan.getEntrypoint());
    Assert.assertNull(buildPlan.getCmd());
    Assert.assertEquals(Collections.emptyList(), buildPlan.getLayers());
  }

  @Test
  public void testToContainerBuildPlan() throws InvalidImageReferenceException, IOException {
    ImageConfiguration imageConfiguration =
        ImageConfiguration.builder(ImageReference.parse("base/image")).build();
    JibContainerBuilder containerBuilder =
        new JibContainerBuilder(imageConfiguration, spyBuildContextBuilder)
            .setPlatforms(ImmutableSet.of(new Platform("testArchitecture", "testOS")))
            .setCreationTime(Instant.ofEpochMilli(1000))
            .setFormat(ImageFormat.OCI)
            .setEnvironment(ImmutableMap.of("env", "var"))
            .setLabels(ImmutableMap.of("com.example.label", "value"))
            .setVolumes(AbsoluteUnixPath.get("/mnt/vol"), AbsoluteUnixPath.get("/media/data"))
            .setExposedPorts(ImmutableSet.of(Port.tcp(1234), Port.udp(5678)))
            .setUser("user")
            .setWorkingDirectory(AbsoluteUnixPath.get("/working/directory"))
            .setEntrypoint(Arrays.asList("entry", "point"))
            .setProgramArguments(Arrays.asList("program", "arguments"))
            .addLayer(Arrays.asList(Paths.get("/non/existing/foo")), "/into/this");

    ContainerBuildPlan buildPlan = containerBuilder.toContainerBuildPlan();
    Assert.assertEquals("base/image", buildPlan.getBaseImage());
    Assert.assertEquals(
        ImmutableSet.of(new Platform("testArchitecture", "testOS")), buildPlan.getPlatforms());
    Assert.assertEquals(Instant.ofEpochMilli(1000), buildPlan.getCreationTime());
    Assert.assertEquals(ImageFormat.OCI, buildPlan.getFormat());
    Assert.assertEquals(ImmutableMap.of("env", "var"), buildPlan.getEnvironment());
    Assert.assertEquals(ImmutableMap.of("com.example.label", "value"), buildPlan.getLabels());
    Assert.assertEquals(
        ImmutableSet.of(AbsoluteUnixPath.get("/mnt/vol"), AbsoluteUnixPath.get("/media/data")),
        buildPlan.getVolumes());
    Assert.assertEquals(
        ImmutableSet.of(Port.tcp(1234), Port.udp(5678)), buildPlan.getExposedPorts());
    Assert.assertEquals("user", buildPlan.getUser());
    Assert.assertEquals(
        AbsoluteUnixPath.get("/working/directory"), buildPlan.getWorkingDirectory());
    Assert.assertEquals(Arrays.asList("entry", "point"), buildPlan.getEntrypoint());
    Assert.assertEquals(Arrays.asList("program", "arguments"), buildPlan.getCmd());

    Assert.assertEquals(1, buildPlan.getLayers().size());
    MatcherAssert.assertThat(
        buildPlan.getLayers().get(0), CoreMatchers.instanceOf(FileEntriesLayer.class));
    Assert.assertEquals(
        Arrays.asList(
            new FileEntry(
                Paths.get("/non/existing/foo"),
                AbsoluteUnixPath.get("/into/this/foo"),
                FilePermissions.fromOctalString("644"),
                Instant.ofEpochSecond(1))),
        ((FileEntriesLayer) buildPlan.getLayers().get(0)).getEntries());
  }

  @Test
  public void setApplyContainerBuildPlan()
      throws InvalidImageReferenceException, CacheDirectoryCreationException {
    FileEntriesLayer layer =
        FileEntriesLayer.builder()
            .addEntry(Paths.get("/src/file/foo"), AbsoluteUnixPath.get("/path/in/container"))
            .build();
    ContainerBuildPlan buildPlan =
        ContainerBuildPlan.builder()
            .setBaseImage("some/base")
            .setPlatforms(ImmutableSet.of(new Platform("testArchitecture", "testOS")))
            .setFormat(ImageFormat.OCI)
            .setCreationTime(Instant.ofEpochMilli(30))
            .setEnvironment(ImmutableMap.of("env", "var"))
            .setVolumes(
                ImmutableSet.of(AbsoluteUnixPath.get("/mnt/foo"), AbsoluteUnixPath.get("/bar")))
            .setLabels(ImmutableMap.of("com.example.label", "cool"))
            .setExposedPorts(ImmutableSet.of(Port.tcp(443)))
            .setLayers(Arrays.asList(layer))
            .setUser(":")
            .setWorkingDirectory(AbsoluteUnixPath.get("/workspace"))
            .setEntrypoint(Arrays.asList("foo", "entrypoint"))
            .setCmd(Arrays.asList("bar", "cmd"))
            .build();

    ImageConfiguration imageConfiguration =
        ImageConfiguration.builder(ImageReference.parse("initial/base")).build();
    JibContainerBuilder containerBuilder =
        new JibContainerBuilder(imageConfiguration, spyBuildContextBuilder)
            .applyContainerBuildPlan(buildPlan);

    BuildContext buildContext =
        containerBuilder.toBuildContext(Containerizer.to(RegistryImage.named("target/image")));
    Assert.assertEquals(
        "some/base", buildContext.getBaseImageConfiguration().getImage().toString());
    Assert.assertEquals(OciManifestTemplate.class, buildContext.getTargetFormat());
    Assert.assertEquals(1, buildContext.getLayerConfigurations().size());
    Assert.assertEquals(
        1, ((FileEntriesLayer) buildContext.getLayerConfigurations().get(0)).getEntries().size());
    Assert.assertEquals(
        Arrays.asList(
            new FileEntry(
                Paths.get("/src/file/foo"),
                AbsoluteUnixPath.get("/path/in/container"),
                FilePermissions.fromOctalString("644"),
                Instant.ofEpochSecond(1))),
        ((FileEntriesLayer) buildContext.getLayerConfigurations().get(0)).getEntries());

    ContainerConfiguration containerConfiguration = buildContext.getContainerConfiguration();
    Assert.assertEquals(Instant.ofEpochMilli(30), containerConfiguration.getCreationTime());
    Assert.assertEquals(ImmutableMap.of("env", "var"), containerConfiguration.getEnvironmentMap());
    Assert.assertEquals(
        ImmutableMap.of("com.example.label", "cool"), containerConfiguration.getLabels());
    Assert.assertEquals(
        ImmutableSet.of(AbsoluteUnixPath.get("/mnt/foo"), AbsoluteUnixPath.get("/bar")),
        containerConfiguration.getVolumes());
    Assert.assertEquals(ImmutableSet.of(Port.tcp(443)), containerConfiguration.getExposedPorts());
    Assert.assertEquals(":", containerConfiguration.getUser());
    Assert.assertEquals(
        AbsoluteUnixPath.get("/workspace"), containerConfiguration.getWorkingDirectory());
    Assert.assertEquals(Arrays.asList("foo", "entrypoint"), containerConfiguration.getEntrypoint());
    Assert.assertEquals(Arrays.asList("bar", "cmd"), containerConfiguration.getProgramArguments());

    ContainerBuildPlan convertedPlan = containerBuilder.toContainerBuildPlan();
    Assert.assertEquals(
        ImmutableSet.of(new Platform("testArchitecture", "testOS")), convertedPlan.getPlatforms());
  }
}
