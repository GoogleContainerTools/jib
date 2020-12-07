/*
 * Copyright 2020 Google LLC.
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

package com.google.cloud.tools.jib.cli.jar;

import com.google.cloud.tools.jib.api.InvalidImageReferenceException;
import com.google.cloud.tools.jib.api.Jib;
import com.google.cloud.tools.jib.api.JibContainerBuilder;
import com.google.cloud.tools.jib.api.buildplan.FileEntriesLayer;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** Class to build a container representation from the contents of a jar file. */
public class JarFiles {

  /**
   * Generates a {@link JibContainerBuilder} from contents of a jar file.
   *
   * @param jarPath path to the jar file
   * @param tempDirPath path to a temporary directory which will be used store the exploded jar's
   *     contents
   * @param mode mode for processing jar
   * @return JibContainerBuilder
   * @throws IOException if I/O error occurs when opening the jar file or if temporary directory
   *     provided doesn't exist
   * @throws InvalidImageReferenceException if the base image reference is invalid
   */
  public static JibContainerBuilder toJibContainerBuilder(
      Path jarPath, Path tempDirPath, ProcessingMode mode)
      throws IOException, InvalidImageReferenceException {

    // Use distroless as the base image.
    JibContainerBuilder containerBuilder = Jib.from("gcr.io/distroless/java");

    List<FileEntriesLayer> layers;
    List<String> entrypoint;
    if (JarModeProcessor.determineJarType(jarPath).equals(JarModeProcessor.JarType.SPRING_BOOT)) {
      if (mode.equals(ProcessingMode.packaged)) {
        layers = JarModeProcessor.createLayerForPackagedSpringboot(jarPath);
        entrypoint = JarModeProcessor.computeEntrypointForPackagedSpringboot(jarPath);
      } else {
        layers = JarModeProcessor.createLayersForExplodedSpringBootFat(jarPath, tempDirPath);
        entrypoint = JarModeProcessor.computeEntrypointForExplodedSpringBoot();
      }
    } else {
      if (mode.equals(ProcessingMode.packaged)) {
        layers = JarModeProcessor.createLayersForPackagedStandard(jarPath);
        entrypoint = JarModeProcessor.computeEntrypointForPackagedStandard(jarPath);
      } else {
        layers = JarModeProcessor.createLayersForExplodedStandard(jarPath, tempDirPath);
        entrypoint = JarModeProcessor.computeEntrypointForExplodedStandard(jarPath);
      }
    }
    containerBuilder.setEntrypoint(entrypoint).setFileEntriesLayers(layers);
    return containerBuilder;
  }
}
