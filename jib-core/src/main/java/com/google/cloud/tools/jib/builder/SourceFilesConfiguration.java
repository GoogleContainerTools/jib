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

package com.google.cloud.tools.jib.builder;

import com.google.common.collect.ImmutableList;
import java.nio.file.Path;

/**
 * Immutable configuration that defines where the source files for each of the application layers
 * are.
 */
public interface SourceFilesConfiguration {

  String DEFAULT_DEPENDENCIES_PATH_ON_IMAGE = "/app/libs/";
  String DEFAULT_SNAPSHOT_DEPENDENCIES_PATH_ON_IMAGE = "/app/snapshot-libs/";
  String DEFAULT_RESOURCES_PATH_ON_IMAGE = "/app/resources/";
  String DEFAULT_CLASSES_PATH_ON_IMAGE = "/app/classes/";

  /**
   * @return the source files for the dependencies layer. These files should be in a deterministic
   *     order.
   */
  ImmutableList<Path> getDependenciesFiles();

  /**
   * @return the source files for snapshot dependencies. These files should be in a deterministic
   *     order
   */
  ImmutableList<Path> getSnapshotDependenciesFiles();

  /**
   * @return the source files for the resources layer. These files should be in a deterministic
   *     order.
   */
  ImmutableList<Path> getResourcesFiles();

  /**
   * @return the source files for the classes layer. These files should be in a deterministic order.
   */
  ImmutableList<Path> getClassesFiles();

  /**
   * @return the Unix-style path where the dependencies source files are placed in the container
   *     filesystem. Must end with slash.
   */
  String getDependenciesPathOnImage();

  /**
   * @return the Unix-style path where the snapshot dependencies sources files are place in the
   *     container filesystem. Must end with slash.
   */
  String getSnapshotDependenciesPathOnImage();

  /**
   * @return the Unix-style path where the resources source files are placed in the container
   *     filesystem. Must end with slash.
   */
  String getResourcesPathOnImage();

  /**
   * @return the Unix-style path where the classes source files are placed in the container
   *     filesystem. Must end with slash.
   */
  String getClassesPathOnImage();
}
