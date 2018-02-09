/*
 * Copyright 2018 Google Inc.
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

import java.nio.file.Path;
import java.util.List;

/**
 * Immutable configuration that defines where the source files for each of the application layers
 * are.
 */
public interface SourceFilesConfiguration {

  /** @return the source files for the dependencies layer. */
  List<Path> getDependenciesFiles();

  /** @return the source files for the resources layer. */
  List<Path> getResourcesFiles();

  /** @return the source files for the classes layer. */
  List<Path> getClassesFiles();

  /**
   * @return the Unix-style path where the dependencies source files are placed in the container
   *     filesystem. Must end with backslash.
   */
  String getDependenciesPathOnImage();

  /**
   * @return the Unix-style path where the resources source files are placed in the container
   *     filesystem. Must end with backslash.
   */
  String getResourcesPathOnImage();

  /**
   * @return the Unix-style path where the classes source files are placed in the container
   *     filesystem. Must end with backslash.
   */
  String getClassesPathOnImage();
}
