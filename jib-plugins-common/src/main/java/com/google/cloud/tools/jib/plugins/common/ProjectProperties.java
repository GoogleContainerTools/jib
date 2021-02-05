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

import com.google.cloud.tools.jib.api.Containerizer;
import com.google.cloud.tools.jib.api.JavaContainerBuilder;
import com.google.cloud.tools.jib.api.JibContainerBuilder;
import com.google.cloud.tools.jib.api.LogEvent;
import com.google.cloud.tools.jib.plugins.common.RawConfiguration.ExtensionConfiguration;
import com.google.cloud.tools.jib.plugins.extension.JibPluginExtensionException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import javax.annotation.Nullable;

/** Project property methods that require maven/gradle-specific implementations. */
public interface ProjectProperties {

  /** Directory name for the cache. The directory will be relative to the build output directory. */
  String CACHE_DIRECTORY_NAME = "jib-cache";

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

  String getToolVersion();

  String getPluginName();

  /**
   * Starts the containerization process.
   *
   * @param javaContainerBuilder Java container builder to start with
   * @param containerizingMode mode to containerize the app
   * @return a {@link JibContainerBuilder} with classes, resources, and dependencies added to it
   * @throws IOException if there is a problem walking the project files
   */
  JibContainerBuilder createJibContainerBuilder(
      JavaContainerBuilder javaContainerBuilder, ContainerizingMode containerizingMode)
      throws IOException;

  List<Path> getClassFiles() throws IOException;

  List<Path> getDependencies();

  Path getDefaultCacheDirectory();

  String getJarPluginName();

  /**
   * Returns the name of the main class configured in a jar plugin, or null if none is found.
   *
   * @return the name of the main class configured in a jar plugin, or {@code null} if none is found
   */
  @Nullable
  String getMainClassFromJarPlugin();

  boolean isWarProject();

  String getName();

  String getVersion();

  int getMajorJavaVersion();

  boolean isOffline();

  JibContainerBuilder runPluginExtensions(
      List<? extends ExtensionConfiguration> extensionConfigs,
      JibContainerBuilder jibContainerBuilder)
      throws JibPluginExtensionException;
}
