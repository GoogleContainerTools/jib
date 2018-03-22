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

import com.google.common.base.Preconditions;
import javax.annotation.Nullable;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;

/** Builds a container image. */
public class BuildImageTask extends DefaultTask {

  /** Linked extension that configures this task. */
  @Nullable private JibExtension extension;

  @TaskAction
  public void buildImage() {
    Preconditions.checkNotNull(extension);

    // TODO: Implement.

    System.out.println("from.image : " + extension.getFrom().getImage());
    System.out.println("from.credHelper : " + extension.getFrom().getCredHelper());
    System.out.println("to.image : " + extension.getTo().getImage());
    System.out.println("to.credHelper : " + extension.getTo().getCredHelper());

    System.out.println("jvmFlags: " + extension.getJvmFlags());
    System.out.println("mainClass: " + extension.getMainClass());
    System.out.println("reproducible: " + extension.getReproducible());
    System.out.println("format: " + extension.getFormat());
  }

  void setExtension(JibExtension jibExtension) {
    extension = jibExtension;
  }
}
