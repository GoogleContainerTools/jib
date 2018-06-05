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

package com.google.cloud.tools.jib.gradle;

import com.google.common.annotations.VisibleForTesting;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.util.GradleVersion;

public class JibPlugin implements Plugin<Project> {

  @VisibleForTesting static final GradleVersion GRADLE_MIN_VERSION = GradleVersion.version("4.6");

  @VisibleForTesting static final String JIB_EXTENSION_NAME = "jib";
  @VisibleForTesting static final String BUILD_IMAGE_TASK_NAME = "jib";
  @VisibleForTesting static final String BUILD_DOCKER_TASK_NAME = "jibBuildToDockerDaemon";
  @VisibleForTesting static final String DOCKER_CONTEXT_TASK_NAME = "jibExportDockerContext";

  @Override
  public void apply(Project project) {
    checkGradleVersion();

    JibExtension jibExtension =
        project.getExtensions().create(JIB_EXTENSION_NAME, JibExtension.class, project);
    Task classesTask = project.getTasks().getByPath("classes");

    project
        .getTasks()
        .create(BUILD_IMAGE_TASK_NAME, BuildImageTask.class)
        .setJibExtension(jibExtension)
        .dependsOn(classesTask);
    project
        .getTasks()
        .create(DOCKER_CONTEXT_TASK_NAME, DockerContextTask.class)
        .setJibExtension(jibExtension)
        .dependsOn(classesTask);
    project
        .getTasks()
        .create(BUILD_DOCKER_TASK_NAME, BuildDockerTask.class)
        .setJibExtension(jibExtension)
        .dependsOn(classesTask);
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
}
