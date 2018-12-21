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

import com.google.cloud.tools.jib.event.EventHandlers;
import com.google.cloud.tools.jib.frontend.JavaLayerConfigurations;
import java.nio.file.Path;
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

  // TODO: Move out of ProjectProperties.
  EventHandlers getEventHandlers();

  String getToolName();

  String getPluginName();

  JavaLayerConfigurations getJavaLayerConfigurations();

  Path getDefaultCacheDirectory();

  String getJarPluginName();

  /** @return the name of the main class configured in a jar plugin, or null if none is found. */
  @Nullable
  String getMainClassFromJar();

  String getName();

  String getVersion();
}
