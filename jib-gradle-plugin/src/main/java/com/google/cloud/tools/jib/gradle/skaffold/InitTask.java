/*
 * Copyright 2019 Google LLC.
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

package com.google.cloud.tools.jib.gradle.skaffold;

import com.google.cloud.tools.jib.gradle.JibExtension;
import com.google.cloud.tools.jib.plugins.common.SkaffoldInitOutput;
import java.io.IOException;
import javax.inject.Inject;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskAction;

/**
 * Prints out to.image configuration and project name, used for Jib project detection in Skaffold.
 *
 * <p>Expected use: {@code ./gradlew _jibSkaffoldInit -q}
 */
public class InitTask extends DefaultTask {

  private JibExtension jibExtension;

  @Inject
  public InitTask(JibExtension jibExtension) {
    this.jibExtension = jibExtension;
  }

  public InitTask setJibExtension(JibExtension jibExtension) {
    this.jibExtension = jibExtension;
    return this;
  }

  /**
   * Task Action, lists modules and targets.
   *
   * @throws IOException if an error occurs generating the json string
   */
  @TaskAction
  public void listModulesAndTargets() throws IOException {
    Project project = getProject();
    // Ignore parent projects
    if (!project.getSubprojects().isEmpty()) {
      return;
    }
    SkaffoldInitOutput skaffoldInitOutput = new SkaffoldInitOutput();
    skaffoldInitOutput.setImage(jibExtension.getTo().getImage());
    if (!project.equals(project.getRootProject())) {
      skaffoldInitOutput.setProject(project.getName());
    }
    System.out.println();
    System.out.println("BEGIN JIB JSON");
    System.out.println(skaffoldInitOutput.getJsonString());
  }
}
