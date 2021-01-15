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

package com.google.cloud.tools.jib.builder.steps;

import com.google.cloud.tools.jib.api.LogEvent;
import com.google.cloud.tools.jib.builder.ProgressEventDispatcher;
import com.google.cloud.tools.jib.configuration.BuildContext;
import com.google.cloud.tools.jib.image.Image;
import com.google.cloud.tools.jib.image.ImageTarball;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

public class WriteTarFileStep implements Callable<BuildResult> {

  private final BuildContext buildContext;
  private final ProgressEventDispatcher.Factory progressEventDispatcherFactory;

  private final Path outputPath;
  private final Image builtImage;

  WriteTarFileStep(
      BuildContext buildContext,
      ProgressEventDispatcher.Factory progressEventDispatcherFactory,
      Path outputPath,
      Image builtImage) {
    this.buildContext = buildContext;
    this.progressEventDispatcherFactory = progressEventDispatcherFactory;
    this.outputPath = outputPath;
    this.builtImage = builtImage;
  }

  @Override
  public BuildResult call() throws IOException {
    buildContext.getEventHandlers().dispatch(LogEvent.progress("Building image to tar file..."));

    try (ProgressEventDispatcher ignored =
        progressEventDispatcherFactory.create("writing to tar file", 1)) {
      // Builds the image to a tarball.
      if (outputPath.getParent() != null) {
        Files.createDirectories(outputPath.getParent());
      }
      try (OutputStream outputStream =
          new BufferedOutputStream(Files.newOutputStream(outputPath))) {
        new ImageTarball(
                builtImage,
                buildContext.getTargetImageConfiguration().getImage(),
                buildContext.getAllTargetImageTags())
            .writeTo(outputStream);
      }

      return BuildResult.fromImage(builtImage, buildContext.getTargetFormat());
    }
  }
}
