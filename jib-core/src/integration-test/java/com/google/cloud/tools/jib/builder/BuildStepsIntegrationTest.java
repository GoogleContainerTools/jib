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

package com.google.cloud.tools.jib.builder;

import com.google.cloud.tools.jib.Command;
import com.google.cloud.tools.jib.configuration.BuildConfiguration;
import com.google.cloud.tools.jib.configuration.CacheConfiguration;
import com.google.cloud.tools.jib.configuration.CacheDirectoryCreationException;
import com.google.cloud.tools.jib.configuration.ContainerConfiguration;
import com.google.cloud.tools.jib.configuration.ImageConfiguration;
import com.google.cloud.tools.jib.configuration.LayerConfiguration;
import com.google.cloud.tools.jib.filesystem.AbsoluteUnixPath;
import com.google.cloud.tools.jib.frontend.ExposedPortsParser;
import com.google.cloud.tools.jib.frontend.JavaEntrypointConstructor;
import com.google.cloud.tools.jib.image.ImageReference;
import com.google.cloud.tools.jib.image.InvalidImageReferenceException;
import com.google.cloud.tools.jib.registry.LocalRegistry;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Integration tests for {@link BuildSteps}. */
public class BuildStepsIntegrationTest {

  @ClassRule public static final LocalRegistry localRegistry = new LocalRegistry(5000);

  /**
   * Lists the files in the {@code resourcePath} resources directory and builds a {@link
   * LayerConfiguration} from those files.
   */
  private static LayerConfiguration makeLayerConfiguration(
      String resourcePath, AbsoluteUnixPath pathInContainer)
      throws URISyntaxException, IOException {
    try (Stream<Path> fileStream =
        Files.list(Paths.get(Resources.getResource(resourcePath).toURI()))) {
      LayerConfiguration.Builder layerConfigurationBuilder = LayerConfiguration.builder();
      fileStream.forEach(
          sourceFile ->
              layerConfigurationBuilder.addEntry(
                  sourceFile, pathInContainer.resolve(sourceFile.getFileName())));
      return layerConfigurationBuilder.build();
    }
  }

  private static void assertDockerInspect(String imageReference)
      throws IOException, InterruptedException {
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
    String dockerConfigEnv =
        new Command("docker", "inspect", "-f", "{{.Config.Env}}", imageReference).run();
    Assert.assertThat(
        dockerConfigEnv, CoreMatchers.containsString("env1=envvalue1 env2=envvalue2"));
    String history = new Command("docker", "history", imageReference).run();
    Assert.assertThat(history, CoreMatchers.containsString("jib-integration-test"));
    Assert.assertThat(history, CoreMatchers.containsString("bazel build ..."));
  }

  private static final Logger logger = LoggerFactory.getLogger(BuildStepsIntegrationTest.class);

  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  private ImmutableList<LayerConfiguration> fakeLayerConfigurations;

  @Before
  public void setUp() throws IOException, URISyntaxException {
    fakeLayerConfigurations =
        ImmutableList.of(
            makeLayerConfiguration("application/dependencies", AbsoluteUnixPath.get("/app/libs/")),
            makeLayerConfiguration(
                "application/resources", AbsoluteUnixPath.get("/app/resources/")),
            makeLayerConfiguration("application/classes", AbsoluteUnixPath.get("/app/classes/")));
  }

  @Test
  public void testSteps_forBuildToDockerRegistry()
      throws IOException, InterruptedException, ExecutionException,
          CacheDirectoryCreationException {
    BuildSteps buildImageSteps =
        BuildSteps.forBuildToDockerRegistry(
            getBuildConfigurationBuilder(
                    ImageReference.of("gcr.io", "distroless/java", "latest"),
                    ImageReference.of("localhost:5000", "testimage", "testtag"))
                .build());

    long lastTime = System.nanoTime();
    buildImageSteps.run();
    logger.info("Initial build time: " + ((System.nanoTime() - lastTime) / 1_000_000));
    lastTime = System.nanoTime();
    buildImageSteps.run();
    logger.info("Secondary build time: " + ((System.nanoTime() - lastTime) / 1_000_000));

    String imageReference = "localhost:5000/testimage:testtag";
    localRegistry.pull(imageReference);
    assertDockerInspect(imageReference);
    Assert.assertEquals(
        "Hello, world. An argument.\n", new Command("docker", "run", "--rm", imageReference).run());
  }

  @Test
  public void testSteps_forBuildToDockerRegistry_multipleTags()
      throws IOException, InterruptedException, ExecutionException,
          CacheDirectoryCreationException {
    BuildSteps buildImageSteps =
        BuildSteps.forBuildToDockerRegistry(
            getBuildConfigurationBuilder(
                    ImageReference.of("gcr.io", "distroless/java", "latest"),
                    ImageReference.of("localhost:5000", "testimage", "testtag"))
                .setAdditionalTargetImageTags(ImmutableSet.of("testtag2", "testtag3"))
                .build());

    long lastTime = System.nanoTime();
    buildImageSteps.run();
    logger.info("Initial build time: " + ((System.nanoTime() - lastTime) / 1_000_000));
    lastTime = System.nanoTime();
    buildImageSteps.run();
    logger.info("Secondary build time: " + ((System.nanoTime() - lastTime) / 1_000_000));

    String imageReference = "localhost:5000/testimage:testtag";
    localRegistry.pull(imageReference);
    assertDockerInspect(imageReference);
    Assert.assertEquals(
        "Hello, world. An argument.\n", new Command("docker", "run", "--rm", imageReference).run());

    String imageReference2 = "localhost:5000/testimage:testtag2";
    localRegistry.pull(imageReference2);
    assertDockerInspect(imageReference2);
    Assert.assertEquals(
        "Hello, world. An argument.\n",
        new Command("docker", "run", "--rm", imageReference2).run());

    String imageReference3 = "localhost:5000/testimage:testtag3";
    localRegistry.pull(imageReference3);
    assertDockerInspect(imageReference3);
    Assert.assertEquals(
        "Hello, world. An argument.\n",
        new Command("docker", "run", "--rm", imageReference3).run());
  }

  @Test
  public void testSteps_forBuildToDockerRegistry_dockerHubBaseImage()
      throws InvalidImageReferenceException, IOException, InterruptedException,
          CacheDirectoryCreationException, ExecutionException {
    BuildSteps.forBuildToDockerRegistry(
            getBuildConfigurationBuilder(
                    ImageReference.parse("openjdk:8-jre-alpine"),
                    ImageReference.of("localhost:5000", "testimage", "testtag"))
                .build())
        .run();

    String imageReference = "localhost:5000/testimage:testtag";
    new Command("docker", "pull", imageReference).run();
    Assert.assertEquals(
        "Hello, world. An argument.\n", new Command("docker", "run", "--rm", imageReference).run());
  }

  @Test
  public void testSteps_forBuildToDockerDaemon()
      throws IOException, InterruptedException, ExecutionException,
          CacheDirectoryCreationException {
    String imageReference = "testdocker";
    BuildConfiguration buildConfiguration =
        getBuildConfigurationBuilder(
                ImageReference.of("gcr.io", "distroless/java", "latest"),
                ImageReference.of(null, imageReference, null))
            .build();
    BuildSteps.forBuildToDockerDaemon(buildConfiguration).run();

    assertDockerInspect(imageReference);
    Assert.assertEquals(
        "Hello, world. An argument.\n", new Command("docker", "run", "--rm", imageReference).run());
  }

  @Test
  public void testSteps_forBuildToDockerDaemon_multipleTags()
      throws IOException, InterruptedException, ExecutionException,
          CacheDirectoryCreationException {
    String imageReference = "testdocker";
    BuildConfiguration buildConfiguration =
        getBuildConfigurationBuilder(
                ImageReference.of("gcr.io", "distroless/java", "latest"),
                ImageReference.of(null, imageReference, null))
            .setAdditionalTargetImageTags(ImmutableSet.of("testtag2", "testtag3"))
            .build();
    BuildSteps.forBuildToDockerDaemon(buildConfiguration).run();

    assertDockerInspect(imageReference);
    Assert.assertEquals(
        "Hello, world. An argument.\n", new Command("docker", "run", "--rm", imageReference).run());
    assertDockerInspect(imageReference + ":testtag2");
    Assert.assertEquals(
        "Hello, world. An argument.\n",
        new Command("docker", "run", "--rm", imageReference + ":testtag2").run());
    assertDockerInspect(imageReference + ":testtag3");
    Assert.assertEquals(
        "Hello, world. An argument.\n",
        new Command("docker", "run", "--rm", imageReference + ":testtag3").run());
  }

  @Test
  public void testSteps_forBuildToTarball()
      throws IOException, InterruptedException, ExecutionException,
          CacheDirectoryCreationException {
    BuildConfiguration buildConfiguration =
        getBuildConfigurationBuilder(
                ImageReference.of("gcr.io", "distroless/java", "latest"),
                ImageReference.of(null, "testtar", null))
            .build();
    Path outputPath = temporaryFolder.newFolder().toPath().resolve("test.tar");
    BuildSteps.forBuildToTar(outputPath, buildConfiguration).run();

    new Command("docker", "load", "--input", outputPath.toString()).run();
    Assert.assertEquals(
        "Hello, world. An argument.\n", new Command("docker", "run", "--rm", "testtar").run());
  }

  private BuildConfiguration.Builder getBuildConfigurationBuilder(
      ImageReference baseImage, ImageReference targetImage) throws IOException {
    ImageConfiguration baseImageConfiguration = ImageConfiguration.builder(baseImage).build();
    ImageConfiguration targetImageConfiguration = ImageConfiguration.builder(targetImage).build();
    ContainerConfiguration containerConfiguration =
        ContainerConfiguration.builder()
            .setEntrypoint(
                JavaEntrypointConstructor.makeDefaultEntrypoint(
                    AbsoluteUnixPath.get("/app"), Collections.emptyList(), "HelloWorld"))
            .setProgramArguments(Collections.singletonList("An argument."))
            .setEnvironment(ImmutableMap.of("env1", "envvalue1", "env2", "envvalue2"))
            .setExposedPorts(
                ExposedPortsParser.parse(Arrays.asList("1000", "2000-2002/tcp", "3000/udp")))
            .setLabels(ImmutableMap.of("key1", "value1", "key2", "value2"))
            .build();
    Path cacheDirectory = temporaryFolder.newFolder().toPath();
    return BuildConfiguration.builder()
        .setBaseImageConfiguration(baseImageConfiguration)
        .setTargetImageConfiguration(targetImageConfiguration)
        .setContainerConfiguration(containerConfiguration)
        .setBaseImageLayersCacheConfiguration(CacheConfiguration.forPath(cacheDirectory))
        .setApplicationLayersCacheConfiguration(CacheConfiguration.forPath(cacheDirectory))
        .setAllowInsecureRegistries(true)
        .setLayerConfigurations(fakeLayerConfigurations)
        .setToolName("jib-integration-test");
  }
}
