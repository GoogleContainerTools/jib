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

package com.google.cloud.tools.jib.cli.buildfile;

import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.buildplan.FileEntriesLayer;
import com.google.cloud.tools.jib.api.buildplan.FileEntry;
import com.google.cloud.tools.jib.api.buildplan.LayerObject;
import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Class to convert between different layer representations. */
class Layers {

  /**
   * Convert a layer spec to a list of layer objects.
   *
   * <p>Does not handle missing directories for files added via this method. We can either prefill
   * directories here, or allow passing of the file entry information directly to the reproducible
   * layer builder
   *
   * @param buildRoot the directory to resolve relative paths, usually the directory where the build
   *     config file is located
   * @param layersSpec a layersSpec containing configuration for all layers
   * @return a {@link List} of {@link LayerObject} to use as part of a buildplan
   * @throws IOException if traversing a directory fails
   */
  static List<LayerObject> toLayers(Path buildRoot, LayersSpec layersSpec) throws IOException {
    List<LayerObject> layers = new ArrayList<>();

    FilePropertiesStack filePropertiesStack = new FilePropertiesStack();
    // base properties
    layersSpec.getProperties().ifPresent(filePropertiesStack::push);

    for (LayerSpec entry : layersSpec.getEntries()) {
      // each loop is a new layer
      if (entry instanceof FileLayerSpec) {
        FileEntriesLayer.Builder layerBuiler = FileEntriesLayer.builder();

        FileLayerSpec fileLayer = (FileLayerSpec) entry;
        layerBuiler.setName(fileLayer.getName());
        // layer properties
        fileLayer.getProperties().ifPresent(filePropertiesStack::push);
        for (CopySpec copySpec : ((FileLayerSpec) entry).getFiles()) {
          // copy spec properties
          copySpec.getProperties().ifPresent(filePropertiesStack::push);

          // relativize all paths to the buildRoot location
          Path rawSrc = copySpec.getSrc();
          Path src = rawSrc.isAbsolute() ? rawSrc : buildRoot.resolve(rawSrc);
          AbsoluteUnixPath dest = copySpec.getDest();

          if (Files.isRegularFile(src)) { // regular file
            if (!copySpec.getExcludes().isEmpty() || !copySpec.getIncludes().isEmpty()) {
              throw new UnsupportedOperationException(
                  "Cannot apply includes/excludes on single file copy directives.");
            }
            layerBuiler.addEntry(
                src,
                copySpec.isDestEndsWithSlash() ? dest.resolve(src.getFileName()) : dest,
                filePropertiesStack.getFilePermissions(),
                filePropertiesStack.getModificationTime(),
                filePropertiesStack.getOwnership());
          } else if (Files.isDirectory(src)) { // directory
            List<PathMatcher> excludes =
                copySpec
                    .getExcludes()
                    .stream()
                    .map(Layers::toPathMatcher)
                    .collect(Collectors.toList());
            List<PathMatcher> includes =
                copySpec
                    .getIncludes()
                    .stream()
                    .map(Layers::toPathMatcher)
                    .collect(Collectors.toList());
            try (Stream<Path> dirWalk = Files.walk(src)) {
              dirWalk
                  // filter out against excludes
                  .filter(path -> excludes.stream().noneMatch(exclude -> exclude.matches(path)))
                  .filter(
                      path -> {
                        // if there are no includes directives, include everything
                        if (includes.isEmpty()) {
                          return true;
                        }
                        // TODO: if <dest>/path/to/file.txt is included because of a pattern like
                        // TODO: **/file.txt, ensure we create <dest>/path and <dest>/path/to with
                        // TODO: the correct directory properties here
                        // if there are includes directives, only include those specified
                        for (PathMatcher matcher : includes) {
                          if (matcher.matches(path)) {
                            return true;
                          }
                        }
                        return false;
                      })
                  .map(
                      path -> {
                        Path relative = src.relativize(path);
                        if (Files.isDirectory(path) || Files.isRegularFile(path)) {
                          return new FileEntry(
                              path,
                              dest.resolve(relative),
                              Files.isDirectory(path)
                                  ? filePropertiesStack.getDirectoryPermissions()
                                  : filePropertiesStack.getFilePermissions(),
                              filePropertiesStack.getModificationTime(),
                              filePropertiesStack.getOwnership());
                        } else {
                          throw new UnsupportedOperationException(
                              "Cannot create FileLayers from non-file, non-directory: "
                                  + path.toString());
                        }
                      })
                  .forEach(layerBuiler::addEntry);
            }
          } else { // other
            throw new UnsupportedOperationException(
                "Cannot create FileLayers from non-file, non-directory: " + src.toString());
          }
          copySpec.getProperties().ifPresent(ignored -> filePropertiesStack.pop());
        }
        fileLayer.getProperties().ifPresent(ignored -> filePropertiesStack.pop());
        // TODO: add logging/handling for empty layers
        layers.add(layerBuiler.build());
      } else {
        throw new UnsupportedOperationException("Only FileLayers are supported at this time.");
      }
    }
    layersSpec.getProperties().ifPresent(ignored -> filePropertiesStack.pop());
    return layers;
  }

  @VisibleForTesting
  static PathMatcher toPathMatcher(String glob) {
    return FileSystems.getDefault()
        .getPathMatcher(
            "glob:" + ((glob.endsWith("/") || glob.endsWith("\\")) ? glob + "**" : glob));
  }
}
