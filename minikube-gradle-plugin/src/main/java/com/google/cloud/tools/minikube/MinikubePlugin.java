/*
 * Copyright 2017 Google Inc.
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

package com.google.cloud.tools.minikube;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

/** An extremely basic minikube plugin to manage the minikube lifecycle from gradle. */
public class MinikubePlugin implements Plugin<Project> {
  private static String MINIKUBE_GROUP = "Minikube";
  private Project project;
  private MinikubeExtension minikubeExtension;

  @Override
  public void apply(Project project) {
    this.project = project;

    createMinikubeExtension();

    configureMinikubeTaskAdditionCallback();
    createMinikubeStartTask();
    createMinikubeStopTask();
    createMinikubeDeleteTask();
  }

  // Configure tasks as they are added. This allows us to configure our own AND any user configured tasks.
  private void configureMinikubeTaskAdditionCallback() {
    project
        .getTasks()
        .withType(MinikubeTask.class)
        .whenTaskAdded(
            task -> {
              task.setMinikube(minikubeExtension.getMinikubeProvider());
              task.setGroup(MINIKUBE_GROUP);
            });
  }

  private void createMinikubeExtension() {
    minikubeExtension =
        project.getExtensions().create("minikube", MinikubeExtension.class, project);
  }

  private void createMinikubeStartTask() {
    MinikubeTask task = project.getTasks().create("minikubeStart", MinikubeTask.class);
    task.setCommand("start");
  }

  private void createMinikubeStopTask() {
    MinikubeTask task = project.getTasks().create("minikubeStop", MinikubeTask.class);
    task.setCommand("stop");
  }

  private void createMinikubeDeleteTask() {
    MinikubeTask task = project.getTasks().create("minikubeDelete", MinikubeTask.class);
    task.setCommand("delete");
  }
}
