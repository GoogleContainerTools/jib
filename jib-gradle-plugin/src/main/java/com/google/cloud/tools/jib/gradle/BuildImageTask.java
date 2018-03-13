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

import org.gradle.api.DefaultTask;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.TaskAction;

public class BuildImageTask extends DefaultTask {

  @TaskAction
  void buildImage() {
    JavaPluginConvention javaPluginConvention =
        getProject().getConvention().getPlugin(JavaPluginConvention.class);
    javaPluginConvention
        .getSourceSets()
        .forEach(
            sourceSet -> {
              System.err.println("SS: " + sourceSet.getName());
              sourceSet
                  .getOutput()
                  .getClassesDirs()
                  .forEach(classesDir -> System.err.println("\tCD: " + classesDir));
              System.err.println("\tRD: " + sourceSet.getOutput().getResourcesDir());
              sourceSet.getOutput().getDirs().forEach(file -> System.err.println("\tD: " + file));

              sourceSet.getRuntimeClasspath().forEach(file -> System.err.println("\tRCP: " + file));
            });

    javaPluginConvention.manifest(
        manifest ->
            manifest
                .getAttributes()
                .forEach((key, object) -> System.err.println("MMC: " + key + "=" + object)));

    getProject()
        .getConfigurations()
        .getByName(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME)
        .getResolvedConfiguration()
        .getResolvedArtifacts()
        .forEach(resolvedArtifact -> System.err.println("PA: " + resolvedArtifact.getFile()));
  }
}
