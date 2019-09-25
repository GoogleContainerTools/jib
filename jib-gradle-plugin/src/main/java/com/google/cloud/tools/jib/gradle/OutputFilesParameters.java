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

import com.google.cloud.tools.jib.plugins.common.PropertyNames;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.inject.Inject;
import org.gradle.api.Project;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;

/** Object that configures where Jib should create its build output files. */
public class OutputFilesParameters {

  private final Project project;

  private Path digest;
  private Path tar;
  private Path id;

  @Inject
  public OutputFilesParameters(Project project) {
    this.project = project;

    digest = project.getBuildDir().toPath().resolve("jib-image.digest");
    id = project.getBuildDir().toPath().resolve("jib-image.id");
    tar = project.getBuildDir().toPath().resolve("jib-image.tar");
  }

  @Input
  public String getDigest() {
    String property = System.getProperty(PropertyNames.OUTPUT_FILES_DIGEST);
    return property == null ? digest.toString() : property;
  }

  @Internal
  Path getDigestPath() {
    String property = System.getProperty(PropertyNames.OUTPUT_FILES_DIGEST);
    return property == null ? digest : Paths.get(property);
  }

  public void setDigest(String digest) {
    this.digest = Paths.get(digest);
  }

  @Input
  public String getId() {
    String property = System.getProperty(PropertyNames.OUTPUT_FILES_ID);
    return property == null ? id.toString() : property;
  }

  @Internal
  Path getIdPath() {
    String property = System.getProperty(PropertyNames.OUTPUT_FILES_ID);
    return property == null ? id : Paths.get(property);
  }

  public void setId(String id) {
    this.id = Paths.get(id);
  }

  @Input
  public String getTar() {
    String property = System.getProperty(PropertyNames.OUTPUT_FILES_TAR);
    return property == null ? tar.toString() : property;
  }

  @Internal
  Path getTarPath() {
    String property = System.getProperty(PropertyNames.OUTPUT_FILES_TAR);
    return property == null ? tar : Paths.get(property);
  }

  public void setTar(String tar) {
    this.tar = Paths.get(tar);
  }
}
