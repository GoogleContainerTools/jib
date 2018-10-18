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

package com.google.cloud.tools.jib.maven;

import com.google.cloud.tools.jib.api.Containerizer;
import com.google.cloud.tools.jib.api.JibContainerBuilder;
import com.google.cloud.tools.jib.filesystem.AbsoluteUnixPath;
import com.google.cloud.tools.jib.frontend.JavaLayerConfigurations;
import com.google.cloud.tools.jib.image.ImageReference;
import com.google.cloud.tools.jib.plugins.common.ProjectProperties;
import org.apache.maven.plugin.MojoExecutionException;

/** Configures and provides builders for the image building goals. */
// TODO: remove and use NPluginConfigurationProcess
class PluginConfigurationProcessor {

  /**
   * Returns true if the Maven packaging type is "war".
   *
   * @param jibPluginConfiguration the Jib plugin configuration
   * @return true if the Maven packaging type is "war"
   */
  private static boolean isWarPackaging(JibPluginConfiguration jibPluginConfiguration) {
    return "war".equals(jibPluginConfiguration.getProject().getPackaging());
  }

  /**
   * Gets the value of the {@code <container><appRoot>} parameter. If the parameter is empty,
   * returns {@link JavaLayerConfigurations#DEFAULT_WEB_APP_ROOT} for project with WAR packaging or
   * {@link JavaLayerConfigurations#DEFAULT_APP_ROOT} for other packaging.
   *
   * @param jibPluginConfiguration the Jib plugin configuration
   * @return the app root value
   * @throws MojoExecutionException if the app root is not an absolute path in Unix-style
   */
  static AbsoluteUnixPath getAppRootChecked(JibPluginConfiguration jibPluginConfiguration)
      throws MojoExecutionException {
    String appRoot = jibPluginConfiguration.getAppRoot();
    if (appRoot.isEmpty()) {
      appRoot =
          isWarPackaging(jibPluginConfiguration)
              ? JavaLayerConfigurations.DEFAULT_WEB_APP_ROOT
              : JavaLayerConfigurations.DEFAULT_APP_ROOT;
    }
    try {
      return AbsoluteUnixPath.get(appRoot);
    } catch (IllegalArgumentException ex) {
      throw new MojoExecutionException(
          "<container><appRoot> is not an absolute Unix-style path: " + appRoot);
    }
  }

  /**
   * Gets the value of the {@code <from><image>} parameter. If the parameter is null, returns
   * "gcr.io/distroless/java/jetty" for projects with WAR packaging or "gcr.io/distroless/java" for
   * other packaging.
   *
   * @param jibPluginConfiguration the Jib plugin configuration
   * @return the base image value
   */
  static String getBaseImage(JibPluginConfiguration jibPluginConfiguration) {
    String baseImage = jibPluginConfiguration.getBaseImage();
    if (baseImage == null) {
      return isWarPackaging(jibPluginConfiguration)
          ? "gcr.io/distroless/java/jetty"
          : "gcr.io/distroless/java";
    }
    return baseImage;
  }

  /** Disables annoying Apache HTTP client logging. */
  static void disableHttpLogging() {
    System.setProperty(
        "org.apache.commons.logging.Log", "org.apache.commons.logging.impl.SimpleLog");
    System.setProperty("org.apache.commons.logging.simplelog.defaultlog", "error");
  }

  /**
   * Configures a {@link Containerizer} with values pulled from project properties/build
   * configuration.
   *
   * @param containerizer the {@link Containerizer} to configure
   * @param jibPluginConfiguration the build configuration
   * @param projectProperties the project properties
   */
  static void configureContainerizer(
      Containerizer containerizer,
      JibPluginConfiguration jibPluginConfiguration,
      ProjectProperties projectProperties) {
    containerizer
        .setToolName(MavenProjectProperties.TOOL_NAME)
        .setEventHandlers(projectProperties.getEventHandlers())
        .setAllowInsecureRegistries(jibPluginConfiguration.getAllowInsecureRegistries())
        .setBaseImageLayersCache(Containerizer.DEFAULT_BASE_CACHE_DIRECTORY)
        .setApplicationLayersCache(projectProperties.getCacheDirectory());

    jibPluginConfiguration.getTargetImageAdditionalTags().forEach(containerizer::withAdditionalTag);

    if (jibPluginConfiguration.getUseOnlyProjectCache()) {
      containerizer.setBaseImageLayersCache(projectProperties.getCacheDirectory());
    }
  }

  private final JibContainerBuilder jibContainerBuilder;
  private final ImageReference baseImageReference;
  private final MavenSettingsServerCredentials mavenSettingsServerCredentials;
  private final boolean isBaseImageCredentialPresent;

  private PluginConfigurationProcessor(
      JibContainerBuilder jibContainerBuilder,
      ImageReference baseImageReference,
      MavenSettingsServerCredentials mavenSettingsServerCredentials,
      boolean isBaseImageCredentialPresent) {
    this.jibContainerBuilder = jibContainerBuilder;
    this.baseImageReference = baseImageReference;
    this.mavenSettingsServerCredentials = mavenSettingsServerCredentials;
    this.isBaseImageCredentialPresent = isBaseImageCredentialPresent;
  }

  JibContainerBuilder getJibContainerBuilder() {
    return jibContainerBuilder;
  }

  ImageReference getBaseImageReference() {
    return baseImageReference;
  }

  MavenSettingsServerCredentials getMavenSettingsServerCredentials() {
    return mavenSettingsServerCredentials;
  }

  boolean isBaseImageCredentialPresent() {
    return isBaseImageCredentialPresent;
  }
}
