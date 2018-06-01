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
import java.io.File;
import java.util.List;
import java.util.stream.Collectors;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.util.GradleVersion;

public class JibPlugin implements Plugin<Project> {

  @VisibleForTesting static final GradleVersion GRADLE_MIN_VERSION = GradleVersion.version("4.6");

  @Override
  public void apply(Project project) {
    checkGradleVersion();
    JibExtension jibExtension = project.getExtensions().create("jib", JibExtension.class, project);
    project
        .getTasks()
        .create(
            "jib",
            BuildImageTask.class,
            buildImageTask -> buildImageTask.setJibExtension(jibExtension));
    project
        .getTasks()
        .create(
            "jibDockerContext",
            DockerContextTask.class,
            dockerContextTask -> dockerContextTask.setJibExtension(jibExtension));
    project
        .getTasks()
        .create(
            "jibBuildDocker",
            BuildDockerTask.class,
            buildDockerTask -> buildDockerTask.setJibExtension(jibExtension));
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

  // List<File> files =
  //     project
  //         .getTasksByName("compileJava", false)
  //         .stream()
  //         .map(Task::getOutputs)
  //         .flatMap(taskOutputs -> taskOutputs.getFiles().getFiles().stream())
  //         .collect(Collectors.toList());
  //             System.out.println(files);
  //                 dockerContextTask.dependsOn(files);

              // project
              //     .getTasksByName("classes", false)
              //     .forEach(
              //     classesTask -> {
              //     classesTask
              //     .getTaskDependencies()
              //     .getDependencies(classesTask)
              //     .forEach(
              //     classesTaskDependency -> {
              //     classesTaskDependency
              //     .getOutputs()
              //     .getFiles()
              //     .forEach(
              //     file -> {
              //     System.out.println(
              //     "classes dependency ("
              //     + classesTaskDependency
              //     + ") outputfile: "
              //     + file);
              //     });
              //     });
              //     });
