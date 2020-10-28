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

import com.google.cloud.tools.jib.api.Jib;
import com.google.cloud.tools.jib.api.JibContainerBuilder;
import com.google.cloud.tools.jib.api.buildplan.FileEntriesLayer;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** Class to convert jar to build container representations. */
public class JarToJibContainerBuilderConverter {

  /**
   * Generates a {@link JibContainerBuilder} from contents of a jar.
   *
   * @param jarPath path to the jar file
   * @param tempDirPath path to a temporary directory which will be used store the exploded jar's
   *     contents
   * @return JibContainerBuilder
   * @throws IOException if I/O error occurs when opening the jar file or if temporary directory
   *     provided doesn't exist
   */
  public static JibContainerBuilder toJibContainerBuilder(Path jarPath, Path tempDirPath)
      throws IOException {
    JibContainerBuilder containerBuilder = Jib.fromScratch();
    List<FileEntriesLayer> layers = JarProcessor.explodeStandardJar(jarPath, tempDirPath);
    List<String> entrypoint = JarProcessor.computeEntrypointForExplodedStandard(jarPath);
    containerBuilder.setEntrypoint(entrypoint).setFileEntriesLayers(layers);
    return containerBuilder;
  }
}
