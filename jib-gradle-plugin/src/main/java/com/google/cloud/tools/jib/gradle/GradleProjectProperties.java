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
import com.google.cloud.tools.jib.configuration.LayerConfiguration;
import com.google.cloud.tools.jib.frontend.HelpfulSuggestions;
import com.google.cloud.tools.jib.frontend.MainClassFinder;
import com.google.cloud.tools.jib.frontend.MainClassInferenceException;
import com.google.cloud.tools.jib.frontend.ProjectProperties;
import com.google.cloud.tools.jib.image.ImageReference;
import com.google.cloud.tools.jib.image.InvalidImageReferenceException;
import com.google.cloud.tools.jib.image.LayerEntry;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.FileCollection;
import org.gradle.jvm.tasks.Jar;

/** Obtains information about a Gradle {@link Project} that uses Jib. */
class GradleProjectProperties implements ProjectProperties {

  private static final String PLUGIN_NAME = "jib";
  private static final String JAR_PLUGIN_NAME = "'jar' task";

  /** @return a GradleProjectProperties from the given project and logger. */
  static GradleProjectProperties getForProject(
      Project project, GradleBuildLogger gradleBuildLogger, Path extraDirectory) {
    try {
      return new GradleProjectProperties(
          project,
          gradleBuildLogger,
          GradleLayerConfigurations.getForProject(project, gradleBuildLogger, extraDirectory));

    } catch (IOException ex) {
      throw new GradleException("Obtaining project build output files failed", ex);
    }
  }

  private final Project project;
  private final GradleBuildLogger gradleBuildLogger;
  private final GradleLayerConfigurations gradleLayerConfigurations;

  @VisibleForTesting
  GradleProjectProperties(
      Project project,
      GradleBuildLogger gradleBuildLogger,
      GradleLayerConfigurations gradleLayerConfigurations) {
    this.project = project;
    this.gradleBuildLogger = gradleBuildLogger;
    this.gradleLayerConfigurations = gradleLayerConfigurations;
  }

  @Override
  public ImmutableList<LayerConfiguration> getLayerConfigurations() {
    return gradleLayerConfigurations.getLayerConfigurations();
  }

  @Override
  public LayerEntry getDependenciesLayerEntry() {
    return gradleLayerConfigurations.getDependenciesLayerEntry();
  }

  @Override
  public LayerEntry getSnapshotDependenciesLayerEntry() {
    return gradleLayerConfigurations.getSnapshotDependenciesLayerEntry();
  }

  @Override
  public LayerEntry getResourcesLayerEntry() {
    return gradleLayerConfigurations.getResourcesLayerEntry();
  }

  @Override
  public LayerEntry getClassesLayerEntry() {
    return gradleLayerConfigurations.getClassesLayerEntry();
  }

  @Override
  public LayerEntry getExtraFilesLayerEntry() {
    return gradleLayerConfigurations.getExtraFilesLayerEntry();
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

  /**
   * Returns an {@link ImageReference} parsed from the configured target image, or one of the form
   * {@code project-name:project-version} if target image is not configured
   *
   * @param jibExtension the plugin configuration parameters to generate the name from
   * @param gradleBuildLogger the logger used to notify users of the target image parameter
   * @return an {@link ImageReference} parsed from the configured target image, or one of the form
   *     {@code project-name:project-version} if target image is not configured
   */
  ImageReference getGeneratedTargetDockerTag(
      JibExtension jibExtension, GradleBuildLogger gradleBuildLogger)
      throws InvalidImageReferenceException {
    Preconditions.checkNotNull(jibExtension);
    if (Strings.isNullOrEmpty(jibExtension.getTargetImage())) {
      // TODO: Validate that project name and version are valid repository/tag
      // TODO: Use HelpfulSuggestions
      gradleBuildLogger.lifecycle(
          "Tagging image with generated image reference "
              + project.getName()
              + ":"
              + project.getVersion().toString()
              + ". If you'd like to specify a different tag, you can set the jib.to.image "
              + "parameter in your build.gradle, or use the --image=<MY IMAGE> commandline flag.");
      return ImageReference.of(null, project.getName(), project.getVersion().toString());
    } else {
      return ImageReference.parse(jibExtension.getTargetImage());
    }
  }

  /**
   * Returns the input files for a task.
   *
   * @param extraDirectory the image's configured extra directory
   * @param project the gradle project
   * @return the input files to the task are all the output files for all the dependencies of the
   *     {@code classes} task
   */
  static FileCollection getInputFiles(File extraDirectory, Project project) {
    Task classesTask = project.getTasks().getByPath("classes");
    Set<? extends Task> classesDependencies =
        classesTask.getTaskDependencies().getDependencies(classesTask);

    List<FileCollection> dependencyFileCollections = new ArrayList<>();
    for (Task task : classesDependencies) {
      dependencyFileCollections.add(task.getOutputs().getFiles());
    }
    if (Files.exists(extraDirectory.toPath())) {
      return project.files(dependencyFileCollections, extraDirectory);
    } else {
      return project.files(dependencyFileCollections);
    }
  }
}
