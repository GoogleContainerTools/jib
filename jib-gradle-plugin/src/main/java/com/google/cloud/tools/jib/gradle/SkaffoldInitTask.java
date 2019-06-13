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

package com.google.cloud.tools.jib.gradle;

import com.google.cloud.tools.jib.plugins.common.SkaffoldInitOutput;
import com.google.common.base.Preconditions;
import java.io.IOException;
import javax.annotation.Nullable;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;

/**
 * Prints out to.image configuration and project name, used for Jib project detection in Skaffold.
 *
 * <p>Expected use: {@code ./gradlew _jibSkaffoldInit -q}
 */
public class SkaffoldInitTask extends DefaultTask {

  @Nullable private JibExtension jibExtension;

  public SkaffoldInitTask setJibExtension(JibExtension jibExtension) {
    this.jibExtension = jibExtension;
    return this;
  }

  @TaskAction
  public void listModulesAndTargets() throws IOException {
    SkaffoldInitOutput skaffoldInitOutput = new SkaffoldInitOutput();
    skaffoldInitOutput.setImage(Preconditions.checkNotNull(jibExtension).getTo().getImage());
    if (!getProject().equals(getProject().getRootProject())) {
      skaffoldInitOutput.setProject(getProject().getName());
    }
    System.out.println("\nBEGIN JIB JSON");
    System.out.println(skaffoldInitOutput.getJsonString());
  }
}
