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

package com.google.cloud.tools.jib.gradle;

import java.nio.file.Path;
import java.nio.file.Paths;
import javax.inject.Inject;
import org.gradle.api.Project;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;

/** Configuration of an extra directory. */
public class ExtraDirectoryParameters {

  private Project project;
  private Path from = Paths.get("");
  private String into = "/";

  @Inject
  public ExtraDirectoryParameters(Project project) {
    this.project = project;
  }

  public ExtraDirectoryParameters(Project project, Path from, String into) {
    this.project = project;
    this.from = from;
    this.into = into;
  }

  @Input
  public String getFromString() {
    // Gradle warns about @Input annotations on File objects, so we have to expose a getter for a
    // String to make them go away.
    return from.toString();
  }

  @Internal
  public Path getFrom() {
    return from;
  }

  public void setFrom(Object from) {
    this.from = project.file(from).toPath();
  }

  @Input
  public String getInto() {
    return into;
  }

  public void setInto(String into) {
    this.into = into;
  }
}
