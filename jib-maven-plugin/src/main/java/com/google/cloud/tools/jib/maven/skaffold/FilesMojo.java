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

package com.google.cloud.tools.jib.maven.skaffold;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import java.io.File;
import java.nio.file.Path;
import java.util.Objects;
import javax.annotation.Nullable;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

/**
 * Print out changing source dependencies on a module. In multimodule applications it should be run
 * by activating a single module and it's dependent modules. Dependency collection will ignore
 * project level snapshots (sub-modules) unless the user has explicitly installed them (by only
 * requiring dependencyCollection). For use only within skaffold.
 *
 * <p>Expected use: "./mvnw jib:_skaffold-files" or "./mvnw jib:_skaffold-files -pl module -am"
 */
@Mojo(
    name = FilesMojo.GOAL_NAME,
    requiresDependencyCollection = ResolutionScope.COMPILE_PLUS_RUNTIME)
public class FilesMojo extends AbstractMojo {

  @VisibleForTesting static final String GOAL_NAME = "_skaffold-files";

  @Nullable
  @Parameter(defaultValue = "${project}", readonly = true)
  private MavenProject project;

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    Preconditions.checkNotNull(project);

    // print out pom configuration files
    System.out.println(project.getFile());
    if ("pom".equals(project.getPackaging())) {
      // done if <packaging>pom</packaging>
      return;
    }

    // print out sources directory
    System.out.println(project.getBuild().getSourceDirectory());

    // print out all SNAPSHOT, non-project artifacts
    project
        .getArtifacts()
        .stream()
        .filter(Artifact::isSnapshot)
        .map(Artifact::getFile)
        .filter(Objects::nonNull)
        .map(File::toPath)
        .map(Path::toAbsolutePath)
        .map(Path::toString)
        .forEach(System.out::println);
  }
}
