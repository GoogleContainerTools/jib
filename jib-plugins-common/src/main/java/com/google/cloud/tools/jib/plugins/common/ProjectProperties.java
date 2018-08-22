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

package com.google.cloud.tools.jib.plugins.common;

import com.google.cloud.tools.jib.JibLogger;
import com.google.cloud.tools.jib.frontend.JavaLayerConfigurations;
import java.nio.file.Path;
import javax.annotation.Nullable;

/** Project property methods that require maven/gradle-specific implementations. */
public interface ProjectProperties {

  /** Directory name for the cache. The directory will be relative to the build output directory. */
  String CACHE_DIRECTORY_NAME = "jib-cache";

  JibLogger getLogger();

  String getPluginName();

  JavaLayerConfigurations getJavaLayerConfigurations();

  Path getCacheDirectory();

  String getJarPluginName();

  /** @return the name of the main class configured in a jar plugin, or null if none is found. */
  @Nullable
  String getMainClassFromJar();
}
