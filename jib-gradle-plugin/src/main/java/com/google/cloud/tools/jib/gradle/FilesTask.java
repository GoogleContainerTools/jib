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

import com.google.common.base.Preconditions;
import java.io.File;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.Nullable;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskAction;

/**
 * Print out changing source dependencies on a project.
 *
 * <p>Expected use: "./gradlew _jibSkaffoldFiles -q"
 */
public class FilesTask extends DefaultTask {

  @Nullable private JibExtension jibExtension;

  public FilesTask setJibExtension(JibExtension jibExtension) {
    this.jibExtension = jibExtension;
    return this;
  }

  @TaskAction
  public void listFiles() {
    Project project = getProject();

    // If this is not the root project, be sure to print the root project's build.gradle
    if (project != project.getRootProject()) {
      System.out.println(project.getRootProject().getBuildFile());
    }

    // Print subproject sources
    Set<Dependency> dependenciesToPrint = new HashSet<>();
    listFiles(project, dependenciesToPrint);

    // Cross-reference collected dependencies with their jar files
    Set<File> printedFiles = new HashSet<>();
    for (Configuration configuration :
        project.getConfigurations().getByName("runtime").getHierarchy()) {
      for (File file : configuration) {
        if (printedFiles.contains(file)) {
          continue;
        }
        String absolutePath = file.getAbsolutePath();
        for (Dependency dependency : dependenciesToPrint) {
          if (absolutePath.contains(dependency.getName() + "-" + dependency.getVersion())) {
            System.out.println(file);
            printedFiles.add(file);
          }
        }
      }
    }
  }

  private void listFiles(Project project, Set<Dependency> dependencyJars) {
    Preconditions.checkNotNull(jibExtension);
    JavaPluginConvention javaConvention =
        project.getConvention().getPlugin(JavaPluginConvention.class);
    SourceSet mainSourceSet = javaConvention.getSourceSets().findByName("main");
    if (mainSourceSet == null) {
      return;
    }

    // Print build.gradle
    System.out.println(project.getBuildFile());

    // Print sources
    mainSourceSet.getAllJava().getSourceDirectories().forEach(System.out::println);

    // Print resources
    mainSourceSet.getResources().getSourceDirectories().forEach(System.out::println);

    // Print extra layer
    if (project.getPlugins().hasPlugin(JibPlugin.class)) {
      System.out.println(jibExtension.getExtraDirectoryPath());
    }

    // Iterate over dependencies
    for (Configuration configuration :
        project.getConfigurations().getByName("runtime").getHierarchy()) {
      for (Dependency dependency : configuration.getDependencies()) {
        if (dependency instanceof ProjectDependency) {
          // If this is a project dependency, find sources files and print those
          Project dependencyProject = ((ProjectDependency) dependency).getDependencyProject();
          listFiles(dependencyProject, dependencyJars);
        } else if (dependency.getVersion() != null
            && dependency.getVersion().endsWith("SNAPSHOT")) {
          // Otherwise save for later to print jar file
          dependencyJars.add(dependency);
        }
      }
    }
  }
}
