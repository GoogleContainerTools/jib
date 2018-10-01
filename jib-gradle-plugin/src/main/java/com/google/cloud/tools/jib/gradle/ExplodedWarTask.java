/*
 * Copyright 2018 Google LLC.
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
import javax.annotation.Nullable;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.Sync;

/** Gradle task that explodes a WAR file into a directory. */
public class ExplodedWarTask extends Sync {

  @Nullable private Path explodedWarDirectory;

  public void setWarFile(Path warFile) {
    from(getProject().zipTree(warFile.toFile()));
  }

  /**
   * Sets the exploded WAR output directory of this {@link Sync} task.
   *
   * @param explodedWarDirectory the directory where to extract the WAR file
   */
  public void setExplodedWarDirectory(Path explodedWarDirectory) {
    this.explodedWarDirectory = explodedWarDirectory;
    into(explodedWarDirectory);
  }

  @OutputDirectory
  @Nullable
  public Path getExplodedWarDirectory() {
    return explodedWarDirectory;
  }
}
