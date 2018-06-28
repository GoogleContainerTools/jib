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

package com.google.cloud.tools.jib.gradle;

import com.google.cloud.tools.jib.builder.BuildLogger;
import com.google.cloud.tools.jib.builder.SourceFilesConfiguration;
import com.google.cloud.tools.jib.frontend.HelpfulSuggestions;
import com.google.cloud.tools.jib.frontend.MainClassFinder;
import com.google.cloud.tools.jib.frontend.MainClassInferenceException;
import com.google.cloud.tools.jib.frontend.ProjectProperties;
import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.jvm.tasks.Jar;

/** Obtains information about a Gradle {@link Project} that uses Jib. */
class GradleProjectProperties implements ProjectProperties {

  private static final String PLUGIN_NAME = "jib";
  private static final String JAR_PLUGIN_NAME = "'jar' task";

  /** @return a GradleProjectProperties from the given project and logger. */
  static GradleProjectProperties getForProject(
      Project project, GradleBuildLogger gradleBuildLogger) {
    try {
      return new GradleProjectProperties(
          project,
          gradleBuildLogger,
          GradleSourceFilesConfiguration.getForProject(project, gradleBuildLogger));

    } catch (IOException ex) {
      throw new GradleException("Obtaining project build output files failed", ex);
    }
  }

  private final Project project;
  private final GradleBuildLogger gradleBuildLogger;
  private final SourceFilesConfiguration sourceFilesConfiguration;

  @VisibleForTesting
  GradleProjectProperties(
      Project project,
      GradleBuildLogger gradleBuildLogger,
      SourceFilesConfiguration sourceFilesConfiguration) {
    this.project = project;
    this.gradleBuildLogger = gradleBuildLogger;
    this.sourceFilesConfiguration = sourceFilesConfiguration;
  }

  @Override
  public SourceFilesConfiguration getSourceFilesConfiguration() {
    return sourceFilesConfiguration;
  }

  @Override
  public HelpfulSuggestions getMainClassHelpfulSuggestions(String prefix) {
    return HelpfulSuggestionsProvider.get(prefix);
  }

  @Override
  public BuildLogger getLogger() {
    return gradleBuildLogger;
  }

  @Override
  public String getPluginName() {
    return PLUGIN_NAME;
  }

  @Nullable
  @Override
  public String getMainClassFromJar() {
    List<Task> jarTasks = new ArrayList<>(project.getTasksByName("jar", false));
    if (jarTasks.size() != 1) {
      return null;
    }
    return (String) ((Jar) jarTasks.get(0)).getManifest().getAttributes().get("Main-Class");
  }

  @Override
  public Path getCacheDirectory() {
    return project.getBuildDir().toPath().resolve(CACHE_DIRECTORY_NAME);
  }

  @Override
  public String getJarPluginName() {
    return JAR_PLUGIN_NAME;
  }

  /**
   * Tries to resolve the main class.
   *
   * @throws GradleException if resolving the main class fails.
   */
  String getMainClass(JibExtension jibExtension) {
    try {
      return MainClassFinder.resolveMainClass(jibExtension.getMainClass(), this);
    } catch (MainClassInferenceException ex) {
      throw new GradleException(ex.getMessage(), ex);
    }
  }
}
