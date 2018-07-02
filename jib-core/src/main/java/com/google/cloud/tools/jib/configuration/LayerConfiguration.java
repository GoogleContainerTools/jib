/*
 * Copyright 2018 Google LLC. All rights reserved.
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

package com.google.cloud.tools.jib.configuration;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.List;

/** Configures how to build a layer in the container image. */
// TODO: Consolidate with ReproducibleLayerBuilder#LayerEntry.
public class LayerConfiguration {

  private final ImmutableList<Path> sourceFiles;
  private final String destinationOnImage;

  /**
   * Constructs a new layer configuration.
   *
   * <p>The source files are specified as a list instead of a set to define the order in which they
   * are added.
   *
   * @param sourceFiles the source files to build from. Source files that are directories will have
   *     all subfiles in the directory added (but not the directory itself)
   * @param destinationOnImage Unix-style path to add the source files to in the container image
   *     filesystem
   */
  public LayerConfiguration(List<Path> sourceFiles, String destinationOnImage) {
    Preconditions.checkArgument(!sourceFiles.contains(null));
    this.sourceFiles = ImmutableList.copyOf(sourceFiles);
    this.destinationOnImage = destinationOnImage;
  }

  public ImmutableList<Path> getSourceFiles() {
    return sourceFiles;
  }

  public String getDestinationOnImage() {
    return destinationOnImage;
  }
}
