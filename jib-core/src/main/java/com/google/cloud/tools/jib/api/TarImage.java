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
// TODO: Move to com.google.cloud.tools.jib once that package is cleaned up.

import java.nio.file.Path;

/**
 * Builds to a tarball archive.
 *
 * <p>Usage example:
 *
 * <pre>{@code
 * TarImage tarImage = TarImage.named("myimage")
 *                             .saveTo(Paths.get("image.tar"));
 * }</pre>
 */
public class TarImage {

  /** Finishes constructing a {@link TarImage}. */
  public static class Builder {

    private final ImageReference imageReference;

    private Builder(ImageReference imageReference) {
      this.imageReference = imageReference;
    }

    /**
     * Sets the output file to save the tarball archive to.
     *
     * @param outputFile the output file
     * @return a new {@link TarImage}
     */
    public TarImage saveTo(Path outputFile) {
      return new TarImage(imageReference, outputFile);
    }
  }

  /**
   * Configures the output tarball archive with an image reference. This image reference will be the
   * name of the image if loaded into the Docker daemon.
   *
   * @param imageReference the image reference
   * @return a {@link Builder} to finish constructing a new {@link TarImage}
   */
  public static Builder named(ImageReference imageReference) {
    return new Builder(imageReference);
  }

  /**
   * Configures the output tarball archive with an image reference to set as its tag.
   *
   * @param imageReference the image reference
   * @return a {@link Builder} to finish constructing a new {@link TarImage}
   * @throws InvalidImageReferenceException if {@code imageReference} is not a valid image reference
   */
  public static Builder named(String imageReference) throws InvalidImageReferenceException {
    return named(ImageReference.parse(imageReference));
  }

  private final ImageReference imageReference;
  private final Path outputFile;

  /** Instantiate with {@link #named}. */
  private TarImage(ImageReference imageReference, Path outputFile) {
    this.imageReference = imageReference;
    this.outputFile = outputFile;
  }

  /**
   * Gets the output file to save the tarball archive to.
   *
   * @return the output file
   */
  Path getOutputFile() {
    return outputFile;
  }

  ImageReference getImageReference() {
    return imageReference;
  }
}
