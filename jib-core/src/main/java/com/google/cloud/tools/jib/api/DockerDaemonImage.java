package com.google.cloud.tools.jib.api;

import com.google.cloud.tools.jib.image.ImageReference;
import com.google.cloud.tools.jib.image.InvalidImageReferenceException;
import java.nio.file.Path;
import java.util.Map;

public interface DockerDaemonImage {

  /**
   * Instantiate with the image reference to tag the built image with. This is the name that shows
   * up on the Docker daemon.
   *
   * @param imageReference the image reference
   * @return a new {@link DockerDaemonImage}
   */
  static DockerDaemonImage named(ImageReference imageReference) {
    return new DockerDaemonTargetImage(imageReference);
  }

  /**
   * Instantiate with the image reference to tag the built image with. This is the name that shows
   * up on the Docker daemon.
   *
   * @param imageReference the image reference
   * @return a new {@link DockerDaemonImage}
   * @throws InvalidImageReferenceException if {@code imageReference} is not a valid image reference
   */
  static DockerDaemonImage named(String imageReference) throws InvalidImageReferenceException {
    return named(ImageReference.parse(imageReference));
  }

  /**
   * Sets the path to the {@code docker} CLI. This is {@code docker} by default.
   *
   * @param dockerExecutable the path to the {@code docker} CLI
   * @return this
   */
  public DockerDaemonImage setDockerExecutable(Path dockerExecutable);

  /**
   * Sets the additional environment variables to use when running {@link #dockerExecutable docker}.
   *
   * @param dockerEnvironment additional environment variables
   * @return this
   */
  public DockerDaemonImage setDockerEnvironment(Map<String, String> dockerEnvironment);
}
