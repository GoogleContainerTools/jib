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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

/** Defines the configuration parameters for Jib. Jib {@link Mojo}s should extend this class. */
abstract class JibPluginConfiguration extends AbstractMojo {

  /**
   * Configuration for {@code from} parameter, where image by default is {@code
   * gcr.io/distroless/java}.
   */
  public static class FromConfiguration {

    @Nullable
    @Parameter(required = true)
    String image = "gcr.io/distroless/java";

    @Nullable @Parameter String credHelper;
  }

  /** Configuration for {@code to} parameter, where image is required. */
  public static class ToConfiguration {

    @Nullable
    @Parameter(required = true)
    String image;

    @Nullable @Parameter String credHelper;
  }

  @Nullable
  @Parameter(defaultValue = "${project}", readonly = true)
  MavenProject project;

  @Nullable @Parameter FromConfiguration from = new FromConfiguration();

  @Nullable
  @Parameter(required = true)
  ToConfiguration to;

  @Parameter List<String> jvmFlags = Collections.emptyList();

  @Nullable @Parameter Map<String, String> environment;

  @Nullable @Parameter String mainClass;

  @Nullable
  @Parameter(defaultValue = "Docker", required = true)
  String format;

  @Parameter(defaultValue = "false", required = true)
  boolean useOnlyProjectCache;
}
