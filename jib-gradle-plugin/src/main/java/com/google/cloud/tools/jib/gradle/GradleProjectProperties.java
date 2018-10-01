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

import com.google.cloud.tools.jib.event.DefaultEventDispatcher;
import com.google.cloud.tools.jib.event.EventDispatcher;
import com.google.cloud.tools.jib.event.EventHandlers;
import com.google.cloud.tools.jib.event.JibEventType;
import com.google.cloud.tools.jib.event.events.LogEvent;
import com.google.cloud.tools.jib.filesystem.AbsoluteUnixPath;
import com.google.cloud.tools.jib.frontend.JavaLayerConfigurations;
import com.google.cloud.tools.jib.plugins.common.MainClassInferenceException;
import com.google.cloud.tools.jib.plugins.common.MainClassResolver;
import com.google.cloud.tools.jib.plugins.common.ProjectProperties;
import com.google.cloud.tools.jib.plugins.common.TimerEventHandler;
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
import org.gradle.api.logging.Logger;
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
      Project project, Logger logger, Path extraDirectory, AbsoluteUnixPath appRoot) {
    try {
      return new GradleProjectProperties(
          project,
          makeEventDispatcher(logger),
          GradleLayerConfigurations.getForProject(project, logger, extraDirectory, appRoot));

    } catch (IOException ex) {
      throw new GradleException("Obtaining project build output files failed", ex);
    }
  }

  private static EventDispatcher makeEventDispatcher(Logger logger) {
    LogEventHandler logEventHandler = new LogEventHandler(logger);
    return new DefaultEventDispatcher(
        new EventHandlers()
            .add(JibEventType.LOGGING, logEventHandler)
            .add(
                JibEventType.TIMING,
                new TimerEventHandler(message -> logEventHandler.accept(LogEvent.debug(message)))));
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

  static Path getExplodedWarDirectory(Project project) {
    return project.getBuildDir().toPath().resolve(ProjectProperties.EXPLODED_WAR_DIRECTORY_NAME);
  }

  private final Project project;
  private final EventDispatcher eventDispatcher;
  private final JavaLayerConfigurations javaLayerConfigurations;

  @VisibleForTesting
  GradleProjectProperties(
      Project project,
      EventDispatcher eventDispatcher,
      JavaLayerConfigurations javaLayerConfigurations) {
    this.project = project;
    this.eventDispatcher = eventDispatcher;
    this.javaLayerConfigurations = javaLayerConfigurations;
  }

  @Override
  public JavaLayerConfigurations getJavaLayerConfigurations() {
    return javaLayerConfigurations;
  }

  @Override
  public EventDispatcher getEventDispatcher() {
    return eventDispatcher;
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
    return getWarTask(project) != null;
  }

  /**
   * Tries to resolve the main class.
   *
   * @throws GradleException if resolving the main class fails.
   */
  String getMainClass(JibExtension jibExtension) {
    try {
      return MainClassResolver.resolveMainClass(jibExtension.getContainer().getMainClass(), this);
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
