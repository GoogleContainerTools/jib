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

package com.google.cloud.tools.jib.builder;

import com.google.cloud.tools.jib.Command;
import com.google.cloud.tools.jib.cache.CacheDirectoryCreationException;
import com.google.cloud.tools.jib.cache.CacheDirectoryNotOwnedException;
import com.google.cloud.tools.jib.cache.CacheMetadataCorruptedException;
import com.google.cloud.tools.jib.cache.Caches;
import com.google.cloud.tools.jib.configuration.BuildConfiguration;
import com.google.cloud.tools.jib.configuration.ContainerConfiguration;
import com.google.cloud.tools.jib.configuration.ImageConfiguration;
import com.google.cloud.tools.jib.configuration.LayerConfiguration;
import com.google.cloud.tools.jib.frontend.ExposedPortsParser;
import com.google.cloud.tools.jib.frontend.JavaEntrypointConstructor;
import com.google.cloud.tools.jib.image.ImageReference;
import com.google.cloud.tools.jib.image.InvalidImageReferenceException;
import com.google.cloud.tools.jib.registry.LocalRegistry;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Integration tests for {@link BuildSteps}. */
public class BuildStepsIntegrationTest {

  @ClassRule public static final LocalRegistry localRegistry = new LocalRegistry(5000);

  /** Lists the files in the {@code resourcePath} resources directory. */
  private static ImmutableList<Path> getResourceFilesList(String resourcePath)
      throws URISyntaxException, IOException {
    try (Stream<Path> fileStream =
        Files.list(Paths.get(Resources.getResource(resourcePath).toURI()))) {
      return fileStream.collect(ImmutableList.toImmutableList());
    }
  }

  private static final TestJibLogger logger = new TestJibLogger();

  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  private ImmutableList<LayerConfiguration> fakeLayerConfigurations;

  @Before
  public void setUp() throws IOException, URISyntaxException {
    fakeLayerConfigurations =
        ImmutableList.of(
            LayerConfiguration.builder()
                .addEntry(getResourceFilesList("application/dependencies"), "/app/libs/")
                .build(),
            LayerConfiguration.builder()
                .addEntry(getResourceFilesList("application/resources"), "/app/resources/")
                .build(),
            LayerConfiguration.builder()
                .addEntry(getResourceFilesList("application/classes"), "/app/classes/")
                .build());
  }

  @Test
  public void testSteps_forBuildToDockerRegistry()
      throws IOException, InterruptedException, CacheMetadataCorruptedException, ExecutionException,
          CacheDirectoryNotOwnedException, CacheDirectoryCreationException {
    BuildSteps buildImageSteps =
        getBuildSteps(
            getBuildConfiguration(
                ImageReference.of("gcr.io", "distroless/java", "latest"),
                ImageReference.of("localhost:5000", "testimage", "testtag")));

    long lastTime = System.nanoTime();
    buildImageSteps.run();
    logger.info("Initial build time: " + ((System.nanoTime() - lastTime) / 1_000_000));
    lastTime = System.nanoTime();
    buildImageSteps.run();
    logger.info("Secondary build time: " + ((System.nanoTime() - lastTime) / 1_000_000));

    String imageReference = "localhost:5000/testimage:testtag";
    localRegistry.pull(imageReference);
    Assert.assertThat(
        new Command("docker", "inspect", imageReference).run(),
        CoreMatchers.containsString(
            "            \"ExposedPorts\": {\n"
                + "                \"1000/tcp\": {},\n"
                + "                \"2000/tcp\": {},\n"
                + "                \"2001/tcp\": {},\n"
                + "                \"2002/tcp\": {},\n"
                + "                \"3000/udp\": {}"));
    String history = new Command("docker", "history", imageReference).run();
    Assert.assertThat(history, CoreMatchers.containsString("jib-integration-test"));
    Assert.assertThat(history, CoreMatchers.containsString("bazel build ..."));
    Assert.assertEquals(
        "Hello, world. An argument.\n", new Command("docker", "run", imageReference).run());
  }

  @Test
  public void testSteps_forBuildToDockerRegistry_dockerHubBaseImage()
      throws InvalidImageReferenceException, IOException, InterruptedException, ExecutionException,
          CacheDirectoryCreationException, CacheMetadataCorruptedException,
          CacheDirectoryNotOwnedException {
    getBuildSteps(
            getBuildConfiguration(
                ImageReference.parse("openjdk:8-jre-alpine"),
                ImageReference.of("localhost:5000", "testimage", "testtag")))
        .run();

    String imageReference = "localhost:5000/testimage:testtag";
    new Command("docker", "pull", imageReference).run();
    Assert.assertEquals(
        "Hello, world. An argument.\n", new Command("docker", "run", imageReference).run());
  }

  @Test
  public void testSteps_forBuildToDockerDaemon()
      throws IOException, InterruptedException, CacheMetadataCorruptedException, ExecutionException,
          CacheDirectoryNotOwnedException, CacheDirectoryCreationException {
    String imageReference = "testdocker";
    BuildConfiguration buildConfiguration =
        getBuildConfiguration(
            ImageReference.of("gcr.io", "distroless/java", "latest"),
            ImageReference.of(null, imageReference, null));
    Path cacheDirectory = temporaryFolder.newFolder().toPath();
    BuildSteps.forBuildToDockerDaemon(
            buildConfiguration,
            new Caches.Initializer(cacheDirectory, false, cacheDirectory, false))
        .run();

    String dockerContainerConfig = new Command("docker", "inspect", imageReference).run();
    Assert.assertThat(
        dockerContainerConfig,
        CoreMatchers.containsString(
            "            \"ExposedPorts\": {\n"
                + "                \"1000/tcp\": {},\n"
                + "                \"2000/tcp\": {},\n"
                + "                \"2001/tcp\": {},\n"
                + "                \"2002/tcp\": {},\n"
                + "                \"3000/udp\": {}"));
    Assert.assertThat(
        dockerContainerConfig,
        CoreMatchers.containsString(
            "            \"Labels\": {\n"
                + "                \"key1\": \"value1\",\n"
                + "                \"key2\": \"value2\"\n"
                + "            }"));
    String history = new Command("docker", "history", imageReference).run();
    Assert.assertThat(history, CoreMatchers.containsString("jib-integration-test"));
    Assert.assertThat(history, CoreMatchers.containsString("bazel build ..."));
    Assert.assertEquals(
        "Hello, world. An argument.\n", new Command("docker", "run", imageReference).run());
  }

  @Test
  public void testSteps_forBuildToTarball()
      throws IOException, InterruptedException, CacheMetadataCorruptedException, ExecutionException,
          CacheDirectoryNotOwnedException, CacheDirectoryCreationException {
    BuildConfiguration buildConfiguration =
        getBuildConfiguration(
            ImageReference.of("gcr.io", "distroless/java", "latest"),
            ImageReference.of(null, "testtar", null));
    Path outputPath = temporaryFolder.newFolder().toPath().resolve("test.tar");
    Path cacheDirectory = temporaryFolder.newFolder().toPath();
    BuildSteps.forBuildToTar(
            outputPath,
            buildConfiguration,
            new Caches.Initializer(cacheDirectory, false, cacheDirectory, false))
        .run();

    new Command("docker", "load", "--input", outputPath.toString()).run();
    Assert.assertEquals(
        "Hello, world. An argument.\n", new Command("docker", "run", "testtar").run());
  }

  private BuildSteps getBuildSteps(BuildConfiguration buildConfiguration) throws IOException {
    Path cacheDirectory = temporaryFolder.newFolder().toPath();
    return BuildSteps.forBuildToDockerRegistry(
        buildConfiguration, new Caches.Initializer(cacheDirectory, false, cacheDirectory, false));
  }

  private BuildConfiguration getBuildConfiguration(
      ImageReference baseImage, ImageReference targetImage) {
    ImageConfiguration baseImageConfiguration = ImageConfiguration.builder(baseImage).build();
    ImageConfiguration targetImageConfiguration = ImageConfiguration.builder(targetImage).build();
    ContainerConfiguration containerConfiguration =
        ContainerConfiguration.builder()
            .setEntrypoint(
                JavaEntrypointConstructor.makeDefaultEntrypoint(
                    Collections.emptyList(), "HelloWorld"))
            .setProgramArguments(Collections.singletonList("An argument."))
            .setExposedPorts(
                ExposedPortsParser.parse(Arrays.asList("1000", "2000-2002/tcp", "3000/udp")))
            .setLabels(ImmutableMap.of("key1", "value1", "key2", "value2"))
            .build();
    return BuildConfiguration.builder(logger)
        .setBaseImageConfiguration(baseImageConfiguration)
        .setTargetImageConfiguration(targetImageConfiguration)
        .setContainerConfiguration(containerConfiguration)
        .setAllowInsecureRegistries(true)
        .setLayerConfigurations(fakeLayerConfigurations)
        .setCreatedBy("jib-integration-test")
        .build();
  }
}
