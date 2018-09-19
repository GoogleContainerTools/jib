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
import javax.annotation.Nullable;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
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
    Preconditions.checkNotNull(jibExtension);

    Project project = getProject();
    JavaPluginConvention javaConvention =
        project.getConvention().getPlugin(JavaPluginConvention.class);
    SourceSet mainSourceSet = javaConvention.getSourceSets().findByName("main");
    if (mainSourceSet == null) {
      return;
    }

    // Print build.gradle
    System.out.println(project.getBuildFile());

    // Print sources
    mainSourceSet.getAllJava().getSrcDirs().forEach(System.out::println);

    // Print resources
    mainSourceSet.getResources().getSrcDirs().forEach(System.out::println);

    // Print extra layer
    if (project.getPlugins().hasPlugin(JibPlugin.class)) {
      System.out.println(jibExtension.getExtraDirectoryPath());
    }

    // Print dependencies
    for (Configuration configuration : project.getConfigurations()) {
      for (Dependency dependency : configuration.getDependencies()) {
        System.out.println(dependency);
      }
    }

    // TODO:
    // Find all project dependencies
    // For all project dependencies,
    //   Add jar files to set
    //   Print source
    //   Find all project dependencies on project dependency
    //   ...
    //     ...
    //
    // For each dependency file
    //   if dependency file isn't in project dep file set
    //     print
  }
}
