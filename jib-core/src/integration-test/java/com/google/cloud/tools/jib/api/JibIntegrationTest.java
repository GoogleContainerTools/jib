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
import com.google.cloud.tools.jib.registry.LocalRegistry;
import com.google.cloud.tools.jib.registry.ManifestPullerIntegrationTest;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Integration tests for {@link Jib}. */
public class JibIntegrationTest {

  @ClassRule
  public static final LocalRegistry localRegistry = new LocalRegistry(5000, "username", "password");

  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  /**
   * Pulls a built image and attempts to run it.
   *
   * @param imageReference the image reference of the built image
   * @return the container output
   * @throws IOException if an I/O exception occurs
   * @throws InterruptedException if the process was interrupted
   */
  private static String pullAndRunBuiltImage(String imageReference)
      throws IOException, InterruptedException {
    localRegistry.pull(imageReference);
    return new Command("docker", "run", "--rm", imageReference).run();
  }

  private static Containerizer getLocalRegistryContainerizer(ImageReference targetImageReference) {
    return Containerizer.to(
            RegistryImage.named(targetImageReference)
                .addCredentialRetriever(() -> Optional.of(Credential.from("username", "password"))))
        .setAllowInsecureRegistries(true);
  }

  @Before
  public void setUp() {
    System.setProperty("sendCredentialsOverHttp", "true");
  }

  @After
  public void tearDown() {
    System.clearProperty("sendCredentialsOverHttp");
  }

  @Test
  public void testBasic_helloWorld()
      throws InvalidImageReferenceException, InterruptedException, CacheDirectoryCreationException,
          IOException, RegistryException, ExecutionException {
    ImageReference targetImageReference =
        ImageReference.of("localhost:5000", "jib-core", "basic-helloworld");
    JibContainer jibContainer =
        Jib.from("busybox")
            .setEntrypoint("echo", "Hello World")
            .containerize(getLocalRegistryContainerizer(targetImageReference));

    Assert.assertEquals("Hello World\n", pullAndRunBuiltImage(targetImageReference.toString()));
    Assert.assertEquals(
        "Hello World\n",
        pullAndRunBuiltImage(
            targetImageReference.withQualifier(jibContainer.getDigest().toString()).toString()));
  }

  @Test
  public void testBasic_dockerDaemonBaseImage()
      throws IOException, InterruptedException, InvalidImageReferenceException, ExecutionException,
          RegistryException, CacheDirectoryCreationException {
    localRegistry.pull("busybox");
    ImageReference targetImageReference =
        ImageReference.of("localhost:5000", "jib-core", "basic-helloworld-dockerdaemon");
    JibContainer jibContainer =
        Jib.from("docker://busybox")
            .setEntrypoint("echo", "Hello World")
            .containerize(getLocalRegistryContainerizer(targetImageReference));

    Assert.assertEquals("Hello World\n", pullAndRunBuiltImage(targetImageReference.toString()));
    Assert.assertEquals(
        "Hello World\n",
        pullAndRunBuiltImage(
            targetImageReference.withQualifier(jibContainer.getDigest().toString()).toString()));
  }

  @Test
  public void testBasic_dockerDaemonBaseImageToDockerDaemon()
      throws IOException, InterruptedException, InvalidImageReferenceException, ExecutionException,
          RegistryException, CacheDirectoryCreationException {
    localRegistry.pull("busybox");
    ImageReference targetImageReference =
        ImageReference.of("localhost:5000", "jib-core", "basic-helloworld-dockerdaemon");
    Jib.from(DockerDaemonImage.named("busybox"))
        .setEntrypoint("echo", "Hello World")
        .containerize(Containerizer.to(DockerDaemonImage.named(targetImageReference)));

    String output = new Command("docker", "run", "--rm", targetImageReference.toString()).run();
    Assert.assertEquals("Hello World\n", output);
  }

  @Test
  public void testBasic_tarBaseImage_dockerSavedCommand()
      throws IOException, InterruptedException, InvalidImageReferenceException, ExecutionException,
          RegistryException, CacheDirectoryCreationException {
    localRegistry.pull("busybox");
    Path path = temporaryFolder.getRoot().toPath().resolve("docker-save");
    new Command("docker", "save", "busybox", "-o", path.toString()).run();

    ImageReference targetImageReference =
        ImageReference.of("localhost:5000", "jib-core", "basic-helloworld-dockersavedtar");
    JibContainer jibContainer =
        Jib.from("tar://" + path)
            .setEntrypoint("echo", "Hello World")
            .containerize(getLocalRegistryContainerizer(targetImageReference));

    Assert.assertEquals("Hello World\n", pullAndRunBuiltImage(targetImageReference.toString()));
    Assert.assertEquals(
        "Hello World\n",
        pullAndRunBuiltImage(
            targetImageReference.withQualifier(jibContainer.getDigest().toString()).toString()));
  }

  @Test
  public void testBasic_tarBaseImage_dockerSavedFile()
      throws IOException, InterruptedException, InvalidImageReferenceException, ExecutionException,
          RegistryException, CacheDirectoryCreationException, URISyntaxException {
    // tar saved with 'docker save busybox -o busybox.tar'
    Path path = Paths.get(Resources.getResource("core/busybox-docker.tar").toURI());

    ImageReference targetImageReference =
        ImageReference.of("localhost:5000", "jib-core", "basic-helloworld-dockersavedtar");
    JibContainer jibContainer =
        Jib.from(TarImage.at(path).named("ignored"))
            .setEntrypoint("echo", "Hello World")
            .containerize(getLocalRegistryContainerizer(targetImageReference));

    Assert.assertEquals("Hello World\n", pullAndRunBuiltImage(targetImageReference.toString()));
    Assert.assertEquals(
        "Hello World\n",
        pullAndRunBuiltImage(
            targetImageReference.withQualifier(jibContainer.getDigest().toString()).toString()));
  }

  @Test
  public void testBasic_tarBaseImage_jibImage()
      throws InvalidImageReferenceException, InterruptedException, ExecutionException,
          RegistryException, CacheDirectoryCreationException, IOException, URISyntaxException {
    ImageReference targetImageReference =
        ImageReference.of("localhost:5000", "jib-core", "jib-base-image");
    Path outputPath = temporaryFolder.getRoot().toPath().resolve("jib-image.tar");
    Jib.from("busybox")
        .addLayer(
            Collections.singletonList(Paths.get(Resources.getResource("core/hello").toURI())), "/")
        .containerize(
            Containerizer.to(TarImage.at(outputPath).named(targetImageReference))
                .setAllowInsecureRegistries(true));

    targetImageReference = ImageReference.of("localhost:5000", "jib-core", "jib-helloworld-jibtar");
    JibContainer jibContainer =
        Jib.from(TarImage.at(outputPath).named("ignored"))
            .setEntrypoint("cat", "/hello")
            .containerize(getLocalRegistryContainerizer(targetImageReference));

    Assert.assertEquals("Hello World\n", pullAndRunBuiltImage(targetImageReference.toString()));
    Assert.assertEquals(
        "Hello World\n",
        pullAndRunBuiltImage(
            targetImageReference.withQualifier(jibContainer.getDigest().toString()).toString()));
  }

  @Test
  public void testBasic_tarBaseImage_jibImageToDockerDaemon()
      throws InvalidImageReferenceException, InterruptedException, ExecutionException,
          RegistryException, CacheDirectoryCreationException, IOException, URISyntaxException {
    // tar saved with Jib.from("busybox").addLayer(...("core/hello")).containerize(TarImage.at...)
    Path path = Paths.get(Resources.getResource("core/busybox-jib.tar").toURI());

    ImageReference targetImageReference =
        ImageReference.of("localhost:5000", "jib-core", "basic-helloworld-jibtar");
    JibContainer jibContainer =
        Jib.from(TarImage.at(path).named("ignored"))
            .setEntrypoint("cat", "/hello")
            .containerize(getLocalRegistryContainerizer(targetImageReference));

    Assert.assertEquals("Hello World\n", pullAndRunBuiltImage(targetImageReference.toString()));
    Assert.assertEquals(
        "Hello World\n",
        pullAndRunBuiltImage(
            targetImageReference.withQualifier(jibContainer.getDigest().toString()).toString()));
  }

  @Test
  public void testScratch()
      throws IOException, InterruptedException, ExecutionException, RegistryException,
          CacheDirectoryCreationException {
    ImageReference targetImageReference =
        ImageReference.of("localhost:5000", "jib-core", "basic-scratch");
    Jib.fromScratch().containerize(getLocalRegistryContainerizer(targetImageReference));

    // Check that resulting image has no layers
    localRegistry.pull(targetImageReference.toString());
    String inspectOutput = new Command("docker", "inspect", targetImageReference.toString()).run();
    Assert.assertFalse(
        "docker inspect output contained layers: " + inspectOutput,
        inspectOutput.contains("\"Layers\": ["));
  }

  @Test
  public void testOffline()
      throws IOException, InterruptedException, InvalidImageReferenceException, ExecutionException,
          RegistryException, CacheDirectoryCreationException {
    LocalRegistry tempRegistry = new LocalRegistry(5001);
    tempRegistry.start();
    tempRegistry.pullAndPushToLocal("busybox", "busybox");
    Path cacheDirectory = temporaryFolder.getRoot().toPath();

    ImageReference targetImageReferenceOnline =
        ImageReference.of("localhost:5001", "jib-core", "basic-online");
    ImageReference targetImageReferenceOffline =
        ImageReference.of("localhost:5001", "jib-core", "basic-offline");

    JibContainerBuilder jibContainerBuilder =
        Jib.from("localhost:5001/busybox").setEntrypoint("echo", "Hello World");

    // Should fail since Jib can't build to registry offline
    try {
      jibContainerBuilder.containerize(
          Containerizer.to(RegistryImage.named(targetImageReferenceOffline)).setOfflineMode(true));
      Assert.fail();
    } catch (IllegalStateException ex) {
      Assert.assertEquals("Cannot build to a container registry in offline mode", ex.getMessage());
    }

    // Should fail since Jib hasn't cached the base image yet
    try {
      jibContainerBuilder.containerize(
          Containerizer.to(DockerDaemonImage.named(targetImageReferenceOffline))
              .setBaseImageLayersCache(cacheDirectory)
              .setOfflineMode(true));
      Assert.fail();
    } catch (ExecutionException ex) {
      Assert.assertEquals(
          "Cannot run Jib in offline mode; localhost:5001/busybox not found in local Jib cache",
          ex.getCause().getMessage());
    }

    // Run online to cache the base image
    jibContainerBuilder.containerize(
        Containerizer.to(DockerDaemonImage.named(targetImageReferenceOnline))
            .setBaseImageLayersCache(cacheDirectory)
            .setAllowInsecureRegistries(true));

    // Run again in offline mode, should succeed this time
    tempRegistry.stop();
    jibContainerBuilder.containerize(
        Containerizer.to(DockerDaemonImage.named(targetImageReferenceOffline))
            .setBaseImageLayersCache(cacheDirectory)
            .setOfflineMode(true));

    // Verify output
    Assert.assertEquals(
        "Hello World\n",
        new Command("docker", "run", "--rm", targetImageReferenceOffline.toString()).run());
  }

  /** Ensure that a provided executor is not disposed. */
  @Test
  public void testProvidedExecutorNotDisposed()
      throws InvalidImageReferenceException, InterruptedException, CacheDirectoryCreationException,
          IOException, RegistryException, ExecutionException {
    ImageReference targetImageReference =
        ImageReference.of("localhost:5000", "jib-core", "basic-helloworld");
    Containerizer containerizer = getLocalRegistryContainerizer(targetImageReference);

    ExecutorService executorService = Executors.newCachedThreadPool();
    try {
      containerizer.setExecutorService(executorService);
      Jib.from("busybox").setEntrypoint("echo", "Hello World").containerize(containerizer);
      Assert.assertFalse(executorService.isShutdown());
    } finally {
      executorService.shutdown();
    }
  }

  @Test
  public void testManifestListReferenceByShaDoesNotFail()
      throws InvalidImageReferenceException, IOException, InterruptedException, ExecutionException,
          RegistryException, CacheDirectoryCreationException {
    ImageReference sourceImageReferenceAsManifestList =
        ImageReference.parse("openjdk@" + ManifestPullerIntegrationTest.KNOWN_MANIFEST_LIST_SHA);
    Containerizer containerizer =
        Containerizer.to(
            TarImage.at(temporaryFolder.newFolder("goose").toPath().resolve("moose"))
                .named("whatever"));

    Jib.from(sourceImageReferenceAsManifestList).containerize(containerizer);
    // pass, no exceptions thrown
  }
}
