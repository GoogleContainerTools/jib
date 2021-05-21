/*
 * Copyright 2021 Google LLC.
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

package com.google.cloud.tools.jib.cli.war;

import com.google.cloud.tools.jib.api.InvalidImageReferenceException;
import com.google.cloud.tools.jib.api.Jib;
import com.google.cloud.tools.jib.api.JibContainerBuilder;
import com.google.cloud.tools.jib.api.buildplan.FileEntriesLayer;
import com.google.cloud.tools.jib.cli.ArtifactProcessor;
import com.google.cloud.tools.jib.cli.CommonCliOptions;
import com.google.cloud.tools.jib.cli.SharedArtifactCliOptions;
import com.google.cloud.tools.jib.cli.War;
import com.google.cloud.tools.jib.plugins.common.logging.ConsoleLogger;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.List;

public class WarFiles {

  /**
   * Generates a {@link JibContainerBuilder} from contents of a WAR file.
   *
   * @param processor artifact processor
   * @param warOptions war cli options
   * @param commonCliOptions common cli options
   * @param sharedArtifactCliOptions shared artifact cli options
   * @param logger console logger
   * @return JibContainerBuilder
   * @throws IOException if I/O error occurs when opening the jar file or if temporary directory
   *     provided doesn't exist
   * @throws InvalidImageReferenceException if the base image reference is invalid
   */
  public static JibContainerBuilder toJibContainerBuilder(
      ArtifactProcessor processor,
      War warOptions,
      CommonCliOptions commonCliOptions,
      SharedArtifactCliOptions sharedArtifactCliOptions,
      ConsoleLogger logger)
      throws IOException, InvalidImageReferenceException {
    JibContainerBuilder containerBuilder = Jib.from("jetty");
    List<FileEntriesLayer> layers = processor.createLayers();

    // JVM Flags are ignored
    List<String> entrypoint = processor.computeEntrypoint(ImmutableList.of());

    containerBuilder.setEntrypoint(entrypoint).setFileEntriesLayers(layers);
    return containerBuilder;
  }
}
