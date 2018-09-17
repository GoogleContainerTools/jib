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

import com.google.cloud.tools.jib.JibLogger;
import com.google.cloud.tools.jib.frontend.JavaLayerConfigurations;
import com.google.cloud.tools.jib.plugins.common.MainClassInferenceException;
import com.google.cloud.tools.jib.plugins.common.MainClassResolver;
import com.google.cloud.tools.jib.plugins.common.ProjectProperties;
import com.google.common.annotations.VisibleForTesting;
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
import org.gradle.api.plugins.WarPluginConvention;
import org.gradle.api.tasks.bundling.War;
import org.gradle.jvm.tasks.Jar;

/** Obtains information about a Gradle {@link Project} that uses Jib. */
class GradleProjectProperties implements ProjectProperties {

  /** Used to generate the User-Agent header and history metadata. */
  static final String TOOL_NAME = "jib-gradle-plugin";

  /** Used for logging during main class inference. */
  private static final String PLUGIN_NAME = "jib";

  /** Used for logging during main class inference. */
  private static final String JAR_PLUGIN_NAME = "'jar' task";

  /** @return a GradleProjectProperties from the given project and logger. */
  static GradleProjectProperties getForProject(
      Project project, GradleJibLogger gradleJibLogger, Path extraDirectory) {
    try {
      return new GradleProjectProperties(
          project,
          gradleJibLogger,
          GradleLayerConfigurations.getForProject(project, gradleJibLogger, extraDirectory));

    } catch (IOException ex) {
      throw new GradleException("Obtaining project build output files failed", ex);
    }
  }

  @Nullable
  static War getWarTask(Project project) {
    WarPluginConvention warPluginConvention =
        project.getConvention().findPlugin(WarPluginConvention.class);
    if (warPluginConvention == null) {
      return null;
    }
    return (War) warPluginConvention.getProject().getTasks().findByName("war");
  }

  private final Project project;
  private final GradleJibLogger gradleJibLogger;
  private final JavaLayerConfigurations javaLayerConfigurations;

  @VisibleForTesting
  GradleProjectProperties(
      Project project,
      GradleJibLogger gradleJibLogger,
      JavaLayerConfigurations javaLayerConfigurations) {
    this.project = project;
    this.gradleJibLogger = gradleJibLogger;
    this.javaLayerConfigurations = javaLayerConfigurations;
  }

  @Override
  public JavaLayerConfigurations getJavaLayerConfigurations() {
    return javaLayerConfigurations;
  }

  @Override
  public JibLogger getLogger() {
    return gradleJibLogger;
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

  @Override
  public boolean isWarProject() {
    // TODO: replace with "getWarTask(project) != null" once ready
    return false;
  }

  /**
   * Tries to resolve the main class.
   *
   * @throws GradleException if resolving the main class fails.
   */
  String getMainClass(JibExtension jibExtension) {
    try {
      return MainClassResolver.resolveMainClass(jibExtension.getMainClass(), this);
    } catch (MainClassInferenceException ex) {
      throw new GradleException(ex.getMessage(), ex);
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
