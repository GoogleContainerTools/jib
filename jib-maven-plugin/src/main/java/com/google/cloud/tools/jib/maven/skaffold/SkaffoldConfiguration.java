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

package com.google.cloud.tools.jib.maven.skaffold;

import java.io.File;
import java.util.Collections;
import java.util.List;
import org.apache.maven.plugins.annotations.Parameter;

/** Skaffold specific Jib plugin configuration options. */
public class SkaffoldConfiguration {

  /** Skaffold specific Jib plugin configuration for files to watch. */
  public static class Watch {
    @Parameter List<File> buildIncludes = Collections.emptyList();
    @Parameter List<File> includes = Collections.emptyList();
    @Parameter List<File> excludes = Collections.emptyList();
  }

  /** Skaffold specific Jib plugin configuration for files to sync. */
  public static class Sync {
    @Parameter List<File> excludes = Collections.emptyList();
  }

  /** Watch is unused, but left here to define how to parse it. */
  @Parameter Watch watch = new Watch();

  @Parameter Sync sync = new Sync();
}
