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

import com.google.cloud.tools.jib.api.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.JavaContainerBuilder;
import com.google.cloud.tools.jib.api.JibContainerBuilder;
import com.google.cloud.tools.jib.api.RegistryImage;
import com.google.cloud.tools.jib.event.EventHandlers;
import com.google.cloud.tools.jib.event.events.LogEvent;
import com.google.cloud.tools.jib.event.events.ProgressEvent;
import com.google.cloud.tools.jib.event.events.TimerEvent;
import com.google.cloud.tools.jib.event.progress.ProgressEventHandler;
import com.google.cloud.tools.jib.filesystem.DirectoryWalker;
import com.google.cloud.tools.jib.plugins.common.ContainerizingMode;
import com.google.cloud.tools.jib.plugins.common.JavaContainerBuilderHelper;
import com.google.cloud.tools.jib.plugins.common.ProjectProperties;
import com.google.cloud.tools.jib.plugins.common.PropertyNames;
import com.google.cloud.tools.jib.plugins.common.TimerEventHandler;
import com.google.cloud.tools.jib.plugins.common.logging.ConsoleLogger;
import com.google.cloud.tools.jib.plugins.common.logging.ConsoleLoggerBuilder;
import com.google.cloud.tools.jib.plugins.common.logging.ProgressDisplayGenerator;
import com.google.cloud.tools.jib.plugins.common.logging.SingleThreadedExecutor;
import com.google.common.annotations.VisibleForTesting;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.apache.tools.ant.taskdefs.condition.Os;
import org.gradle.api.GradleException;
import org.gradle.api.JavaVersion;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.FileCollection;
import org.gradle.api.logging.Logger;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;
import org.gradle.jvm.tasks.Jar;

/** Obtains information about a Gradle {@link Project} that uses Jib. */
class GradleProjectProperties implements ProjectProperties {

  /** Used to generate the User-Agent header and history metadata. */
  private static final String TOOL_NAME = "jib-gradle-plugin";

  /** Used for logging during main class inference. */
  private static final String PLUGIN_NAME = "jib";

  /** Used for logging during main class inference. */
  private static final String JAR_PLUGIN_NAME = "'jar' task";

  /** Name of the `main` {@link SourceSet} to use as source files. */
  private static final String MAIN_SOURCE_SET_NAME = "main";

  /** @return a GradleProjectProperties from the given project and logger. */
  static GradleProjectProperties getForProject(Project project, Logger logger) {
    return new GradleProjectProperties(project, logger);
  }

  static Path getExplodedWarDirectory(Project project) {
    return project.getBuildDir().toPath().resolve(ProjectProperties.EXPLODED_WAR_DIRECTORY_NAME);
  }

  private static EventHandlers makeEventHandlers(
      Project project, Logger logger, SingleThreadedExecutor singleThreadedExecutor) {
    ConsoleLoggerBuilder consoleLoggerBuilder =
        (isProgressFooterEnabled(project)
                ? ConsoleLoggerBuilder.rich(singleThreadedExecutor)
                : ConsoleLoggerBuilder.plain(singleThreadedExecutor).progress(logger::lifecycle))
            .lifecycle(logger::lifecycle);
    if (logger.isDebugEnabled()) {
      consoleLoggerBuilder.debug(logger::debug);
    }
    if (logger.isInfoEnabled()) {
      consoleLoggerBuilder.info(logger::info);
    }
    if (logger.isWarnEnabled()) {
      consoleLoggerBuilder.warn(logger::warn);
    }
    if (logger.isErrorEnabled()) {
      consoleLoggerBuilder.error(logger::error);
    }
    ConsoleLogger consoleLogger = consoleLoggerBuilder.build();

    return EventHandlers.builder()
        .add(
            LogEvent.class,
            logEvent -> consoleLogger.log(logEvent.getLevel(), logEvent.getMessage()))
        .add(
            TimerEvent.class,
            new TimerEventHandler(message -> consoleLogger.log(LogEvent.Level.DEBUG, message)))
        .add(
            ProgressEvent.class,
            new ProgressEventHandler(
                update -> {
                  List<String> footer =
                      ProgressDisplayGenerator.generateProgressDisplay(
                          update.getProgress(), update.getUnfinishedLeafTasks());
                  footer.add("");
                  consoleLogger.setFooter(footer);
                }))
        .build();
  }

  private static boolean isProgressFooterEnabled(Project project) {
    if ("plain".equals(System.getProperty(PropertyNames.CONSOLE))) {
      return false;
    }

    switch (project.getGradle().getStartParameter().getConsoleOutput()) {
      case Plain:
        return false;

      case Auto:
        // Enables progress footer when ANSI is supported (Windows or TERM not 'dumb').
        return Os.isFamily(Os.FAMILY_WINDOWS) || !"dumb".equals(System.getenv("TERM"));

      default:
        return true;
    }
  }

  private final Project project;
  private final SingleThreadedExecutor singleThreadedExecutor = new SingleThreadedExecutor();
  private final EventHandlers eventHandlers;
  private final Logger logger;

  @VisibleForTesting
  GradleProjectProperties(Project project, Logger logger) {
    this.project = project;
    this.logger = logger;

    eventHandlers = makeEventHandlers(project, logger, singleThreadedExecutor);
  }

  @Override
  public JibContainerBuilder createContainerBuilder(
      RegistryImage baseImage, AbsoluteUnixPath appRoot, ContainerizingMode containerizingMode) {
    JavaContainerBuilder javaContainerBuilder =
        JavaContainerBuilder.from(baseImage).setAppRoot(appRoot);

    try {
      if (isWarProject()) {
        logger.info("WAR project identified, creating WAR image: " + project.getDisplayName());
        Path explodedWarPath = GradleProjectProperties.getExplodedWarDirectory(project);
        return JavaContainerBuilderHelper.fromExplodedWar(javaContainerBuilder, explodedWarPath);
      }

      JavaPluginConvention javaPluginConvention =
          project.getConvention().getPlugin(JavaPluginConvention.class);
      SourceSet mainSourceSet =
          javaPluginConvention.getSourceSets().getByName(MAIN_SOURCE_SET_NAME);

      FileCollection classesOutputDirectories =
          mainSourceSet.getOutput().getClassesDirs().filter(File::exists);
      Path resourcesOutputDirectory = mainSourceSet.getOutput().getResourcesDir().toPath();
      FileCollection allFiles = mainSourceSet.getRuntimeClasspath();
      FileCollection dependencyFiles =
          allFiles
              .minus(classesOutputDirectories)
              .filter(file -> !file.toPath().equals(resourcesOutputDirectory));

      // Adds resource files
      if (Files.exists(resourcesOutputDirectory)) {
        javaContainerBuilder.addResources(resourcesOutputDirectory);
      }

      // Adds class files
      for (File classesOutputDirectory : classesOutputDirectories) {
        javaContainerBuilder.addClasses(classesOutputDirectory.toPath());
      }
      if (classesOutputDirectories.isEmpty()) {
        logger.warn("No classes files were found - did you compile your project?");
      }

      // Adds dependency files
      javaContainerBuilder.addDependencies(
          dependencyFiles
              .getFiles()
              .stream()
              .filter(File::exists)
              .map(File::toPath)
              .collect(Collectors.toList()));

      return javaContainerBuilder.toContainerBuilder();

    } catch (IOException ex) {
      throw new GradleException("Obtaining project build output files failed", ex);
    }
  }

  @Override
  public List<Path> getClassFiles() throws IOException {
    // TODO: Consolidate with createContainerBuilder
    JavaPluginConvention javaPluginConvention =
        project.getConvention().getPlugin(JavaPluginConvention.class);
    SourceSet mainSourceSet = javaPluginConvention.getSourceSets().getByName(MAIN_SOURCE_SET_NAME);
    FileCollection classesOutputDirectories =
        mainSourceSet.getOutput().getClassesDirs().filter(File::exists);
    List<Path> classFiles = new ArrayList<>();
    for (File classesOutputDirectory : classesOutputDirectories) {
      classFiles.addAll(new DirectoryWalker(classesOutputDirectory.toPath()).walk().asList());
    }
    return classFiles;
  }

  @Override
  public void waitForLoggingThread() {
    singleThreadedExecutor.shutDownAndAwaitTermination();
  }

  @Override
  public EventHandlers getEventHandlers() {
    return eventHandlers;
  }

  @Override
  public String getToolName() {
    return TOOL_NAME;
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
  public Path getDefaultCacheDirectory() {
    return project.getBuildDir().toPath().resolve(CACHE_DIRECTORY_NAME);
  }

  @Override
  public String getJarPluginName() {
    return JAR_PLUGIN_NAME;
  }

  @Override
  public boolean isWarProject() {
    return TaskCommon.getWarTask(project) != null;
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

  @Override
  public String getName() {
    return project.getName();
  }

  @Override
  public String getVersion() {
    return project.getVersion().toString();
  }

  @Override
  public int getMajorJavaVersion() {
    JavaVersion version = JavaVersion.current();
    JavaPluginConvention javaPluginConvention =
        project.getConvention().findPlugin(JavaPluginConvention.class);
    if (javaPluginConvention != null) {
      version = javaPluginConvention.getTargetCompatibility();
    }
    return Integer.valueOf(version.getMajorVersion());
  }

  @Override
  public boolean isOffline() {
    return project.getGradle().getStartParameter().isOffline();
  }
}
