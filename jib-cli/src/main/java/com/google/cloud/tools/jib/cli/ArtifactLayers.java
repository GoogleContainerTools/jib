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

package com.google.cloud.tools.jib.cli;

import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.buildplan.FileEntriesLayer;
import com.google.cloud.tools.jib.filesystem.DirectoryWalker;
import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Predicate;

public class ArtifactLayers {

  public static final String CLASSES = "classes";
  public static final String RESOURCES = "resources";
  public static final String DEPENDENCIES = "dependencies";
  public static final String SNAPSHOT_DEPENDENCIES = "snapshot dependencies";

  /**
   * Creates a layer containing contents of a directory. Only paths that match the given predicate
   * will be added.
   *
   * @param layerName name of the layer
   * @param sourceRoot path to source directory
   * @param pathFilter predicate to determine whether to add the path or not
   * @param basePathInContainer path to destination on container
   * @return {@link FileEntriesLayer} representing the layer
   * @throws IOException if io exception occurs when reading from the source directory
   */
  public static FileEntriesLayer getDirectoryContentsAsLayer(
      String layerName,
      Path sourceRoot,
      Predicate<Path> pathFilter,
      AbsoluteUnixPath basePathInContainer)
      throws IOException {
    FileEntriesLayer.Builder builder = FileEntriesLayer.builder().setName(layerName);
    new DirectoryWalker(sourceRoot)
        .filterRoot()
        .filter(path -> pathFilter.test(path))
        .walk(
            path -> {
              AbsoluteUnixPath pathOnContainer =
                  basePathInContainer.resolve(sourceRoot.relativize(path));
              builder.addEntry(path, pathOnContainer);
            });
    return builder.build();
  }
}
