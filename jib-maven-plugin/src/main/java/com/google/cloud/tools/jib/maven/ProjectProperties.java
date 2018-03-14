/*
 * Copyright 2018 Google Inc.
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

import com.google.cloud.tools.jib.builder.SourceFilesConfiguration;
import java.io.IOException;
import javax.annotation.Nullable;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;

/** Obtains information about a {@link MavenProject}. */
class ProjectProperties {

  private final MavenProject project;
  private final Log log;

  ProjectProperties(MavenProject project, Log log) {
    this.project = project;
    this.log = log;
  }

  /** @return the {@link SourceFilesConfiguration} based on the current project */
  SourceFilesConfiguration getSourceFilesConfiguration() throws MojoExecutionException {
    try {
      SourceFilesConfiguration sourceFilesConfiguration =
          new MavenSourceFilesConfiguration(project);

      // Logs the different source files used.
      log.info("");
      log.info("Containerizing application with the following files:");
      log.info("");

      log.info("\tDependencies:");
      log.info("");
      sourceFilesConfiguration
          .getDependenciesFiles()
          .forEach(dependencyFile -> log.info("\t\t" + dependencyFile));

      log.info("\tResources:");
      log.info("");
      sourceFilesConfiguration
          .getResourcesFiles()
          .forEach(resourceFile -> log.info("\t\t" + resourceFile));

      log.info("\tClasses:");
      log.info("");
      sourceFilesConfiguration
          .getClassesFiles()
          .forEach(classesFile -> log.info("\t\t" + classesFile));

      log.info("");

      return sourceFilesConfiguration;

    } catch (IOException ex) {
      throw new MojoExecutionException("Obtaining project build output files failed", ex);
    }
  }

  /** Extracts main class from 'maven-jar-plugin' configuration if available. */
  @Nullable
  String getMainClassFromMavenJarPlugin() {
    Plugin mavenJarPlugin = project.getPlugin("org.apache.maven.plugins:maven-jar-plugin");
    if (mavenJarPlugin != null) {
      String mainClass = getMainClassFromMavenJarPlugin(mavenJarPlugin);
      if (mainClass != null) {
        log.info("Using main class from maven-jar-plugin: " + mainClass);
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

  /** Returns the Maven logger. */
  Log getLog() {
    return log;
  }
}
