/*
 * Copyright 2020 Google LLC.
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

import java.io.File;
import javax.inject.Inject;
import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.tasks.InputFiles;

/** Skaffold specific JibExtension parameters for configuring files to sync. */
public class SkaffoldSyncParameters {

  private final ConfigurableFileCollection fileCollection;
  private ConfigurableFileCollection excludes;

  @Inject
  public SkaffoldSyncParameters(Project project) {
    this.fileCollection = project.getObjects().fileCollection();
    this.excludes = project.getObjects().fileCollection();
  }

  /**
   * Get the excludes directive for sync functionality in skaffold.
   *
   * @return a set of absolute paths
   */
  @InputFiles
  public ConfigurableFileCollection getExcludes() {
    return excludes;
  }

  /**
   * Sets excludes. {@code excludes} can be any suitable object describing file paths convertible by
   * {@link Project#files} (such as {@link File}, {@code List<File>}, or {@code List<String>}).
   *
   * @param paths paths to set on excludes
   */
  public void setExcludes(Object paths) {
    this.excludes.from(paths);
  }

  @InputFiles
  public ConfigurableFileCollection getFileCollection() {
    return fileCollection;
  }
}
