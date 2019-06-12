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

package com.google.cloud.tools.jib.plugins.common;

import com.google.cloud.tools.jib.api.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.Containerizer;
import com.google.cloud.tools.jib.api.JibContainerBuilder;
import com.google.cloud.tools.jib.api.LogEvent;
import com.google.cloud.tools.jib.api.RegistryImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import javax.annotation.Nullable;

/** Project property methods that require maven/gradle-specific implementations. */
public interface ProjectProperties {

  /** Directory name for the cache. The directory will be relative to the build output directory. */
  String CACHE_DIRECTORY_NAME = "jib-cache";

  /**
   * Directory name for the exploded WAR. The directory will be relative to the build output
   * directory.
   */
  String EXPLODED_WAR_DIRECTORY_NAME = "jib-exploded-war";

  // TODO: Move out of ProjectProperties.
  void waitForLoggingThread();

  /**
   * Adds the plugin's event handlers to a containerizer.
   *
   * @param containerizer the containerizer to add event handlers to
   */
  // TODO: Move out of ProjectProperties.
  void configureEventHandlers(Containerizer containerizer);

  void log(LogEvent logEvent);

  String getToolName();

  String getPluginName();

  /**
   * Starts the containerization process.
   *
   * @param baseImage the base image
   * @param appRoot root directory in the image where the app will be placed
   * @param containerizingMode mode to containerize the app
   * @return a {@link JibContainerBuilder} with classes, resources, and dependencies added to it
   * @throws IOException if there is a problem walking the project files
   */
  JibContainerBuilder createContainerBuilder(
      RegistryImage baseImage, AbsoluteUnixPath appRoot, ContainerizingMode containerizingMode)
      throws IOException;

  List<Path> getClassFiles() throws IOException;

  Path getDefaultCacheDirectory();

  String getJarPluginName();

  /** @return the name of the main class configured in a jar plugin, or null if none is found. */
  @Nullable
  String getMainClassFromJar();

  boolean isWarProject();

  String getName();

  String getVersion();

  int getMajorJavaVersion();

  boolean isOffline();
}
