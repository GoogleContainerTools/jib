/*
 * Copyright 2026 Google LLC.
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

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import org.gradle.api.JavaVersion;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.file.Directory;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.logging.configuration.ConsoleOutput;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.plugins.WarPlugin;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.jvm.tasks.Jar;

/**
 * The state of a Gradle {@link Project} that Jib needs at task execution time.
 *
 * <p>Tasks must not access the {@link Project} at execution time to be compatible with the Gradle
 * configuration cache. Instances of this class are therefore created at configuration time and only
 * hold lazy file collections, providers and values that the configuration cache can serialize. The
 * providers are evaluated when the configuration cache entry is stored (or at execution time when
 * the configuration cache is disabled).
 */
public class GradleProjectData {

  /** Name of the `main` {@link SourceSet} to use as source files. */
  private static final String MAIN_SOURCE_SET_NAME = SourceSet.MAIN_SOURCE_SET_NAME;

  /**
   * Captures the state of a Gradle project.
   *
   * @param project the Gradle project; only accessed at configuration time
   * @param configurationName supplies the name of the configuration of which the dependencies
   *     should be packed into the container
   * @return a new {@link GradleProjectData}
   */
  public static GradleProjectData create(Project project, Supplier<String> configurationName) {
    return new GradleProjectData(project, configurationName);
  }

  private final String name;
  private final Provider<String> version;
  private final ProjectLayout layout;
  private final FileCollection dependencyFiles;
  private final FileCollection projectDependencyFiles;
  private final FileCollection mainSourceSetOutput;
  private final FileCollection classesOutputDirectories;
  private final Provider<File> resourcesOutputDirectory;
  private final Provider<Boolean> warProject;
  private final Provider<String> warFilePath;
  private final Provider<File> jarFile;
  private final Provider<String> mainClassFromJar;
  private final Provider<File> buildDirectory;
  private final Provider<Integer> majorJavaVersion;
  private final boolean offline;
  private final ConsoleOutput consoleOutput;

  private GradleProjectData(Project project, Supplier<String> configurationName) {
    ProviderFactory providers = project.getProviders();
    layout = project.getLayout();
    name = project.getName();
    version = providers.provider(() -> project.getVersion().toString());
    offline = project.getGradle().getStartParameter().isOffline();
    consoleOutput = project.getGradle().getStartParameter().getConsoleOutput();

    dependencyFiles =
        layout.files(
            (Callable<Configuration>)
                () -> project.getConfigurations().getByName(configurationName.get()));
    projectDependencyFiles =
        layout.files(
            (Callable<FileCollection>)
                () ->
                    project
                        .getConfigurations()
                        .getByName(configurationName.get())
                        .getIncoming()
                        .artifactView(
                            view ->
                                view.componentFilter(
                                    id -> id instanceof ProjectComponentIdentifier))
                        .getFiles());
    mainSourceSetOutput =
        layout.files((Callable<FileCollection>) () -> getMainSourceSet(project).getOutput());
    classesOutputDirectories =
        layout.files(
            (Callable<FileCollection>)
                () -> getMainSourceSet(project).getOutput().getClassesDirs());
    resourcesOutputDirectory =
        providers.provider(() -> getMainSourceSet(project).getOutput().getResourcesDir());

    warProject = providers.provider(() -> project.getPlugins().hasPlugin(WarPlugin.class));
    warFilePath = providers.provider(() -> computeWarFilePath(project));
    jarFile = providers.provider(() -> computeJarFile(project));
    mainClassFromJar = providers.provider(() -> computeMainClassFromJar(project));
    buildDirectory = layout.getBuildDirectory().map(Directory::getAsFile);
    majorJavaVersion = providers.provider(() -> computeMajorJavaVersion(project));
  }

  @Nullable
  private static String computeWarFilePath(Project project) {
    TaskProvider<Task> bootWarTask = TaskCommon.getBootWarTaskProvider(project);
    if (bootWarTask != null && bootWarTask.get().getEnabled()) {
      return bootWarTask.get().getOutputs().getFiles().getAsPath();
    }

    TaskProvider<Task> warTask = TaskCommon.getWarTaskProvider(project);
    if (warTask == null) {
      return null;
    }
    return warTask.get().getOutputs().getFiles().getAsPath();
  }

  @Nullable
  private static File computeJarFile(Project project) {
    Task jarTask = project.getTasks().findByName("jar");
    if (!(jarTask instanceof Jar)) {
      return null;
    }
    return ((Jar) jarTask).getArchiveFile().get().getAsFile();
  }

  @Nullable
  private static String computeMainClassFromJar(Project project) {
    Task jarTask = project.getTasks().findByName("jar");
    if (!(jarTask instanceof Jar)) {
      return null;
    }

    Object value = ((Jar) jarTask).getManifest().getAttributes().get("Main-Class");

    if (value instanceof Provider) {
      value = ((Provider<?>) value).getOrNull();
    }

    if (value instanceof String) {
      return (String) value;
    }

    if (value == null) {
      return null;
    }

    return String.valueOf(value);
  }

  private static int computeMajorJavaVersion(Project project) {
    JavaVersion version = JavaVersion.current();
    JavaPluginExtension javaPluginExtension =
        project.getExtensions().findByType(JavaPluginExtension.class);
    if (javaPluginExtension != null) {
      version = javaPluginExtension.getTargetCompatibility();
    }
    return Integer.valueOf(version.getMajorVersion());
  }

  private static SourceSet getMainSourceSet(Project project) {
    SourceSetContainer sourceSetContainer =
        project.getExtensions().getByType(SourceSetContainer.class);
    return sourceSetContainer.getByName(MAIN_SOURCE_SET_NAME);
  }

  String getName() {
    return name;
  }

  String getVersion() {
    return version.get();
  }

  boolean isOffline() {
    return offline;
  }

  ConsoleOutput getConsoleOutput() {
    return consoleOutput;
  }

  /**
   * Returns the files of the configuration of which the dependencies should be packed into the
   * container.
   *
   * @return the dependency files
   */
  FileCollection getDependencyFiles() {
    return dependencyFiles;
  }

  /**
   * Returns the files of the dependencies that are other projects of this build.
   *
   * @return the project dependency files
   */
  FileCollection getProjectDependencyFiles() {
    return projectDependencyFiles;
  }

  FileCollection getClassesOutputDirectories() {
    return classesOutputDirectories;
  }

  Path getResourcesOutputDirectory() {
    return resourcesOutputDirectory.get().toPath();
  }

  boolean isWarProject() {
    return warProject.get();
  }

  @Nullable
  String getWarFilePath() {
    return warFilePath.getOrNull();
  }

  @Nullable
  File getJarFile() {
    return jarFile.getOrNull();
  }

  @Nullable
  String getMainClassFromJar() {
    return mainClassFromJar.getOrNull();
  }

  File getBuildDirectory() {
    return buildDirectory.get();
  }

  int getMajorJavaVersion() {
    return majorJavaVersion.get();
  }

  /**
   * Returns the input files for a task. These files include the gradle {@link
   * org.gradle.api.artifacts.Configuration}, output directories (classes, resources, etc.) of the
   * main {@link org.gradle.api.tasks.SourceSet}, and any extraDirectories defined by the user to
   * include in the container.
   *
   * @param extraDirectories the image's configured extra directories
   * @return the input files
   */
  FileCollection getInputFiles(List<Path> extraDirectories) {
    List<Object> dependencyFileCollections = new ArrayList<>();
    dependencyFileCollections.add(dependencyFiles);
    // Output directories (classes and resources) from main SourceSet are added
    // so that BuildTarTask picks up changes in these and do not skip task
    dependencyFileCollections.add(mainSourceSetOutput);

    extraDirectories.stream()
        .filter(Files::exists)
        .map(Path::toFile)
        .forEach(dependencyFileCollections::add);

    return layout.files(dependencyFileCollections);
  }
}
