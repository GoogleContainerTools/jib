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

import static com.google.common.truth.Truth.assertThat;

import com.google.cloud.tools.jib.Command;
import com.google.cloud.tools.jib.api.buildplan.Platform;
import com.google.cloud.tools.jib.blob.Blobs;
import com.google.cloud.tools.jib.event.EventHandlers;
import com.google.cloud.tools.jib.http.FailoverHttpClient;
import com.google.cloud.tools.jib.image.json.V22ManifestListTemplate;
import com.google.cloud.tools.jib.image.json.V22ManifestListTemplate.ManifestDescriptorTemplate;
import com.google.cloud.tools.jib.image.json.V22ManifestTemplate;
import com.google.cloud.tools.jib.registry.LocalRegistry;
import com.google.cloud.tools.jib.registry.ManifestPullerIntegrationTest;
import com.google.cloud.tools.jib.registry.RegistryClient;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Integration tests for {@link Jib}. */
public class JibIntegrationTest {

  /** A known oci index sha for gcr.io/distroless/base. */
  public static final String KNOWN_OCI_INDEX_SHA =
      "sha256:2c50b819aa3bfaf6ae72e47682f6c5abc0f647cf3f4224a4a9be97dd30433909";

  @ClassRule public static final LocalRegistry localRegistry = new LocalRegistry(5000);

  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  private final String dockerHost =
      System.getenv("DOCKER_IP") != null ? System.getenv("DOCKER_IP") : "localhost";

  private final RegistryClient registryClient =
      RegistryClient.factory(
              EventHandlers.NONE,
              dockerHost + ":5000",
              "jib-scratch",
              new FailoverHttpClient(true, true, ignored -> {}))
          .newRegistryClient();

  private final RegistryClient distrolessRegistryClient =
      RegistryClient.factory(
              EventHandlers.NONE,
              dockerHost + ":5000",
              "jib-distroless",
              new FailoverHttpClient(true, true, ignored -> {}))
          .newRegistryClient();

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

  @BeforeClass
  public static void setUpClass() throws IOException, InterruptedException {
    localRegistry.pullAndPushToLocal("busybox", "busybox");
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
    String toImage = dockerHost + ":5000/basic-helloworld";
    JibContainer jibContainer =
        Jib.from(dockerHost + ":5000/busybox")
            .setEntrypoint("echo", "Hello World")
            .containerize(
                Containerizer.to(RegistryImage.named(toImage)).setAllowInsecureRegistries(true));

    Assert.assertEquals("Hello World\n", pullAndRunBuiltImage(toImage));
    Assert.assertEquals(
        "Hello World\n", pullAndRunBuiltImage(toImage + "@" + jibContainer.getDigest()));
  }

  @Test
  public void testBasic_dockerDaemonBaseImage()
      throws IOException, InterruptedException, InvalidImageReferenceException, ExecutionException,
          RegistryException, CacheDirectoryCreationException {
    String toImage = dockerHost + ":5000/basic-dockerdaemon";
    JibContainer jibContainer =
        Jib.from("docker://" + dockerHost + ":5000/busybox")
            .setEntrypoint("echo", "Hello World")
            .containerize(
                Containerizer.to(RegistryImage.named(toImage)).setAllowInsecureRegistries(true));

    Assert.assertEquals("Hello World\n", pullAndRunBuiltImage(toImage));
    Assert.assertEquals(
        "Hello World\n", pullAndRunBuiltImage(toImage + "@" + jibContainer.getDigest()));
  }

  @Test
  public void testBasic_dockerDaemonBaseImageToDockerDaemon()
      throws IOException, InterruptedException, InvalidImageReferenceException, ExecutionException,
          RegistryException, CacheDirectoryCreationException {
    Jib.from(DockerDaemonImage.named(dockerHost + ":5000/busybox"))
        .setEntrypoint("echo", "Hello World")
        .containerize(
            Containerizer.to(DockerDaemonImage.named(dockerHost + ":5000/docker-to-docker")));

    String output =
        new Command("docker", "run", "--rm", dockerHost + ":5000/docker-to-docker").run();
    Assert.assertEquals("Hello World\n", output);
  }

  @Test
  public void testBasic_tarBaseImage_dockerSavedCommand()
      throws IOException, InterruptedException, InvalidImageReferenceException, ExecutionException,
          RegistryException, CacheDirectoryCreationException {
    Path path = temporaryFolder.getRoot().toPath().resolve("docker-save.tar");
    new Command("docker", "save", dockerHost + ":5000/busybox", "-o=" + path).run();

    String toImage = dockerHost + ":5000/basic-dockersavedcommand";
    JibContainer jibContainer =
        Jib.from("tar://" + path)
            .setEntrypoint("echo", "Hello World")
            .containerize(
                Containerizer.to(RegistryImage.named(toImage)).setAllowInsecureRegistries(true));

    Assert.assertEquals("Hello World\n", pullAndRunBuiltImage(toImage));
    Assert.assertEquals(
        "Hello World\n", pullAndRunBuiltImage(toImage + "@" + jibContainer.getDigest()));
  }

  @Test
  public void testBasic_tarBaseImage_dockerSavedFile()
      throws IOException, InterruptedException, InvalidImageReferenceException, ExecutionException,
          RegistryException, CacheDirectoryCreationException, URISyntaxException {
    // tar saved with 'docker save busybox -o busybox.tar'
    Path path = Paths.get(Resources.getResource("core/busybox-docker.tar").toURI());

    String toImage = dockerHost + ":5000/basic-dockersavedfile";
    JibContainer jibContainer =
        Jib.from(TarImage.at(path).named("ignored"))
            .setEntrypoint("echo", "Hello World")
            .containerize(
                Containerizer.to(RegistryImage.named(toImage)).setAllowInsecureRegistries(true));

    Assert.assertEquals("Hello World\n", pullAndRunBuiltImage(toImage));
    Assert.assertEquals(
        "Hello World\n", pullAndRunBuiltImage(toImage + "@" + jibContainer.getDigest()));
  }

  @Test
  public void testBasic_tarBaseImage_jibImage()
      throws InvalidImageReferenceException, InterruptedException, ExecutionException,
          RegistryException, CacheDirectoryCreationException, IOException, URISyntaxException {
    Path outputPath = temporaryFolder.getRoot().toPath().resolve("jib-image.tar");
    Jib.from(dockerHost + ":5000/busybox")
        .addLayer(
            Collections.singletonList(Paths.get(Resources.getResource("core/hello").toURI())), "/")
        .containerize(
            Containerizer.to(TarImage.at(outputPath).named("ignored"))
                .setAllowInsecureRegistries(true));

    String toImage = dockerHost + ":5000/basic-jibtar";
    JibContainer jibContainer =
        Jib.from(TarImage.at(outputPath).named("ignored"))
            .setEntrypoint("cat", "/hello")
            .containerize(
                Containerizer.to(RegistryImage.named(toImage)).setAllowInsecureRegistries(true));

    Assert.assertEquals("Hello World\n", pullAndRunBuiltImage(toImage));
    Assert.assertEquals(
        "Hello World\n", pullAndRunBuiltImage(toImage + "@" + jibContainer.getDigest()));
  }

  @Test
  public void testBasic_tarBaseImage_jibImageToDockerDaemon()
      throws InvalidImageReferenceException, InterruptedException, ExecutionException,
          RegistryException, CacheDirectoryCreationException, IOException, URISyntaxException {
    // tar saved with Jib.from("busybox").addLayer(...("core/hello")).containerize(TarImage.at...)
    Path path = Paths.get(Resources.getResource("core/busybox-jib.tar").toURI());

    String toImage = dockerHost + ":5000/basic-jibtar-to-docker";
    JibContainer jibContainer =
        Jib.from(TarImage.at(path).named("ignored"))
            .setEntrypoint("cat", "/hello")
            .containerize(
                Containerizer.to(RegistryImage.named(toImage)).setAllowInsecureRegistries(true));

    Assert.assertEquals("Hello World\n", pullAndRunBuiltImage(toImage));
    Assert.assertEquals(
        "Hello World\n", pullAndRunBuiltImage(toImage + "@" + jibContainer.getDigest()));
  }

  @Test
  public void testScratch_defaultPlatform()
      throws IOException, InterruptedException, ExecutionException, RegistryException,
          CacheDirectoryCreationException, InvalidImageReferenceException {
    Jib.fromScratch()
        .containerize(
            Containerizer.to(RegistryImage.named(dockerHost + ":5000/jib-scratch:default-platform"))
                .setAllowInsecureRegistries(true));

    V22ManifestTemplate manifestTemplate =
        registryClient.pullManifest("default-platform", V22ManifestTemplate.class).getManifest();
    String containerConfig =
        Blobs.writeToString(
            registryClient.pullBlob(
                manifestTemplate.getContainerConfiguration().getDigest(),
                ignored -> {},
                ignored -> {}));

    Assert.assertTrue(manifestTemplate.getLayers().isEmpty());
    Assert.assertTrue(containerConfig.contains("\"architecture\":\"amd64\""));
    Assert.assertTrue(containerConfig.contains("\"os\":\"linux\""));
  }

  @Test
  public void testScratch_singlePlatform()
      throws IOException, InterruptedException, ExecutionException, RegistryException,
          CacheDirectoryCreationException, InvalidImageReferenceException {
    Jib.fromScratch()
        .setPlatforms(ImmutableSet.of(new Platform("arm64", "windows")))
        .containerize(
            Containerizer.to(RegistryImage.named(dockerHost + ":5000/jib-scratch:single-platform"))
                .setAllowInsecureRegistries(true));

    V22ManifestTemplate manifestTemplate =
        registryClient.pullManifest("single-platform", V22ManifestTemplate.class).getManifest();
    String containerConfig =
        Blobs.writeToString(
            registryClient.pullBlob(
                manifestTemplate.getContainerConfiguration().getDigest(),
                ignored -> {},
                ignored -> {}));

    Assert.assertTrue(manifestTemplate.getLayers().isEmpty());
    Assert.assertTrue(containerConfig.contains("\"architecture\":\"arm64\""));
    Assert.assertTrue(containerConfig.contains("\"os\":\"windows\""));
  }

  @Test
  public void testScratch_multiPlatform()
      throws IOException, InterruptedException, ExecutionException, RegistryException,
          CacheDirectoryCreationException, InvalidImageReferenceException {
    Jib.fromScratch()
        .setPlatforms(
            ImmutableSet.of(new Platform("arm64", "windows"), new Platform("amd32", "windows")))
        .containerize(
            Containerizer.to(RegistryImage.named(dockerHost + ":5000/jib-scratch:multi-platform"))
                .setAllowInsecureRegistries(true));

    V22ManifestListTemplate manifestList =
        (V22ManifestListTemplate) registryClient.pullManifest("multi-platform").getManifest();
    Assert.assertEquals(2, manifestList.getManifests().size());
    ManifestDescriptorTemplate.Platform platform1 =
        manifestList.getManifests().get(0).getPlatform();
    ManifestDescriptorTemplate.Platform platform2 =
        manifestList.getManifests().get(1).getPlatform();

    Assert.assertEquals("arm64", platform1.getArchitecture());
    Assert.assertEquals("windows", platform1.getOs());
    Assert.assertEquals("amd32", platform2.getArchitecture());
    Assert.assertEquals("windows", platform2.getOs());
  }

  @Test
  public void testBasic_jibImageToDockerDaemon()
      throws IOException, InterruptedException, InvalidImageReferenceException, ExecutionException,
          RegistryException, CacheDirectoryCreationException {
    Jib.from(DockerDaemonImage.named(dockerHost + ":5000/busybox"))
        .setEntrypoint("echo", "Hello World")
        .containerize(
            Containerizer.to(DockerDaemonImage.named(dockerHost + ":5000/docker-to-docker")));

    String output =
        new Command("docker", "run", "--rm", dockerHost + ":5000/docker-to-docker").run();
    Assert.assertEquals("Hello World\n", output);
  }

  @Test
  public void testBasicMultiPlatform_toDockerDaemon()
      throws IOException, InterruptedException, ExecutionException, RegistryException,
          CacheDirectoryCreationException, InvalidImageReferenceException {
    Jib.from(
            RegistryImage.named(
                "busybox@sha256:4f47c01fa91355af2865ac10fef5bf6ec9c7f42ad2321377c21e844427972977"))
        .setPlatforms(
            ImmutableSet.of(new Platform("arm64", "linux"), new Platform("amd64", "linux")))
        .setEntrypoint("echo", "Hello World")
        .containerize(
            Containerizer.to(
                    DockerDaemonImage.named(dockerHost + ":5000/docker-daemon-multi-platform"))
                .setAllowInsecureRegistries(true));

    String output =
        new Command("docker", "run", "--rm", dockerHost + ":5000/docker-daemon-multi-platform")
            .run();
    Assert.assertEquals("Hello World\n", output);
  }

  @Test
  public void testBasicMultiPlatform_toDockerDaemon_pickFirstPlatformWhenNoMatchingImage()
      throws IOException, InterruptedException, InvalidImageReferenceException,
          CacheDirectoryCreationException, ExecutionException, RegistryException {
    Jib.from(
            RegistryImage.named(
                "busybox@sha256:4f47c01fa91355af2865ac10fef5bf6ec9c7f42ad2321377c21e844427972977"))
        .setPlatforms(ImmutableSet.of(new Platform("s390x", "linux"), new Platform("arm", "linux")))
        .setEntrypoint("echo", "Hello World")
        .containerize(
            Containerizer.to(
                    DockerDaemonImage.named(dockerHost + ":5000/docker-daemon-multi-platform"))
                .setAllowInsecureRegistries(true));
    String os =
        new Command(
                "docker",
                "inspect",
                dockerHost + ":5000/docker-daemon-multi-platform",
                "--format",
                "{{.Os}}")
            .run()
            .replace("\n", "");
    String architecture =
        new Command(
                "docker",
                "inspect",
                dockerHost + ":5000/docker-daemon-multi-platform",
                "--format",
                "{{.Architecture}}")
            .run()
            .replace("\n", "");
    assertThat(os).isEqualTo("linux");
    assertThat(architecture).isEqualTo("s390x");
  }

  @Test
  public void testDistroless_ociManifest()
      throws IOException, InterruptedException, ExecutionException, RegistryException,
          CacheDirectoryCreationException, InvalidImageReferenceException {
    Jib.from("gcr.io/distroless/base@" + KNOWN_OCI_INDEX_SHA)
        .setPlatforms(
            ImmutableSet.of(new Platform("arm64", "linux"), new Platform("amd64", "linux")))
        .containerize(
            Containerizer.to(
                    RegistryImage.named(dockerHost + ":5000/jib-distroless:multi-platform"))
                .setAllowInsecureRegistries(true));

    V22ManifestListTemplate manifestList =
        (V22ManifestListTemplate)
            distrolessRegistryClient.pullManifest("multi-platform").getManifest();
    Assert.assertEquals(2, manifestList.getManifests().size());
    ManifestDescriptorTemplate.Platform platform1 =
        manifestList.getManifests().get(0).getPlatform();
    ManifestDescriptorTemplate.Platform platform2 =
        manifestList.getManifests().get(1).getPlatform();

    Assert.assertEquals("arm64", platform1.getArchitecture());
    Assert.assertEquals("linux", platform1.getOs());
    Assert.assertEquals("amd64", platform2.getArchitecture());
    Assert.assertEquals("linux", platform2.getOs());
  }

  @Test
  public void testOffline()
      throws IOException, InterruptedException, InvalidImageReferenceException, ExecutionException,
          RegistryException, CacheDirectoryCreationException {
    Path cacheDirectory = temporaryFolder.getRoot().toPath();

    JibContainerBuilder jibContainerBuilder =
        Jib.from(dockerHost + ":5000/busybox").setEntrypoint("echo", "Hello World");

    // Should fail since Jib can't build to registry offline
    try {
      jibContainerBuilder.containerize(
          Containerizer.to(RegistryImage.named("ignored")).setOfflineMode(true));
      Assert.fail();
    } catch (IllegalStateException ex) {
      Assert.assertEquals("Cannot build to a container registry in offline mode", ex.getMessage());
    }

    // Should fail since Jib hasn't cached the base image yet
    try {
      jibContainerBuilder.containerize(
          Containerizer.to(DockerDaemonImage.named("ignored"))
              .setBaseImageLayersCache(cacheDirectory)
              .setOfflineMode(true));
      Assert.fail();
    } catch (ExecutionException ex) {
      Assert.assertEquals(
          "Cannot run Jib in offline mode; "
              + dockerHost
              + ":5000/busybox not found in local Jib cache",
          ex.getCause().getMessage());
    }

    // Run online to cache the base image
    jibContainerBuilder.containerize(
        Containerizer.to(DockerDaemonImage.named("ignored"))
            .setBaseImageLayersCache(cacheDirectory)
            .setAllowInsecureRegistries(true));

    // Run again in offline mode, should succeed this time
    jibContainerBuilder.containerize(
        Containerizer.to(DockerDaemonImage.named(dockerHost + ":5000/offline"))
            .setBaseImageLayersCache(cacheDirectory)
            .setOfflineMode(true));

    // Verify output
    Assert.assertEquals(
        "Hello World\n", new Command("docker", "run", "--rm", dockerHost + ":5000/offline").run());
  }

  /** Ensure that a provided executor is not disposed. */
  @Test
  public void testProvidedExecutorNotDisposed()
      throws InvalidImageReferenceException, InterruptedException, CacheDirectoryCreationException,
          IOException, RegistryException, ExecutionException {
    ExecutorService executorService = Executors.newCachedThreadPool();
    try {
      Jib.fromScratch()
          .containerize(
              Containerizer.to(RegistryImage.named(dockerHost + ":5000/foo"))
                  .setExecutorService(executorService)
                  .setAllowInsecureRegistries(true));
      Assert.assertFalse(executorService.isShutdown());
    } finally {
      executorService.shutdown();
    }
  }

  @Test
  public void testManifestListReferenceByShaDoesNotFail()
      throws InvalidImageReferenceException, IOException, InterruptedException, ExecutionException,
          RegistryException, CacheDirectoryCreationException {
    Containerizer containerizer =
        Containerizer.to(TarImage.at(temporaryFolder.newFile("goose").toPath()).named("whatever"));

    Jib.from("gcr.io/distroless/base@" + ManifestPullerIntegrationTest.KNOWN_MANIFEST_LIST_SHA)
        .containerize(containerizer);
    // pass, no exceptions thrown
  }
}
