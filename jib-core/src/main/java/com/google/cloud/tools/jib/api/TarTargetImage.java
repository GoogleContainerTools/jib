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

import com.google.cloud.tools.jib.builder.BuildSteps;
import com.google.cloud.tools.jib.configuration.BuildConfiguration;
import com.google.cloud.tools.jib.configuration.ImageConfiguration;
import com.google.cloud.tools.jib.image.ImageReference;
import java.nio.file.Path;

class TarTargetImage implements TarImage, TargetImage {

  private final ImageReference imageReference;
  private final Path outputFile;

  TarTargetImage(ImageReference imageReference, Path outputFile) {
    this.imageReference = imageReference;
    this.outputFile = outputFile;
  }

  @Override
  public ImageConfiguration toImageConfiguration() {
    return ImageConfiguration.builder(imageReference).build();
  }

  @Override
  public BuildSteps toBuildSteps(BuildConfiguration buildConfiguration) {
    return BuildSteps.forBuildToTar(outputFile, buildConfiguration);
  }

  /**
   * Gets the output file to save the tarball archive to.
   *
   * @return the output file
   */
  Path getOutputFile() {
    return outputFile;
  }
}
