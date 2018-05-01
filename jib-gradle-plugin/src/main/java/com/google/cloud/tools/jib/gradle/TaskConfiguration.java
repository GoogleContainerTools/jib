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

import com.google.cloud.tools.jib.image.json.BuildableManifestTemplate;
import java.util.List;
import javax.annotation.Nullable;
import org.gradle.api.Task;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;

/**
 * Defines the configuration parameters injected from {@link JibExtension}. {@link Task}s should use
 * this class as a nested input. This allows Gradle to inspect and inform the user of missing
 * required inputs.
 */
class TaskConfiguration {

  @Nullable private ImageConfiguration from;
  @Nullable private ImageConfiguration to;
  @Nullable private List<String> jvmFlags;
  @Nullable private String mainClass;
  private boolean reproducible;
  @Nullable private Class<? extends BuildableManifestTemplate> format;
  private boolean useOnlyProjectCache;

  @Nested
  @Optional
  @Nullable
  public ImageConfiguration getFrom() {
    return from;
  }

  @Nested
  @Nullable
  public ImageConfiguration getTo() {
    return to;
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

  @Input
  public boolean getUseOnlyProjectCache() {
    return useOnlyProjectCache;
  }

  /**
   * Applies the configuration from {@code jibExtension}. This must be called before the {@link
   * TaskAction}.
   */
  void applyExtension(JibExtension jibExtension) {
    from = jibExtension.getFrom();
    to = jibExtension.getTo();
    jvmFlags = jibExtension.getJvmFlags();
    mainClass = jibExtension.getMainClass();
    reproducible = jibExtension.getReproducible();
    format = jibExtension.getFormat();
    useOnlyProjectCache = jibExtension.getUseOnlyProjectCache();
  }
}
