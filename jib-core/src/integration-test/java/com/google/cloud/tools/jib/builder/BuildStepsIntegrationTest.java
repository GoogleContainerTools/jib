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
import com.google.cloud.tools.jib.builder.steps.BuildResult;
import com.google.cloud.tools.jib.configuration.BuildConfiguration;
import com.google.cloud.tools.jib.configuration.ContainerConfiguration;
import com.google.cloud.tools.jib.configuration.ImageConfiguration;
import com.google.cloud.tools.jib.configuration.LayerConfiguration;
import com.google.cloud.tools.jib.docker.DockerClient;
import com.google.cloud.tools.jib.event.DefaultEventDispatcher;
import com.google.cloud.tools.jib.event.EventHandlers;
import com.google.cloud.tools.jib.event.JibEventType;
import com.google.cloud.tools.jib.event.progress.ProgressEventHandler;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;
import org.hamcrest.CoreMatchers;
import org.junit.AfterClass;
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

  /**
   * Helper class to hold a {@link ProgressEventHandler} and verify that it handles a full progress.
   */
  private static class ProgressChecker {

    private final ProgressEventHandler progressEventHandler =
        new ProgressEventHandler(
            update -> {
              lastProgress = update.getProgress();
              areTasksFinished = update.getUnfinishedAllocations().isEmpty();
            });

    private volatile double lastProgress = 0.0;
    private volatile boolean areTasksFinished = false;

    private void checkCompletion() {
      Assert.assertEquals(1.0, lastProgress, DOUBLE_ERROR_MARGIN);
      Assert.assertTrue(areTasksFinished);
    }
  }

  @ClassRule public static final LocalRegistry localRegistry = new LocalRegistry(5000);
  private static final ExecutorService executorService = Executors.newCachedThreadPool();
  private static final Logger logger = LoggerFactory.getLogger(BuildStepsIntegrationTest.class);

  private static final double DOUBLE_ERROR_MARGIN = 1e-10;

  @AfterClass
  public static void cleanUp() {
    executorService.shutdown();
  }

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
    Assert.assertThat(dockerConfigEnv, CoreMatchers.containsString("env1=envvalue1"));
    Assert.assertThat(dockerConfigEnv, CoreMatchers.containsString("env2=envvalue2"));
    String history = new Command("docker", "history", imageReference).run();
    Assert.assertThat(history, CoreMatchers.containsString("jib-integration-test"));
    Assert.assertThat(history, CoreMatchers.containsString("bazel build ..."));
  }

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
      throws IOException, InterruptedException, ExecutionException {
    ProgressChecker progressChecker = new ProgressChecker();

    long lastTime = System.nanoTime();
    BuildResult image1 =
        BuildSteps.forBuildToDockerRegistry(
                getBuildConfigurationBuilder(
                        ImageReference.of("gcr.io", "distroless/java", "latest"),
                        ImageReference.of("localhost:5000", "testimage", "testtag"))
                    .setEventDispatcher(
                        new DefaultEventDispatcher(
                            new EventHandlers()
                                .add(JibEventType.PROGRESS, progressChecker.progressEventHandler)))
                    .build())
            .run();
    progressChecker.checkCompletion();

    logger.info("Initial build time: " + ((System.nanoTime() - lastTime) / 1_000_000));

    lastTime = System.nanoTime();
    BuildResult image2 =
        BuildSteps.forBuildToDockerRegistry(
                getBuildConfigurationBuilder(
                        ImageReference.of("gcr.io", "distroless/java", "latest"),
                        ImageReference.of("localhost:5000", "testimage", "testtag"))
                    .build())
            .run();
    logger.info("Secondary build time: " + ((System.nanoTime() - lastTime) / 1_000_000));

    Assert.assertEquals(image1, image2);

    String imageReference = "localhost:5000/testimage:testtag";
    localRegistry.pull(imageReference);
    assertDockerInspect(imageReference);
    Assert.assertEquals(
        "Hello, world. An argument.\n", new Command("docker", "run", "--rm", imageReference).run());

    String imageReferenceByDigest = "localhost:5000/testimage@" + image1.getImageDigest();
    localRegistry.pull(imageReferenceByDigest);
    assertDockerInspect(imageReferenceByDigest);
    Assert.assertEquals(
        "Hello, world. An argument.\n",
        new Command("docker", "run", "--rm", imageReferenceByDigest).run());
  }

  @Test
  public void testSteps_forBuildToDockerRegistry_multipleTags()
      throws IOException, InterruptedException, ExecutionException {
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
      throws InvalidImageReferenceException, IOException, InterruptedException, ExecutionException {
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
      throws IOException, InterruptedException, ExecutionException {
    ProgressChecker progressChecker = new ProgressChecker();

    BuildConfiguration buildConfiguration =
        getBuildConfigurationBuilder(
                ImageReference.of("gcr.io", "distroless/java", "latest"),
                ImageReference.of(null, "testdocker", null))
            .setEventDispatcher(
                new DefaultEventDispatcher(
                    new EventHandlers()
                        .add(JibEventType.PROGRESS, progressChecker.progressEventHandler)))
            .build();
    BuildSteps.forBuildToDockerDaemon(DockerClient.newDefaultClient(), buildConfiguration).run();

    progressChecker.checkCompletion();

    assertDockerInspect("testdocker");
    Assert.assertEquals(
        "Hello, world. An argument.\n", new Command("docker", "run", "--rm", "testdocker").run());
  }

  @Test
  public void testSteps_forBuildToDockerDaemon_multipleTags()
      throws IOException, InterruptedException, ExecutionException {
    String imageReference = "testdocker";
    BuildConfiguration buildConfiguration =
        getBuildConfigurationBuilder(
                ImageReference.of("gcr.io", "distroless/java", "latest"),
                ImageReference.of(null, imageReference, null))
            .setAdditionalTargetImageTags(ImmutableSet.of("testtag2", "testtag3"))
            .build();
    BuildSteps.forBuildToDockerDaemon(DockerClient.newDefaultClient(), buildConfiguration).run();

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
      throws IOException, InterruptedException, ExecutionException {
    ProgressChecker progressChecker = new ProgressChecker();

    BuildConfiguration buildConfiguration =
        getBuildConfigurationBuilder(
                ImageReference.of("gcr.io", "distroless/java", "latest"),
                ImageReference.of(null, "testtar", null))
            .setEventDispatcher(
                new DefaultEventDispatcher(
                    new EventHandlers()
                        .add(JibEventType.PROGRESS, progressChecker.progressEventHandler)))
            .build();
    Path outputPath = temporaryFolder.newFolder().toPath().resolve("test.tar");
    BuildSteps.forBuildToTar(outputPath, buildConfiguration).run();

    progressChecker.checkCompletion();

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
        .setBaseImageLayersCacheDirectory(cacheDirectory)
        .setApplicationLayersCacheDirectory(cacheDirectory)
        .setAllowInsecureRegistries(true)
        .setLayerConfigurations(fakeLayerConfigurations)
        .setToolName("jib-integration-test")
        .setExecutorService(executorService);
  }
}
