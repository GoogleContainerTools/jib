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

import com.google.cloud.tools.jib.image.ImageReference;
import java.nio.file.Path;

/**
 * Builds to a tarball archive.
 *
 * Usage example:
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
   * Configures the output tarball archive with an image reference to set as its tag.
   *
   * @param imageReference the image reference
   * @return a {@link Builder} to finish constructing a new {@link TarImage}
   */
  public static Builder named(ImageReference imageReference) {
    return new Builder(imageReference);
  }

  private final ImageReference imageReference;
  private final Path outputFile;

  /** Instantiate with {@link #named}. */
  private TarImage(ImageReference imageReference, Path outputFile) {
    this.imageReference = imageReference;
    this.outputFile = outputFile;
  }

  ImageReference getImageReference() {
    return imageReference;
  }

  Path getOutputFile() {
    return outputFile;
  }
}
