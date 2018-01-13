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

import java.nio.file.Path;
import java.nio.file.Paths;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

/** Says "Hi" to the user. */
@Mojo(name = "sayhi")
public class GreetingMojo extends AbstractMojo {

  @Parameter(defaultValue = "${project}", readonly = true)
  private MavenProject project;

  @Override
  public void execute() throws MojoExecutionException {
    // Gets the dependencies.

  }

  @Deprecated
  private void projectVals() {
    System.out.println("afdsfsda");
    getLog().info("Hello world");

    getLog().info("Source:");
    Path sourceDir = Paths.get(project.getBuild().getSourceDirectory());
    getLog().info(sourceDir.toString());

    getLog().info("Classes:");
    Path classesDir = Paths.get(project.getBuild().getOutputDirectory());
    getLog().info(classesDir.toString());

    getLog().info("Resources:");
    project
        .getResources()
        .forEach(resource -> getLog().info("Resource: " + resource.getDirectory()));

    getLog().info("Artifacts:");
    project.getArtifacts().forEach(artifact -> getLog().info(artifact.getFile().toString()));

    getLog().info("Dependencies:");
    project.getDependencies().forEach(dependency -> getLog().info(dependency.getSystemPath()));

    getLog().info("Compile classpath:");
    try {
      project.getCompileClasspathElements().forEach(classpath -> getLog().info(classpath));
    } catch (DependencyResolutionRequiredException ex) {
      throw new MojoExecutionException("", ex);
    }

    getLog().info("Runtime classpath:");
    try {
      project.getRuntimeClasspathElements().forEach(classpath -> getLog().info(classpath));
    } catch (DependencyResolutionRequiredException ex) {
      throw new MojoExecutionException("", ex);
    }

    throw new MojoExecutionException("WHAT");
  }
}
