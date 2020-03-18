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
import com.google.cloud.tools.jib.api.buildplan.FileEntriesLayer;
import com.google.cloud.tools.jib.api.buildplan.ImageFormat;
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
      throws InvalidImageReferenceException, CacheDirectoryCreationException, IOException {
    ImageConfiguration imageConfiguration =
        ImageConfiguration.builder(ImageReference.parse("base/image")).build();
    JibContainerBuilder jibContainerBuilder =
        new JibContainerBuilder(imageConfiguration, spyBuildContextBuilder)
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
      throws InvalidImageReferenceException, CacheDirectoryCreationException, IOException {
    ImageConfiguration imageConfiguration =
        ImageConfiguration.builder(ImageReference.parse("base/image")).build();
    JibContainerBuilder jibContainerBuilder =
        new JibContainerBuilder(imageConfiguration, spyBuildContextBuilder)
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
      throws InvalidImageReferenceException, CredentialRetrievalException, IOException,
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
}
