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

import com.google.cloud.tools.jib.maven.MavenProjectProperties;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import java.io.File;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.FileSet;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.DefaultDependencyResolutionRequest;
import org.apache.maven.project.DependencyResolutionException;
import org.apache.maven.project.DependencyResolutionResult;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectDependenciesResolver;
import org.eclipse.aether.graph.DependencyFilter;

/**
 * Print out changing source dependencies on a module. In multimodule applications it should be run
 * by activating a single module and its dependent modules. Dependency collection will ignore
 * project level snapshots (sub-modules) unless the user has explicitly installed them (by only
 * requiring dependencyCollection). For use only within skaffold.
 *
 * <p>Expected use: "./mvnw jib:_skaffold-files -q" or "./mvnw jib:_skaffold-files -pl module -am
 * -q"
 */
@Mojo(
    name = FilesMojo.GOAL_NAME,
    requiresDependencyCollection = ResolutionScope.COMPILE_PLUS_RUNTIME)
public class FilesMojo extends AbstractMojo {

  @VisibleForTesting static final String GOAL_NAME = "_skaffold-files";

  @Nullable
  @Parameter(defaultValue = "${session}", required = true, readonly = true)
  private MavenSession session;

  @Nullable
  @Parameter(defaultValue = "${project}", required = true, readonly = true)
  private MavenProject project;

  @Nullable
  @Parameter(defaultValue = "${reactorProjects}", required = true, readonly = true)
  private List<MavenProject> projects;

  // TODO: This is internal maven, we should find a better way to do this
  @Nullable @Component private ProjectDependenciesResolver projectDependenciesResolver;

  // This parameter is cloned from JibPluginConfiguration
  @Nullable
  @Parameter(defaultValue = "${project.basedir}/src/main/jib", required = true)
  private File extraDirectory;

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    Preconditions.checkNotNull(project);
    Preconditions.checkNotNull(projects);
    Preconditions.checkNotNull(session);
    Preconditions.checkNotNull(extraDirectory);
    Preconditions.checkNotNull(projectDependenciesResolver);

    // print out pom configuration files
    System.out.println(project.getFile());
    if ("pom".equals(project.getPackaging())) {
      // done if <packaging>pom</packaging>
      return;
    }

    // print out sources directory (resolved by maven to be an absolute path)
    System.out.println(project.getBuild().getSourceDirectory());

    // print out resources directory (resolved by maven to be an absolute path)
    ImmutableSet.copyOf(project.getBuild().getResources())
        .stream()
        .map(FileSet::getDirectory)
        .forEach(System.out::println);

    // this seems weird, but we will only print out the jib "extraFiles" directory on projects where
    // the plugin is explicitly configured (even though _skaffold-files is a jib-maven-plugin goal
    // and is expected to run on all projects irrespective of their configuring of the jib plugin).
    if (project.getPlugin(MavenProjectProperties.PLUGIN_KEY) != null) {
      // print out extra directory
      System.out.println(extraDirectory.getAbsoluteFile().toPath());
    }

    // Grab non-project SNAPSHOT dependencies for this project
    // TODO: this whole sections relies on internal maven API, it could break. We need to explore
    // TODO: better ways to resolve dependencies using the public maven API.
    Set<String> projectArtifacts =
        projects
            .stream()
            .map(MavenProject::getArtifact)
            .map(Artifact::toString)
            .collect(Collectors.toSet());

    DependencyFilter ignoreProjectDependenciesFilter =
        (node, parents) -> {
          if (node == null || node.getDependency() == null) {
            // if nothing, then ignore
            return false;
          }
          if (projectArtifacts.contains(node.getArtifact().toString())) {
            // ignore project dependency artifacts
            return false;
          }
          // we only want compile/runtime deps
          return Artifact.SCOPE_COMPILE_PLUS_RUNTIME.contains(node.getDependency().getScope());
        };

    try {
      DependencyResolutionResult resolutionResult =
          projectDependenciesResolver.resolve(
              new DefaultDependencyResolutionRequest(project, session.getRepositorySession())
                  .setResolutionFilter(ignoreProjectDependenciesFilter));
      resolutionResult
          .getDependencies()
          .stream()
          .map(org.eclipse.aether.graph.Dependency::getArtifact)
          .filter(org.eclipse.aether.artifact.Artifact::isSnapshot)
          .map(org.eclipse.aether.artifact.Artifact::getFile)
          .forEach(System.out::println);

    } catch (DependencyResolutionException ex) {
      throw new MojoExecutionException("Failed to resolve dependencies", ex);
    }
  }
}
