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

package com.google.cloud.tools.jib.maven;

import com.google.cloud.tools.jib.maven.extension.MavenData;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;

/** Maven-specific data and properties to supply to plugin extensions. */
class MavenExtensionData implements MavenData {

  private final MavenProject project;
  private final MavenSession session;

  MavenExtensionData(MavenProject project, MavenSession session) {
    this.project = project;
    this.session = session;
  }

  @Override
  public MavenProject getMavenProject() {
    return project;
  }

  @Override
  public MavenSession getMavenSession() {
    return session;
  }
}
