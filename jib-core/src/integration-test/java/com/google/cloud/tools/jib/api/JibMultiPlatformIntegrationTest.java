package com.google.cloud.tools.jib.api;

import static com.google.common.truth.Truth.assertThat;

import com.google.cloud.tools.jib.Command;
import com.google.cloud.tools.jib.api.buildplan.Platform;
import com.google.cloud.tools.jib.registry.LocalRegistry;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import org.junit.After;
import org.junit.ClassRule;
import org.junit.Test;

public class JibMultiPlatformIntegrationTest {

  @ClassRule public static final LocalRegistry localRegistry = new LocalRegistry(5000);

  private final String dockerHost =
      System.getenv("DOCKER_IP") != null ? System.getenv("DOCKER_IP") : "localhost";
  private String imageToDelete;

  @After
  public void tearDown() throws IOException, InterruptedException {
    System.clearProperty("sendCredentialsOverHttp");
    if (imageToDelete != null) {
      new Command("docker", "rmi", imageToDelete).run();
    }
  }

  @Test
  public void testBasic_jibImageToDockerDaemon_arm64()
      throws IOException, InterruptedException, InvalidImageReferenceException, ExecutionException,
          RegistryException, CacheDirectoryCreationException {
    System.out.println("testBasic_jibImageToDockerDaemon_arm64");

    // Use arm64v8/busybox as base image.
    String toImage = dockerHost + ":5000/docker-daemon-mismatched-arch";
    Jib.from(
            RegistryImage.named(
                "busybox@sha256:eb427d855f82782c110b48b9a398556c629ce4951ae252c6f6751a136e194668"))
        .containerize(Containerizer.to(DockerDaemonImage.named(toImage)));
    String os =
        new Command("docker", "inspect", toImage, "--format", "{{.Os}}").run().replace("\n", "");
    String architecture =
        new Command("docker", "inspect", toImage, "--format", "{{.Architecture}}")
            .run()
            .replace("\n", "");
    assertThat(os).isEqualTo("linux");
    assertThat(architecture).isEqualTo("arm64");
    imageToDelete = toImage;
  }

  @Test
  public void testBasicMultiPlatform_toDockerDaemon_pickFirstPlatformWhenNoMatchingImage()
      throws IOException, InterruptedException, InvalidImageReferenceException,
          CacheDirectoryCreationException, ExecutionException, RegistryException {
    System.out.println(
        "testBasicMultiPlatform_toDockerDaemon_pickFirstPlatformWhenNoMatchingImage()");

    String toImage = dockerHost + ":5000/docker-daemon-multi-plat-mismatched-configs";
    Jib.from(
            RegistryImage.named(
                "busybox@sha256:4f47c01fa91355af2865ac10fef5bf6ec9c7f42ad2321377c21e844427972977"))
        .setPlatforms(ImmutableSet.of(new Platform("s390x", "linux"), new Platform("arm", "linux")))
        .containerize(
            Containerizer.to(DockerDaemonImage.named(toImage)).setAllowInsecureRegistries(true));
    String os =
        new Command("docker", "inspect", toImage, "--format", "{{.Os}}").run().replace("\n", "");
    String architecture =
        new Command("docker", "inspect", toImage, "--format", "{{.Architecture}}")
            .run()
            .replace("\n", "");
    assertThat(os).isEqualTo("linux");
    assertThat(architecture).isEqualTo("s390x");
    imageToDelete = toImage;
  }
}
