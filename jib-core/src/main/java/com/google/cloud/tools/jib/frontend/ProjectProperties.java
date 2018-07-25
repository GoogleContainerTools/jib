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

package com.google.cloud.tools.jib.frontend;

import com.google.cloud.tools.jib.builder.BuildLogger;
import com.google.cloud.tools.jib.configuration.LayerConfiguration;
import com.google.cloud.tools.jib.image.LayerEntry;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import javax.annotation.Nullable;

/** Project property methods that require maven/gradle-specific implementations. */
public interface ProjectProperties {

  /** Directory name for the cache. The directory will be relative to the build output directory. */
  String CACHE_DIRECTORY_NAME = "jib-cache";

  BuildLogger getLogger();

  String getPluginName();

  ImmutableList<LayerConfiguration> getLayerConfigurations();

  Path getCacheDirectory();

  String getJarPluginName();

  /** @return the name of the main class configured in a jar plugin, or null if none is found. */
  @Nullable
  String getMainClassFromJar();

  LayerEntry getDependenciesLayerEntry();

  LayerEntry getSnapshotDependenciesLayerEntry();

  LayerEntry getResourcesLayerEntry();

  LayerEntry getClassesLayerEntry();

  LayerEntry getExtraFilesLayerEntry();

  /**
   * @param prefix the prefix message for the {@link HelpfulSuggestions}.
   * @return a {@link HelpfulSuggestions} instance for main class inference failure.
   */
  HelpfulSuggestions getMainClassHelpfulSuggestions(String prefix);
}
