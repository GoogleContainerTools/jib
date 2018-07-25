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

import com.google.cloud.tools.jib.builder.BuildLogger;
import com.google.cloud.tools.jib.image.ImageReference;
import com.google.cloud.tools.jib.image.InvalidImageReferenceException;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.crypto.SettingsDecrypter;

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

    @Nullable @Parameter private String image;

    @Nullable @Parameter private String credHelper;

    public void set(String image) {
      this.image = image;
    }
  }

  /** Configuration for {@code container} parameter. */
  public static class ContainerParameters {

    @Parameter private boolean useCurrentTimestamp = false;

    @Parameter private List<String> jvmFlags = Collections.emptyList();

    @Nullable @Parameter private String mainClass;

    @Parameter private List<String> args = Collections.emptyList();

    @Nullable
    @Parameter(required = true)
    private String format = "Docker";

    @Parameter private List<String> ports = Collections.emptyList();
  }

  /**
   * @param image the image reference string to parse.
   * @param type name of the parameter being parsed (e.g. "to" or "from").
   * @return the {@link ImageReference} parsed from {@code from}.
   */
  static ImageReference parseImageReference(String image, String type) {
    try {
      return ImageReference.parse(image);
    } catch (InvalidImageReferenceException ex) {
      throw new IllegalStateException("Parameter '" + type + "' is invalid", ex);
    }
  }

  /**
   * Warns about deprecated parameters in use.
   *
   * @param logger The logger used to print the warnings
   */
  void handleDeprecatedParameters(BuildLogger logger) {
    StringBuilder deprecatedParams = new StringBuilder();
    if (!jvmFlags.isEmpty()) {
      deprecatedParams.append("  <jvmFlags> -> <container><jvmFlags>\n");
      if (container.jvmFlags.isEmpty()) {
        container.jvmFlags = jvmFlags;
      }
    }
    if (!Strings.isNullOrEmpty(mainClass)) {
      deprecatedParams.append("  <mainClass> -> <container><mainClass>\n");
      if (Strings.isNullOrEmpty(container.mainClass)) {
        container.mainClass = mainClass;
      }
    }
    if (!args.isEmpty()) {
      deprecatedParams.append("  <args> -> <container><args>\n");
      if (container.args.isEmpty()) {
        container.args = args;
      }
    }
    if (!Strings.isNullOrEmpty(format)) {
      deprecatedParams.append("  <format> -> <container><format>\n");
      container.format = format;
    }

    if (deprecatedParams.length() > 0) {
      logger.warn(
          "There are deprecated parameters used in the build configuration. Please make the "
              + "following changes to your pom.xml to avoid issues in the future:\n"
              + deprecatedParams);
    }
  }

  @Nullable
  @Parameter(defaultValue = "${project}", readonly = true)
  private MavenProject project;

  @Nullable
  @Parameter(defaultValue = "${session}", readonly = true)
  MavenSession session;

  @Parameter private FromConfiguration from = new FromConfiguration();

  @Parameter(property = "image")
  private ToConfiguration to = new ToConfiguration();

  @Parameter private ContainerParameters container = new ContainerParameters();

  @Deprecated @Parameter private List<String> jvmFlags = Collections.emptyList();

  @Nullable @Parameter private Map<String, String> environment;

  @Deprecated @Nullable @Parameter private String mainClass;

  @Deprecated @Parameter private List<String> args = Collections.emptyList();

  @Deprecated @Nullable @Parameter private String format;

  @Parameter(defaultValue = "false", required = true)
  private boolean useOnlyProjectCache;

  @Parameter(defaultValue = "false", required = true)
  private boolean allowInsecureRegistries;

  @Nullable
  @Parameter(defaultValue = "${project.basedir}/src/main/jib", required = true)
  private String extraDirectory;

  @Nullable @Component protected SettingsDecrypter settingsDecrypter;

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

  @Nullable
  String getTargetImage() {
    return to.image;
  }

  @Nullable
  String getTargetImageCredentialHelperName() {
    return Preconditions.checkNotNull(to).credHelper;
  }

  boolean getUseCurrentTimestamp() {
    return container.useCurrentTimestamp;
  }

  List<String> getJvmFlags() {
    return container.jvmFlags;
  }

  @Nullable
  Map<String, String> getEnvironment() {
    return environment;
  }

  @Nullable
  String getMainClass() {
    return container.mainClass;
  }

  List<String> getArgs() {
    return container.args;
  }

  List<String> getExposedPorts() {
    return container.ports;
  }

  String getFormat() {
    return Preconditions.checkNotNull(container.format);
  }

  boolean getUseOnlyProjectCache() {
    return useOnlyProjectCache;
  }

  boolean getAllowInsecureRegistries() {
    return allowInsecureRegistries;
  }

  Path getExtraDirectory() {
    // TODO: Should inform user about nonexistent directory if using custom directory.
    return Paths.get(Preconditions.checkNotNull(extraDirectory));
  }

  @VisibleForTesting
  void setJvmFlags(List<String> jvmFlags) {
    this.jvmFlags = jvmFlags;
  }

  @VisibleForTesting
  void setMainClass(String mainClass) {
    this.mainClass = mainClass;
  }

  @VisibleForTesting
  void setArgs(List<String> args) {
    this.args = args;
  }

  @VisibleForTesting
  void setFormat(String format) {
    this.format = format;
  }

  @VisibleForTesting
  void setProject(MavenProject project) {
    this.project = project;
  }

  @VisibleForTesting
  void setTargetImage(@Nullable String targetImage) {
    this.to.image = targetImage;
  }

  @VisibleForTesting
  void setExtraDirectory(String extraDirectory) {
    this.extraDirectory = extraDirectory;
  }
}
