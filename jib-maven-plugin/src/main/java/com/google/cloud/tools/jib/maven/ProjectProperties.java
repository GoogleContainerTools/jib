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

import com.google.cloud.tools.jib.builder.BuildConfiguration;
import com.google.cloud.tools.jib.builder.SourceFilesConfiguration;
import com.google.cloud.tools.jib.frontend.MainClassFinder;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;

/** Obtains information about a {@link MavenProject}. */
class ProjectProperties {

  private static final String PLUGIN_NAME = "jib-maven-plugin";

  /** Directory name for the cache. The directory will be relative to the build output directory. */
  private static final String CACHE_DIRECTORY_NAME = "jib-cache";

  private final MavenProject project;
  private final Log log;
  private final SourceFilesConfiguration sourceFilesConfiguration;

  /** @return a ProjectProperties from the given project and logger. */
  static ProjectProperties getForProject(MavenProject project, Log log)
      throws MojoExecutionException {
    try {
      return new ProjectProperties(
          project, log, MavenSourceFilesConfiguration.getForProject(project));
    } catch (IOException ex) {
      throw new MojoExecutionException(
          "Obtaining project build output files failed; make sure you have compiled your project "
              + "before trying to build the image. (Did you accidentally run \"mvn clean "
              + "jib:build\" instead of \"mvn clean compile jib:build\"?)",
          ex);
    }
  }

  Path getCacheDirectory() {
    return Paths.get(project.getBuild().getDirectory(), CACHE_DIRECTORY_NAME);
  }

  @VisibleForTesting
  ProjectProperties(
      MavenProject project, Log log, SourceFilesConfiguration sourceFilesConfiguration) {
    this.project = project;
    this.log = log;
    this.sourceFilesConfiguration = sourceFilesConfiguration;
  }

  /** @return the {@link SourceFilesConfiguration} based on the current project */
  SourceFilesConfiguration getSourceFilesConfiguration() {
    return sourceFilesConfiguration;
  }

  /**
   * If {@code mainClass} is {@code null}, tries to infer main class in this order:
   *
   * <ul>
   *   <li>1. Looks in a {@code jar} task.
   *   <li>2. Searches for a class defined with a main method.
   * </ul>
   *
   * <p>Warns if main class is not valid.
   *
   * @throws MojoExecutionException if no valid main class is not found.
   */
  String getMainClass(@Nullable String mainClass) throws MojoExecutionException {
    if (mainClass == null) {
      log.info(
          "Searching for main class... Add a 'mainClass' configuration to '"
              + PLUGIN_NAME
              + "' to improve build speed.");
      mainClass = getMainClassFromMavenJarPlugin();
      if (mainClass == null || !BuildConfiguration.isValidJavaClass(mainClass)) {
        log.debug(
            "Could not find a valid main class specified in maven-jar-plugin; attempting to infer "
                + "main class.");

        try {
          // Adds each file in each classes output directory to the classes files list.
          List<String> mainClasses = new ArrayList<>();
          for (Path classPath : sourceFilesConfiguration.getClassesFiles()) {
            mainClasses.addAll(MainClassFinder.findMainClasses(classPath));
          }

          if (mainClasses.size() == 1) {
            // Valid class found; use inferred main class
            mainClass = mainClasses.get(0);
          } else if (mainClasses.size() == 0 && mainClass == null) {
            // No main class found anywhere
            throw new MojoExecutionException(
                HelpfulSuggestionsProvider.get("Main class was not found")
                    .forMainClassNotFound(PLUGIN_NAME));
          } else if (mainClasses.size() > 1 && mainClass == null) {
            // More than one main class found with no jar plugin to fall back on; error
            throw new MojoExecutionException(
                HelpfulSuggestionsProvider.get(
                        "Multiple valid main classes were found: " + String.join(", ", mainClasses))
                    .forMainClassNotFound(PLUGIN_NAME));
          }
        } catch (IOException ex) {
          throw new MojoExecutionException(
              HelpfulSuggestionsProvider.get("Failed to get main class")
                  .forMainClassNotFound(PLUGIN_NAME),
              ex);
        }
      }
    }
    Preconditions.checkNotNull(mainClass);
    if (!BuildConfiguration.isValidJavaClass(mainClass)) {
      log.warn("'mainClass' is not a valid Java class : " + mainClass);
    }

    return mainClass;
  }

  /** Extracts main class from 'maven-jar-plugin' configuration if available. */
  @Nullable
  private String getMainClassFromMavenJarPlugin() {
    Plugin mavenJarPlugin = project.getPlugin("org.apache.maven.plugins:maven-jar-plugin");
    if (mavenJarPlugin != null) {
      String mainClass = getMainClassFromMavenJarPlugin(mavenJarPlugin);
      if (mainClass != null) {
        log.debug("Using main class from maven-jar-plugin: " + mainClass);
        return mainClass;
      }
    }
    return null;
  }

  /** Gets the {@code mainClass} configuration from {@code maven-jar-plugin}. */
  @Nullable
  private String getMainClassFromMavenJarPlugin(Plugin mavenJarPlugin) {
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
}
