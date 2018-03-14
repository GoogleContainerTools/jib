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

import java.util.ArrayList;
import java.util.List;

import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.logging.Logger;
import org.gradle.jvm.tasks.Jar;

import javax.annotation.Nullable;

/** Obtains information about a Gradle {@link Project}. */
class ProjectProperties {

  private final Project project;
  private final Logger logger;

  ProjectProperties(Project project, Logger logger) {
    this.project = project;
    this.logger = logger;
  }

  /** Extracts main class from 'jar' task, if available. */
  @Nullable
  String getMainClassFromJarTask() {
    List<Task> jarTasks = new ArrayList<>(project.getTasksByName("jar", false));
    if (jarTasks.size() != 1) {
      return null;
    }
    return (String) ((Jar) jarTasks.get(0)).getManifest().getAttributes().get("Main-Class");
  }

  Logger getLogger() {
    return logger;
  }
}
