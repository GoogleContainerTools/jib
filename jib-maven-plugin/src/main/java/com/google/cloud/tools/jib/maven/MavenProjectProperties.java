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
import com.google.cloud.tools.jib.configuration.LayerConfiguration;
import com.google.cloud.tools.jib.frontend.HelpfulSuggestions;
import com.google.cloud.tools.jib.frontend.MainClassFinder;
import com.google.cloud.tools.jib.frontend.MainClassInferenceException;
import com.google.cloud.tools.jib.frontend.ProjectProperties;
import com.google.cloud.tools.jib.image.ImageReference;
import com.google.cloud.tools.jib.image.LayerEntry;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.annotation.Nullable;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;

/** Obtains information about a {@link MavenProject}. */
class MavenProjectProperties implements ProjectProperties {

  private static final String PLUGIN_NAME = "jib-maven-plugin";
  private static final String JAR_PLUGIN_NAME = "'maven-jar-plugin'";

  /**
   * @param project the {@link MavenProject} for the plugin.
   * @param mavenBuildLogger the logger used for printing status messages.
   * @param extraDirectory path to the directory for the extra files layer
   * @return a MavenProjectProperties from the given project and logger.
   * @throws MojoExecutionException if no class files are found in the output directory.
   */
  static MavenProjectProperties getForProject(
      MavenProject project, MavenBuildLogger mavenBuildLogger, Path extraDirectory)
      throws MojoExecutionException {
    try {
      return new MavenProjectProperties(
          project,
          mavenBuildLogger,
          MavenLayerConfigurations.getForProject(project, extraDirectory));
    } catch (IOException ex) {
      throw new MojoExecutionException(
          "Obtaining project build output files failed; make sure you have compiled your project "
              + "before trying to build the image. (Did you accidentally run \"mvn clean "
              + "jib:build\" instead of \"mvn clean compile jib:build\"?)",
          ex);
    }
  }

  private final MavenProject project;
  private final MavenBuildLogger mavenBuildLogger;
  private final MavenLayerConfigurations mavenLayerConfigurations;

  @VisibleForTesting
  MavenProjectProperties(
      MavenProject project,
      MavenBuildLogger mavenBuildLogger,
      MavenLayerConfigurations mavenLayerConfigurations) {
    this.project = project;
    this.mavenBuildLogger = mavenBuildLogger;
    this.mavenLayerConfigurations = mavenLayerConfigurations;
  }

  @Override
  public ImmutableList<LayerConfiguration> getLayerConfigurations() {
    return mavenLayerConfigurations.getLayerConfigurations();
  }

  @Override
  public LayerEntry getDependenciesLayerEntry() {
    return mavenLayerConfigurations.getDependenciesLayerEntry();
  }

  @Override
  public LayerEntry getSnapshotDependenciesLayerEntry() {
    return mavenLayerConfigurations.getSnapshotDependenciesLayerEntry();
  }

  @Override
  public LayerEntry getResourcesLayerEntry() {
    return mavenLayerConfigurations.getResourcesLayerEntry();
  }

  @Override
  public LayerEntry getClassesLayerEntry() {
    return mavenLayerConfigurations.getClassesLayerEntry();
  }

  @Override
  public LayerEntry getExtraFilesLayerEntry() {
    return mavenLayerConfigurations.getExtraFilesLayerEntry();
  }

  @Override
  public HelpfulSuggestions getMainClassHelpfulSuggestions(String prefix) {
    return HelpfulSuggestionsProvider.get(prefix);
  }

  @Override
  public BuildLogger getLogger() {
    return mavenBuildLogger;
  }

  @Override
  public String getPluginName() {
    return PLUGIN_NAME;
  }

  @Nullable
  @Override
  public String getMainClassFromJar() {
    Plugin mavenJarPlugin = project.getPlugin("org.apache.maven.plugins:maven-jar-plugin");
    if (mavenJarPlugin != null) {
      Xpp3Dom jarConfiguration = (Xpp3Dom) mavenJarPlugin.getConfiguration();
      if (jarConfiguration == null) {
        return null;
      }
      Xpp3Dom archiveObject = jarConfiguration.getChild("archive");
      if (archiveObject == null) {
        return null;
      }
      Xpp3Dom manifestObject = archiveObject.getChild("manifest");
      if (manifestObject == null) {
        return null;
      }
      Xpp3Dom mainClassObject = manifestObject.getChild("mainClass");
      if (mainClassObject == null) {
        return null;
      }
      return mainClassObject.getValue();
    }
    return null;
  }

  @Override
  public Path getCacheDirectory() {
    return Paths.get(project.getBuild().getDirectory(), CACHE_DIRECTORY_NAME);
  }

  @Override
  public String getJarPluginName() {
    return JAR_PLUGIN_NAME;
  }

  /**
   * Tries to resolve the main class.
   *
   * @param jibPluginConfiguration the mojo configuration properties.
   * @return the configured main class, or the inferred main class if none is configured.
   * @throws MojoExecutionException if resolving the main class fails.
   */
  String getMainClass(JibPluginConfiguration jibPluginConfiguration) throws MojoExecutionException {
    try {
      return MainClassFinder.resolveMainClass(jibPluginConfiguration.getMainClass(), this);
    } catch (MainClassInferenceException ex) {
      throw new MojoExecutionException(ex.getMessage(), ex);
    }
  }

  /**
   * Returns an {@link ImageReference} parsed from the configured target image, or one of the form
   * {@code project-name:project-version} if target image is not configured
   *
   * @param targetImage the configured target image reference (can be empty)
   * @param mavenBuildLogger the logger used to notify users of the target image parameter
   * @return an {@link ImageReference} parsed from the configured target image, or one of the form
   *     {@code project-name:project-version} if target image is not configured
   */
  ImageReference getGeneratedTargetDockerTag(
      @Nullable String targetImage, MavenBuildLogger mavenBuildLogger) {
    if (Strings.isNullOrEmpty(targetImage)) {
      // TODO: Validate that project name and version are valid repository/tag
      // TODO: Use HelpfulSuggestions
      mavenBuildLogger.lifecycle(
          "Tagging image with generated image reference "
              + project.getName()
              + ":"
              + project.getVersion()
              + ". If you'd like to specify a different tag, you can set the <to><image> parameter "
              + "in your pom.xml, or use the -Dimage=<MY IMAGE> commandline flag.");
      return ImageReference.of(null, project.getName(), project.getVersion());
    } else {
      return JibPluginConfiguration.parseImageReference(targetImage, "to");
    }
  }
}
