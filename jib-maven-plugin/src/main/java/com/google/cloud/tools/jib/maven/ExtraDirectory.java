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

package com.google.cloud.tools.jib.maven;

import java.io.File;
import java.nio.file.Path;
import org.apache.maven.plugins.annotations.Parameter;

/** A bean that configures the source and destination of an extra directory. */
public class ExtraDirectory {

  @Parameter private File from = new File("");
  @Parameter private String into = "/";

  // Need default constructor for Maven
  public ExtraDirectory() {}

  public ExtraDirectory(File from, String into) {
    this.from = from;
    this.into = into;
  }

  // Allows <path>source</path> shorthand instead of forcing
  // <path><from>source</from><into>/</into></path>
  public void set(File path) {
    this.from = path;
    this.into = "/";
  }

  public Path getFrom() {
    return from.toPath();
  }

  public void setFrom(File from) {
    this.from = from;
  }

  String getInto() {
    return into;
  }
}
