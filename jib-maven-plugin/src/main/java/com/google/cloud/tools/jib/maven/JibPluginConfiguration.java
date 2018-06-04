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

import com.google.cloud.tools.jib.image.ImageReference;
import com.google.cloud.tools.jib.image.InvalidImageReferenceException;
import com.google.common.base.Preconditions;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import org.apache.maven.execution.MavenSession;
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
    private String image = "gcr.io/distroless/java";

    @Nullable @Parameter private String credHelper;
  }

  /** Configuration for {@code to} parameter, where image is required. */
  public static class ToConfiguration {

    @Nullable
    @Parameter(required = true)
    private String image;

    @Nullable @Parameter private String credHelper;

    public void set(String image) {
      this.image = image;
    }
  }

  /** @return the {@link ImageReference} parsed from {@code from}. */
  static ImageReference parseBaseImageReference(String from) {
    try {
      return ImageReference.parse(from);
    } catch (InvalidImageReferenceException ex) {
      throw new IllegalStateException("Parameter 'from' is invalid", ex);
    }
  }

  /** @return the {@link ImageReference} parsed from {@code to}. */
  static ImageReference parseTargetImageReference(String to) {
    try {
      return ImageReference.parse(to);
    } catch (InvalidImageReferenceException ex) {
      throw new IllegalStateException("Parameter 'to' is invalid", ex);
    }
  }

  @Nullable
  @Parameter(defaultValue = "${project}", readonly = true)
  private MavenProject project;

  @Nullable
  @Parameter(defaultValue = "${session}", readonly = true)
  MavenSession session;

  @Nullable @Parameter private FromConfiguration from = new FromConfiguration();

  @Nullable
  @Parameter(property = "image", required = true)
  private ToConfiguration to;

  @Parameter private List<String> jvmFlags = Collections.emptyList();

  @Nullable @Parameter private Map<String, String> environment;

  @Nullable @Parameter private String mainClass;

  @Parameter private List<String> args = Collections.emptyList();

  @Nullable
  @Parameter(defaultValue = "Docker", required = true)
  private String format;

  @Parameter(defaultValue = "false", required = true)
  private boolean useOnlyProjectCache;

  MavenProject getProject() {
    return Preconditions.checkNotNull(project);
  }

  String getBaseImage() {
    return Preconditions.checkNotNull(Preconditions.checkNotNull(from).image);
  }

  @Nullable
  String getBaseImageCredentialHelperName() {
    return Preconditions.checkNotNull(from).credHelper;
  }

  String getTargetImage() {
    return Preconditions.checkNotNull(Preconditions.checkNotNull(to).image);
  }

  @Nullable
  String getTargetImageCredentialHelperName() {
    return Preconditions.checkNotNull(to).credHelper;
  }

  List<String> getJvmFlags() {
    return jvmFlags;
  }

  @Nullable
  Map<String, String> getEnvironment() {
    return environment;
  }

  @Nullable
  String getMainClass() {
    return mainClass;
  }

  List<String> getArgs() {
    return args;
  }

  String getFormat() {
    return Preconditions.checkNotNull(format);
  }

  boolean getUseOnlyProjectCache() {
    return useOnlyProjectCache;
  }
}
