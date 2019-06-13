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

import com.google.cloud.tools.jib.Command;
import com.google.cloud.tools.jib.event.events.ProgressEvent;
import com.google.cloud.tools.jib.event.progress.ProgressEventHandler;
import com.google.cloud.tools.jib.registry.LocalRegistry;
import com.google.common.base.Splitter;
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
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;
import org.hamcrest.CoreMatchers;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// TODO: now it looks like we can move everything here into JibIntegrationTest.
/** Integration tests for {@link Containerizer}. */
public class ContainerizerIntegrationTest {

  /**
   * Helper class to hold a {@link ProgressEventHandler} and verify that it handles a full progress.
   */
  private static class ProgressChecker {

    private final ProgressEventHandler progressEventHandler =
        new ProgressEventHandler(
            update -> {
              lastProgress = update.getProgress();
              areTasksFinished = update.getUnfinishedLeafTasks().isEmpty();
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
  private static final Logger logger = LoggerFactory.getLogger(ContainerizerIntegrationTest.class);
  private static final String DISTROLESS_DIGEST =
      "sha256:f488c213f278bc5f9ffe3ddf30c5dbb2303a15a74146b738d12453088e662880";
  private static final double DOUBLE_ERROR_MARGIN = 1e-10;

  public static ImmutableList<LayerConfiguration> fakeLayerConfigurations;

  @BeforeClass
  public static void setUp() throws URISyntaxException, IOException {
    fakeLayerConfigurations =
        ImmutableList.of(
            makeLayerConfiguration("core/application/dependencies", "/app/libs/"),
            makeLayerConfiguration("core/application/resources", "/app/resources/"),
            makeLayerConfiguration("core/application/classes", "/app/classes/"));
  }

  @AfterClass
  public static void cleanUp() {
    executorService.shutdown();
  }

  /**
   * Lists the files in the {@code resourcePath} resources directory and builds a {@link
   * LayerConfiguration} from those files.
   */
  private static LayerConfiguration makeLayerConfiguration(
      String resourcePath, String pathInContainer) throws URISyntaxException, IOException {
    try (Stream<Path> fileStream =
        Files.list(Paths.get(Resources.getResource(resourcePath).toURI()))) {
      LayerConfiguration.Builder layerConfigurationBuilder = LayerConfiguration.builder();
      fileStream.forEach(
          sourceFile ->
              layerConfigurationBuilder.addEntry(
                  sourceFile, AbsoluteUnixPath.get(pathInContainer + sourceFile.getFileName())));
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

  private static void assertLayerSizer(int expected, String imageReference)
      throws IOException, InterruptedException {
    Command command =
        new Command("docker", "inspect", "-f", "{{join .RootFS.Layers \",\"}}", imageReference);
    String layers = command.run().trim();
    Assert.assertEquals(expected, Splitter.on(",").splitToList(layers).size());
  }

  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  private final ProgressChecker progressChecker = new ProgressChecker();

  @Test
  public void testSteps_forBuildToDockerRegistry()
      throws IOException, InterruptedException, ExecutionException, RegistryException,
          CacheDirectoryCreationException {
    long lastTime = System.nanoTime();
    JibContainer image1 =
        buildRegistryImage(
            ImageReference.of("gcr.io", "distroless/java", DISTROLESS_DIGEST),
            ImageReference.of("localhost:5000", "testimage", "testtag"),
            Collections.emptyList());

    progressChecker.checkCompletion();

    logger.info("Initial build time: " + ((System.nanoTime() - lastTime) / 1_000_000));

    lastTime = System.nanoTime();
    JibContainer image2 =
        buildRegistryImage(
            ImageReference.of("gcr.io", "distroless/java", DISTROLESS_DIGEST),
            ImageReference.of("localhost:5000", "testimage", "testtag"),
            Collections.emptyList());

    logger.info("Secondary build time: " + ((System.nanoTime() - lastTime) / 1_000_000));

    Assert.assertEquals(image1, image2);

    String imageReference = "localhost:5000/testimage:testtag";
    localRegistry.pull(imageReference);
    assertDockerInspect(imageReference);
    assertLayerSizer(7, imageReference);
    Assert.assertEquals(
        "Hello, world. An argument.\n", new Command("docker", "run", "--rm", imageReference).run());

    String imageReferenceByDigest = "localhost:5000/testimage@" + image1.getDigest();
    localRegistry.pull(imageReferenceByDigest);
    assertDockerInspect(imageReferenceByDigest);
    Assert.assertEquals(
        "Hello, world. An argument.\n",
        new Command("docker", "run", "--rm", imageReferenceByDigest).run());
  }

  @Test
  public void testSteps_forBuildToDockerRegistry_multipleTags()
      throws IOException, InterruptedException, ExecutionException, RegistryException,
          CacheDirectoryCreationException {
    buildRegistryImage(
        ImageReference.of("gcr.io", "distroless/java", DISTROLESS_DIGEST),
        ImageReference.of("localhost:5000", "testimage", "testtag"),
        Arrays.asList("testtag2", "testtag3"));

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
  public void tesBuildToDockerRegistry_dockerHubBaseImage()
      throws InvalidImageReferenceException, IOException, InterruptedException, ExecutionException,
          RegistryException, CacheDirectoryCreationException {
    buildRegistryImage(
        ImageReference.parse("openjdk:8-jre-alpine"),
        ImageReference.of("localhost:5000", "testimage", "testtag"),
        Collections.emptyList());

    String imageReference = "localhost:5000/testimage:testtag";
    new Command("docker", "pull", imageReference).run();
    Assert.assertEquals(
        "Hello, world. An argument.\n", new Command("docker", "run", "--rm", imageReference).run());
  }

  @Test
  public void testBuildToDockerDaemon()
      throws IOException, InterruptedException, ExecutionException, RegistryException,
          CacheDirectoryCreationException {
    buildDockerDaemonImage(
        ImageReference.of("gcr.io", "distroless/java", DISTROLESS_DIGEST),
        ImageReference.of(null, "testdocker", null),
        Collections.emptyList());

    progressChecker.checkCompletion();

    assertDockerInspect("testdocker");
    assertLayerSizer(7, "testdocker");
    Assert.assertEquals(
        "Hello, world. An argument.\n", new Command("docker", "run", "--rm", "testdocker").run());
  }

  @Test
  public void testBuildToDockerDaemon_multipleTags()
      throws IOException, InterruptedException, ExecutionException, RegistryException,
          CacheDirectoryCreationException {
    String imageReference = "testdocker";
    buildDockerDaemonImage(
        ImageReference.of("gcr.io", "distroless/java", DISTROLESS_DIGEST),
        ImageReference.of(null, imageReference, null),
        Arrays.asList("testtag2", "testtag3"));

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
  public void testBuildTarball()
      throws IOException, InterruptedException, ExecutionException, RegistryException,
          CacheDirectoryCreationException {
    Path outputPath = temporaryFolder.newFolder().toPath().resolve("test.tar");
    buildTarImage(
        ImageReference.of("gcr.io", "distroless/java", DISTROLESS_DIGEST),
        ImageReference.of(null, "testtar", null),
        outputPath,
        Collections.emptyList());

    progressChecker.checkCompletion();

    new Command("docker", "load", "--input", outputPath.toString()).run();
    assertLayerSizer(7, "testtar");
    Assert.assertEquals(
        "Hello, world. An argument.\n", new Command("docker", "run", "--rm", "testtar").run());
  }

  private JibContainer buildRegistryImage(
      ImageReference baseImage, ImageReference targetImage, List<String> additionalTags)
      throws IOException, InterruptedException, RegistryException, CacheDirectoryCreationException,
          ExecutionException {
    return buildImage(
        baseImage, Containerizer.to(RegistryImage.named(targetImage)), additionalTags);
  }

  private JibContainer buildDockerDaemonImage(
      ImageReference baseImage, ImageReference targetImage, List<String> additionalTags)
      throws IOException, InterruptedException, RegistryException, CacheDirectoryCreationException,
          ExecutionException {
    return buildImage(
        baseImage, Containerizer.to(DockerDaemonImage.named(targetImage)), additionalTags);
  }

  private JibContainer buildTarImage(
      ImageReference baseImage,
      ImageReference targetImage,
      Path outputPath,
      List<String> additionalTags)
      throws IOException, InterruptedException, RegistryException, CacheDirectoryCreationException,
          ExecutionException {
    return buildImage(
        baseImage,
        Containerizer.to(TarImage.named(targetImage).saveTo(outputPath)),
        additionalTags);
  }

  private JibContainer buildImage(
      ImageReference baseImage, Containerizer containerizer, List<String> additionalTags)
      throws IOException, InterruptedException, RegistryException, CacheDirectoryCreationException,
          ExecutionException {
    JibContainerBuilder containerBuilder =
        Jib.from(baseImage)
            .setEntrypoint(
                Arrays.asList(
                    "java", "-cp", "/app/resources:/app/classes:/app/libs/*", "HelloWorld"))
            .setProgramArguments(Collections.singletonList("An argument."))
            .setEnvironment(ImmutableMap.of("env1", "envvalue1", "env2", "envvalue2"))
            .setExposedPorts(Ports.parse(Arrays.asList("1000", "2000-2002/tcp", "3000/udp")))
            .setLabels(ImmutableMap.of("key1", "value1", "key2", "value2"))
            .setLayers(fakeLayerConfigurations);

    Path cacheDirectory = temporaryFolder.newFolder().toPath();
    containerizer
        .setBaseImageLayersCache(cacheDirectory)
        .setApplicationLayersCache(cacheDirectory)
        .setAllowInsecureRegistries(true)
        .setToolName("jib-integration-test")
        .setExecutorService(executorService)
        .addEventHandler(ProgressEvent.class, progressChecker.progressEventHandler);
    additionalTags.forEach(containerizer::withAdditionalTag);

    return containerBuilder.containerize(containerizer);
  }
}
