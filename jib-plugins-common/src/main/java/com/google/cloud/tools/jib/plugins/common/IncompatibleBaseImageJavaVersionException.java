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

package com.google.cloud.tools.jib.plugins.common;

/**
 * Exception when the Java version in the base image is incompatible with the Java version of the
 * application to be containerized. For example, when the project is Java 11 but the base image is
 * Java 8.
 */
public class IncompatibleBaseImageJavaVersionException extends Exception {

  private final int baseImageMajorJavaVersion;
  private final int projectMajorJavaVersion;

  public IncompatibleBaseImageJavaVersionException(
      int baseImageMajorJavaVersion, int projectMajorJavaVersion) {
    this.baseImageMajorJavaVersion = baseImageMajorJavaVersion;
    this.projectMajorJavaVersion = projectMajorJavaVersion;
  }

  public int getBaseImageMajorJavaVersion() {
    return baseImageMajorJavaVersion;
  }

  public int getProjectMajorJavaVersion() {
    return projectMajorJavaVersion;
  }
}
