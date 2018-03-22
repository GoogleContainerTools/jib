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

import com.google.cloud.tools.jib.image.json.BuildableManifestTemplate;
import com.google.common.base.Preconditions;
import java.util.List;
import javax.annotation.Nullable;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;

/** Builds a container image. */
public class BuildImageTask extends DefaultTask {

  /** Linked extension that configures this task. Must be set before the task is executed. */
  @Nullable private JibExtension extension;

  @Input
  @Nullable
  public String getFromImage() {
    return Preconditions.checkNotNull(extension).getFrom().getImage();
  }

  @Input
  @Nullable
  @Optional
  public String getFromCredHelper() {
    return Preconditions.checkNotNull(extension).getFrom().getCredHelper();
  }

  @Input
  @Nullable
  public String getToImage() {
    return Preconditions.checkNotNull(extension).getTo().getImage();
  }

  @Input
  @Nullable
  @Optional
  public String getToCredHelper() {
    return Preconditions.checkNotNull(extension).getTo().getCredHelper();
  }

  @Input
  public List<String> getJvmFlags() {
    return Preconditions.checkNotNull(extension).getJvmFlags();
  }

  @Input
  @Nullable
  @Optional
  public String getMainClass() {
    return Preconditions.checkNotNull(extension).getMainClass();
  }

  @Input
  public boolean getReproducible() {
    return Preconditions.checkNotNull(extension).getReproducible();
  }

  @Input
  public Class<? extends BuildableManifestTemplate> getFormat() {
    return Preconditions.checkNotNull(extension).getFormat();
  }

  @TaskAction
  public void buildImage() {
    // TODO: Implement.

    System.out.println("from.image : " + getFromImage());
    System.out.println("from.credHelper : " + getFromCredHelper());
    System.out.println("to.image : " + getToImage());
    System.out.println("to.credHelper : " + getToCredHelper());

    System.out.println("jvmFlags: " + getJvmFlags());
    System.out.println("mainClass: " + getMainClass());
    System.out.println("reproducible: " + getReproducible());
    System.out.println("format: " + getFormat());
  }

  void setExtension(JibExtension jibExtension) {
    extension = jibExtension;
  }
}
