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

import com.google.common.annotations.VisibleForTesting;
import java.util.List;
import java.util.stream.Collectors;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.UnknownTaskException;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.tasks.bundling.War;
import org.gradle.util.GradleVersion;

public class JibPlugin implements Plugin<Project> {

  @VisibleForTesting static final GradleVersion GRADLE_MIN_VERSION = GradleVersion.version("4.6");

  @VisibleForTesting static final String JIB_EXTENSION_NAME = "jib";
  @VisibleForTesting static final String BUILD_IMAGE_TASK_NAME = "jib";
  @VisibleForTesting static final String BUILD_TAR_TASK_NAME = "jibBuildTar";
  @VisibleForTesting static final String BUILD_DOCKER_TASK_NAME = "jibDockerBuild";
  @VisibleForTesting static final String FILES_TASK_NAME = "_jibSkaffoldFiles";
  @VisibleForTesting static final String EXPLODED_WAR_TASK_NAME = "jibExplodedWar";

  /**
   * Collects all project dependencies of the style "compile project(':mylib')" for any kind of
   * configuration [compile, runtime, etc]. It potentially will collect common test libraries in
   * configs like [test, integrationTest, etc], but it's either that or filter based on a
   * configuration containing the word "test" which feels dangerous.
   *
   * @param project this project we are containerizing
   * @return a list of projects that this project depends on.
   */
  @VisibleForTesting
  static List<Project> getProjectDependencies(Project project) {
    return project
        .getConfigurations()
        .stream()
        .map(Configuration::getDependencies)
        .flatMap(DependencySet::stream)
        .filter(ProjectDependency.class::isInstance)
        .map(ProjectDependency.class::cast)
        .map(ProjectDependency::getDependencyProject)
        .collect(Collectors.toList());
  }

  private static void checkGradleVersion() {
    if (GRADLE_MIN_VERSION.compareTo(GradleVersion.current()) > 0) {
      throw new GradleException(
          "Detected "
              + GradleVersion.current()
              + ", but jib requires "
              + GRADLE_MIN_VERSION
              + " or higher. You can upgrade by running 'gradle wrapper --gradle-version="
              + GRADLE_MIN_VERSION.getVersion()
              + "'.");
    }
  }

  @Override
  public void apply(Project project) {
    checkGradleVersion();

    JibExtension jibExtension =
        project.getExtensions().create(JIB_EXTENSION_NAME, JibExtension.class, project);

    Task buildImageTask =
        project
            .getTasks()
            .create(BUILD_IMAGE_TASK_NAME, BuildImageTask.class)
            .setJibExtension(jibExtension);
    Task buildDockerTask =
        project
            .getTasks()
            .create(BUILD_DOCKER_TASK_NAME, BuildDockerTask.class)
            .setJibExtension(jibExtension);
    Task buildTarTask =
        project
            .getTasks()
            .create(BUILD_TAR_TASK_NAME, BuildTarTask.class)
            .setJibExtension(jibExtension);
    project.getTasks().create(FILES_TASK_NAME, FilesTask.class).setJibExtension(jibExtension);

    project.afterEvaluate(
        projectAfterEvaluation -> {
          try {
            War warTask = GradleProjectProperties.getWarTask(project);
            Task dependsOnTask;
            if (warTask != null
                && (jibExtension.getPackagingOverride() == null
                    || "war".equals(jibExtension.getPackagingOverride()))) {
              ExplodedWarTask explodedWarTask =
                  (ExplodedWarTask)
                      project
                          .getTasks()
                          .create(EXPLODED_WAR_TASK_NAME, ExplodedWarTask.class)
                          .dependsOn(warTask);
              explodedWarTask.setWarFile(warTask.getArchivePath().toPath());
              explodedWarTask.setExplodedWarDirectory(
                  GradleProjectProperties.getExplodedWarDirectory(projectAfterEvaluation));
              // Have all tasks depend on the 'jibExplodedWar' task.
              dependsOnTask = explodedWarTask;
            } else {
              // Have all tasks depend on the 'classes' task.
              dependsOnTask = projectAfterEvaluation.getTasks().getByPath("classes");
            }
            buildImageTask.dependsOn(dependsOnTask);
            buildDockerTask.dependsOn(dependsOnTask);
            buildTarTask.dependsOn(dependsOnTask);

            // Find project dependencies and add a dependency to their assemble task. We make sure
            // to only add the dependency after BasePlugin is evaluated as otherwise the assemble
            // task may not be available yet.
            List<Project> computedDependencies = getProjectDependencies(projectAfterEvaluation);
            for (Project dependencyProject : computedDependencies) {
              dependencyProject
                  .getPlugins()
                  .withType(
                      BasePlugin.class,
                      unused -> {
                        Task assembleTask =
                            dependencyProject.getTasks().getByPath(BasePlugin.ASSEMBLE_TASK_NAME);
                        buildImageTask.dependsOn(assembleTask);
                        buildDockerTask.dependsOn(assembleTask);
                        buildTarTask.dependsOn(assembleTask);
                      });
            }
          } catch (UnknownTaskException ex) {
            throw new GradleException(
                "Could not find task 'classes' on project "
                    + projectAfterEvaluation.getDisplayName()
                    + " - perhaps you did not apply the 'java' plugin?",
                ex);
          }
        });
  }
}
