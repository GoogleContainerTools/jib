/*
 * Copyright 2018 Google LLC. All Rights Reserved.
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

import javax.annotation.Nullable;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;

/** Builds a container image and exports to the default Docker daemon. */
public class BuildDockerTask extends DefaultTask {

  @Nullable private JibExtension jibExtension;

  @TaskAction
  public void buildDocker() {
    getLogger().warn("Doing gradle jibBuildDocker!");
  }

  void setJibExtension(JibExtension jibExtension) {
    this.jibExtension = jibExtension;
  }
}
