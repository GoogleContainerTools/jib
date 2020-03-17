/*
 * Copyright 2019 Google LLC.
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

import com.google.cloud.tools.jib.api.buildplan.FileEntriesLayer;
import com.google.cloud.tools.jib.configuration.ContainerConfiguration;
import com.google.common.collect.ImmutableList;
import java.util.List;

/**
 * A class containing the representation of the contents of a container. Currently only exposes
 * "layers", but can be extended to expose {@link ContainerConfiguration}, {@link ImageReference} of
 * the base image, or other informational classes.
 *
 * <p>This class is immutable and thread-safe.
 */
public class JibContainerDescription {

  private final ImmutableList<FileEntriesLayer> layers;

  JibContainerDescription(List<FileEntriesLayer> layers) {
    this.layers = ImmutableList.copyOf(layers);
  }

  /**
   * Returns a list of "user configured" layers, does <em>not</em> include base layer information.
   *
   * @return a {@link List} of {@link FileEntriesLayer}s
   */
  public List<FileEntriesLayer> getFileEntriesLayers() {
    return layers;
  }
}
