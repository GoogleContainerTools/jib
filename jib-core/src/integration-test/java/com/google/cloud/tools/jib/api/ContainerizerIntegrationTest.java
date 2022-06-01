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
import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.buildplan.FileEntriesLayer;
import com.google.cloud.tools.jib.event.events.ProgressEvent;
import com.google.cloud.tools.jib.event.progress.ProgressEventHandler;
import com.google.cloud.tools.jib.global.JibSystemProperties;
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
import java.util.stream.Stream;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.RestoreSystemProperties;
import org.junit.rules.TemporaryFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// TODO: now it looks like we can move everything here into JibIntegrationTest.
/** Integration tests for {@link Containerizer}. */
public class ContainerizerIntegrationTest {

  @Rule public final RestoreSystemProperties systemPropertyRestorer = new RestoreSystemProperties();

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

  private static final Logger logger = LoggerFactory.getLogger(ContainerizerIntegrationTest.class);
  private static final String DISTROLESS_DIGEST =
      "sha256:f488c213f278bc5f9ffe3ddf30c5dbb2303a15a74146b738d12453088e662880";
  private static final double DOUBLE_ERROR_MARGIN = 1e-10;

  public static ImmutableList<FileEntriesLayer> fakeLayerConfigurations;

  @BeforeClass
  public static void setUp() throws URISyntaxException, IOException {
    fakeLayerConfigurations =
        ImmutableList.of(
            makeLayerConfiguration("core/application/dependencies", "/app/libs/"),
            makeLayerConfiguration("core/application/resources", "/app/resources/"),
            makeLayerConfiguration("core/application/classes", "/app/classes/"));
  }

  /**
   * Lists the files in the {@code resourcePath} resources directory and builds a {@link
   * FileEntriesLayer} from those files.
   */
  private static FileEntriesLayer makeLayerConfiguration(
      String resourcePath, String pathInContainer) throws URISyntaxException, IOException {
    try (Stream<Path> fileStream =
        Files.list(Paths.get(Resources.getResource(resourcePath).toURI()))) {
      FileEntriesLayer.Builder layerConfigurationBuilder = FileEntriesLayer.builder();
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
    MatcherAssert.assertThat(
        dockerContainerConfig,
        CoreMatchers.containsString(
            "            \"ExposedPorts\": {\n"
                + "                \"1000/tcp\": {},\n"
                + "                \"2000/tcp\": {},\n"
                + "                \"2001/tcp\": {},\n"
                + "                \"2002/tcp\": {},\n"
                + "                \"3000/udp\": {}"));
    MatcherAssert.assertThat(
        dockerContainerConfig,
        CoreMatchers.containsString(
            "            \"Labels\": {\n"
                + "                \"key1\": \"value1\",\n"
                + "                \"key2\": \"value2\"\n"
                + "            }"));
    String dockerConfigEnv =
        new Command("docker", "inspect", "-f", "{{.Config.Env}}", imageReference).run();
    MatcherAssert.assertThat(dockerConfigEnv, CoreMatchers.containsString("env1=envvalue1"));
    MatcherAssert.assertThat(dockerConfigEnv, CoreMatchers.containsString("env2=envvalue2"));
    String history = new Command("docker", "history", imageReference).run();
    MatcherAssert.assertThat(history, CoreMatchers.containsString("jib-integration-test"));
    MatcherAssert.assertThat(history, CoreMatchers.containsString("bazel build ..."));
  }

  private static void assertLayerSize(int expected, String imageReference)
      throws IOException, InterruptedException {
    Command command =
        new Command("docker", "inspect", "-f", "{{join .RootFS.Layers \",\"}}", imageReference);
    String layers = command.run().trim();
    Assert.assertEquals(expected, Splitter.on(",").splitToList(layers).size());
  }

  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  private ProgressChecker progressChecker = new ProgressChecker();

  @Test
  public void testSteps_forBuildToDockerRegistry()
      throws IOException, InterruptedException, ExecutionException, RegistryException,
          CacheDirectoryCreationException, InvalidImageReferenceException {
    System.setProperty("jib.alwaysCacheBaseImage", "true");
    String imageReference = "localhost:5001/testimage:testtag";
    Path cacheDirectory = temporaryFolder.newFolder().toPath();
    Containerizer containerizer =
        Containerizer.to(RegistryImage.named(imageReference))
            .setBaseImageLayersCache(cacheDirectory)
            .setApplicationLayersCache(cacheDirectory);

    long lastTime = System.nanoTime();
    JibContainer image1 =
        buildImage(
            ImageReference.of("gcr.io", "distroless/java", DISTROLESS_DIGEST),
            containerizer,
            Collections.emptyList());

    progressChecker.checkCompletion();
    progressChecker = new ProgressChecker(); // to reset

    logger.info("Initial build time: " + ((System.nanoTime() - lastTime) / 1_000_000));

    lastTime = System.nanoTime();
    JibContainer image2 =
        buildImage(
            ImageReference.of("gcr.io", "distroless/java", DISTROLESS_DIGEST),
            containerizer,
            Collections.emptyList());

    logger.info("Secondary build time: " + ((System.nanoTime() - lastTime) / 1_000_000));

    Assert.assertEquals(image1, image2);

    localRegistry.pull(imageReference);
    assertDockerInspect(imageReference);
    assertLayerSize(7, imageReference);
    Assert.assertEquals(
        "Hello, world. An argument.\n", new Command("docker", "run", "--rm", imageReference).run());

    String imageReferenceByDigest = "localhost:5001/testimage@" + image1.getDigest();
    localRegistry.pull(imageReferenceByDigest);
    assertDockerInspect(imageReferenceByDigest);
    Assert.assertEquals(
        "Hello, world. An argument.\n",
        new Command("docker", "run", "--rm", imageReferenceByDigest, "--network", "host").run());
  }

  @Test
  public void testSteps_forBuildToDockerRegistry_multipleTags()
      throws IOException, InterruptedException, ExecutionException, RegistryException,
          CacheDirectoryCreationException, InvalidImageReferenceException {
    buildImage(
        ImageReference.of("gcr.io", "distroless/java", DISTROLESS_DIGEST),
        Containerizer.to(RegistryImage.named("localhost:5001/testimage:testtag")),
        Arrays.asList("testtag2", "testtag3"));

    String imageReference = "localhost:5001/testimage:testtag";
    localRegistry.pull(imageReference);
    assertDockerInspect(imageReference);
    Assert.assertEquals(
        "Hello, world. An argument.\n", new Command("docker", "run", "--rm", imageReference).run());

    String imageReference2 = "localhost:5001/testimage:testtag2";
    localRegistry.pull(imageReference2);
    assertDockerInspect(imageReference2);
    Assert.assertEquals(
        "Hello, world. An argument.\n",
        new Command("docker", "run", "--rm", imageReference2, "--network", "host").run());

    String imageReference3 = "localhost:5001/testimage:testtag3";
    localRegistry.pull(imageReference3);
    assertDockerInspect(imageReference3);
    Assert.assertEquals(
        "Hello, world. An argument.\n",
        new Command("docker", "run", "--rm", imageReference3, "--network", "host").run());
  }

  @Test
  public void testSteps_forBuildToDockerRegistry_skipExistingDigest()
      throws IOException, InterruptedException, ExecutionException, RegistryException,
          CacheDirectoryCreationException, InvalidImageReferenceException {
    System.setProperty(JibSystemProperties.SKIP_EXISTING_IMAGES, "true");

    JibContainer image1 =
        buildImage(
            ImageReference.scratch(),
            Containerizer.to(RegistryImage.named("localhost:5000/testimagerepo:testtag")),
            Collections.singletonList("testtag2"));

    // Test that the initial image with the original tag has been pushed.
    localRegistry.pull("localhost:5000/testimagerepo:testtag");
    // Test that any additional tags have also been pushed with the original image.
    localRegistry.pull("localhost:5000/testimagerepo:testtag2");

    // Push the same image with a different tag, with SKIP_EXISTING_IMAGES enabled.
    JibContainer image2 =
        buildImage(
            ImageReference.scratch(),
            Containerizer.to(RegistryImage.named("localhost:5000/testimagerepo:new_testtag")),
            Collections.emptyList());

    // Test that the pull request throws an exception, indicating that the new tag was not pushed.
    try {
      localRegistry.pull("localhost:5000/testimagerepo:new_testtag");
      Assert.fail(
          "jib.skipExistingImages was enabled and digest was already pushed, "
              + "hence new_testtag shouldn't have been pushed.");
    } catch (RuntimeException ex) {
      MatcherAssert.assertThat(
          ex.getMessage(),
          CoreMatchers.containsString(
              "manifest for localhost:5000/testimagerepo:new_testtag not found"));
    }

    // Test that both images have the same properties.
    Assert.assertEquals(image1.getDigest(), image2.getDigest());
    Assert.assertEquals(image1.getImageId(), image2.getImageId());
  }

  @Test
  public void testBuildToDockerRegistry_dockerHubBaseImage()
      throws InvalidImageReferenceException, IOException, InterruptedException, ExecutionException,
          RegistryException, CacheDirectoryCreationException {
    buildImage(
        ImageReference.parse("openjdk:8-jre-slim"),
        Containerizer.to(RegistryImage.named("localhost:5000/testimage:testtag")),
        Collections.emptyList());

    String imageReference = "localhost:5000/testimage:testtag";
    new Command("docker", "pull", imageReference).run();
    Assert.assertEquals(
        "Hello, world. An argument.\n", new Command("docker", "run", "--rm", imageReference).run());
  }

  @Test
  public void testBuildToDockerDaemon_multipleTags()
      throws IOException, InterruptedException, ExecutionException, RegistryException,
          CacheDirectoryCreationException, InvalidImageReferenceException {
    buildImage(
        ImageReference.of("gcr.io", "distroless/java", DISTROLESS_DIGEST),
        Containerizer.to(DockerDaemonImage.named("testdocker")),
        Arrays.asList("testtag2", "testtag3"));

    progressChecker.checkCompletion();

    assertLayerSize(7, "testdocker");
    assertDockerInspect("testdocker");
    assertDockerInspect("testdocker:testtag2");
    assertDockerInspect("testdocker:testtag3");
    Assert.assertEquals(
        "Hello, world. An argument.\n", new Command("docker", "run", "--rm", "testdocker").run());
    Assert.assertEquals(
        "Hello, world. An argument.\n",
        new Command("docker", "run", "--rm", "testdocker:testtag2", "--network", "host").run());
    Assert.assertEquals(
        "Hello, world. An argument.\n",
        new Command("docker", "run", "--rm", "testdocker:testtag3", "--network", "host").run());
  }

  @Test
  public void testBuildTarball()
      throws IOException, InterruptedException, ExecutionException, RegistryException,
          CacheDirectoryCreationException, InvalidImageReferenceException {
    Path outputPath = temporaryFolder.newFolder().toPath().resolve("test.tar");
    buildImage(
        ImageReference.of("gcr.io", "distroless/java", DISTROLESS_DIGEST),
        Containerizer.to(TarImage.at(outputPath).named("testtar")),
        Collections.emptyList());

    progressChecker.checkCompletion();

    new Command("docker", "load", "--input", outputPath.toString()).run();
    assertLayerSize(7, "testtar");
    Assert.assertEquals(
        "Hello, world. An argument.\n", new Command("docker", "run", "--rm", "testtar").run());
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
            .setFileEntriesLayers(fakeLayerConfigurations);

    containerizer
        .setAllowInsecureRegistries(true)
        .setToolName("jib-integration-test")
        .addEventHandler(ProgressEvent.class, progressChecker.progressEventHandler);
    additionalTags.forEach(containerizer::withAdditionalTag);

    return containerBuilder.containerize(containerizer);
  }
}
