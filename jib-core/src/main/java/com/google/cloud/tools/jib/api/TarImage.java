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

import java.nio.file.Path;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * Builds to a tarball archive.
 *
 * <p>Usage example:
 *
 * <pre>{@code
 * TarImage tarImage = TarImage.at(Paths.get("image.tar"))
 *                             .named("myimage");
 * }</pre>
 */
public class TarImage {

  /**
   * Constructs a {@link TarImage} with the specified path.
   *
   * @param path the path to the tarball archive
   * @return a new {@link TarImage}
   */
  public static TarImage at(Path path) {
    return new TarImage(path);
  }

  private final Path path;
  @Nullable private ImageReference imageReference;

  /** Instantiate with {@link #at}. */
  private TarImage(Path path) {
    this.path = path;
  }

  /**
   * Sets the name of the image. This is the name that shows up when the tar is loaded by the Docker
   * daemon.
   *
   * @param imageReference the image reference
   * @return this
   */
  public TarImage named(ImageReference imageReference) {
    this.imageReference = imageReference;
    return this;
  }

  /**
   * Sets the name of the image. This is the name that shows up when the tar is loaded by the Docker
   * daemon.
   *
   * @param imageReference the image reference
   * @return this
   * @throws InvalidImageReferenceException if {@code imageReference} is not a valid image reference
   */
  public TarImage named(String imageReference) throws InvalidImageReferenceException {
    return named(ImageReference.parse(imageReference));
  }

  Path getPath() {
    return path;
  }

  Optional<ImageReference> getImageReference() {
    return Optional.ofNullable(imageReference);
  }
}
