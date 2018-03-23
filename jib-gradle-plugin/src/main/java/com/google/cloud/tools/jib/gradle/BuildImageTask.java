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
import java.util.List;
import javax.annotation.Nullable;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;

/** Builds a container image. */
public class BuildImageTask extends DefaultTask {

  @Nullable private String fromImage;
  @Nullable private String fromCredHelper;
  @Nullable private String toImage;
  @Nullable private String toCredHelper;
  @Nullable private List<String> jvmFlags;
  @Nullable private String mainClass;
  private boolean reproducible;
  @Nullable private Class<? extends BuildableManifestTemplate> format;

  @Input
  @Nullable
  public String getFromImage() {
    return fromImage;
  }

  @Input
  @Nullable
  @Optional
  public String getFromCredHelper() {
    return fromCredHelper;
  }

  @Input
  @Nullable
  public String getToImage() {
    return toImage;
  }

  @Input
  @Nullable
  @Optional
  public String getToCredHelper() {
    return toCredHelper;
  }

  @Input
  @Nullable
  public List<String> getJvmFlags() {
    return jvmFlags;
  }

  @Input
  @Nullable
  @Optional
  public String getMainClass() {
    return mainClass;
  }

  @Input
  public boolean getReproducible() {
    return reproducible;
  }

  @Input
  @Nullable
  public Class<? extends BuildableManifestTemplate> getFormat() {
    return format;
  }

  @TaskAction
  public void buildImage() {
    // TODO: Implement.
  }

  void applyExtension(JibExtension jibExtension) {
    fromImage = jibExtension.getFrom().getImage();
    fromCredHelper = jibExtension.getFrom().getCredHelper();
    toImage = jibExtension.getTo().getImage();
    toCredHelper = jibExtension.getTo().getCredHelper();
    jvmFlags = jibExtension.getJvmFlags();
    mainClass = jibExtension.getMainClass();
    reproducible = jibExtension.getReproducible();
    format = jibExtension.getFormat();
  }
}
