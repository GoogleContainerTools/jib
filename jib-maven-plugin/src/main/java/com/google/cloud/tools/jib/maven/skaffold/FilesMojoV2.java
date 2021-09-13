/*
 * Copyright 2019 Google LLC.
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

import com.google.api.client.util.Strings;
import com.google.cloud.tools.jib.maven.MavenProjectProperties;
import com.google.cloud.tools.jib.plugins.common.ConfigurationPropertyValidator;
import com.google.cloud.tools.jib.plugins.common.PropertyNames;
import com.google.cloud.tools.jib.plugins.common.SkaffoldFilesOutput;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.FileSet;
import org.apache.maven.model.Plugin;
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
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.eclipse.aether.graph.DependencyFilter;

/**
 * Print out changing source dependencies on a module. In multimodule applications it should be run
 * by activating a single module and its dependent modules. Dependency collection will ignore
 * project level snapshots (sub-modules) unless the user has explicitly installed them (by only
 * requiring dependencyCollection). For use only within skaffold.
 *
 * <p>Expected use: "./mvnw jib:_skaffold-files-v2 -q" or "./mvnw jib:_skaffold-files-v2 -pl module
 * -am -q"
 */
@Mojo(
    name = FilesMojoV2.GOAL_NAME,
    requiresDependencyCollection = ResolutionScope.COMPILE_PLUS_RUNTIME,
    aggregator = true)
public class FilesMojoV2 extends SkaffoldBindingMojo {

  @VisibleForTesting static final String GOAL_NAME = "_skaffold-files-v2";

  // extracting source directories based on https://kotlinlang.org/docs/reference/using-maven.html
  @VisibleForTesting
  static Set<Path> getKotlinSourceDirectories(MavenProject project) {
    Plugin kotlinPlugin = project.getPlugin("org.jetbrains.kotlin:kotlin-maven-plugin");
    if (kotlinPlugin == null) {
      return Collections.emptySet();
    }

    Path projectBaseDir = project.getBasedir().toPath();

    // Extract <sourceDir> values from <configuration> in the plugin <executions>. Sample:
    // <executions><execution><configuration>
    //   <sourceDirs>
    //     <sourceDir>src/main/kotlin</sourceDir>
    //     <sourceDir>${project.basedir}/src/main/java</sourceDir>
    //   </sourceDirs>
    // </configuration></execution></executions>
    Set<Path> kotlinSourceDirectories =
        kotlinPlugin.getExecutions().stream()
            .filter(execution -> !execution.getGoals().contains("test-compile"))
            .map(execution -> (Xpp3Dom) execution.getConfiguration())
            .filter(Objects::nonNull)
            .map(configuration -> configuration.getChild("sourceDirs"))
            .filter(Objects::nonNull)
            .map(sourceDirs -> Arrays.asList(sourceDirs.getChildren()))
            .flatMap(Collection::stream) // "array of arrays" into "arrays"
            .map(Xpp3Dom::getValue)
            .filter(value -> !Strings.isNullOrEmpty(value))
            .map(Paths::get)
            .map(path -> path.isAbsolute() ? path : projectBaseDir.resolve(path))
            .collect(Collectors.toSet());

    Path conventionalDirectory = projectBaseDir.resolve(Paths.get("src", "main", "kotlin"));
    kotlinSourceDirectories.add(conventionalDirectory);

    return kotlinSourceDirectories;
  }

  @Nullable
  @Parameter(defaultValue = "${session}", required = true, readonly = true)
  private MavenSession session;

  @Nullable
  @Parameter(defaultValue = "${reactorProjects}", required = true, readonly = true)
  private List<MavenProject> projects;

  // TODO: This is internal maven, we should find a better way to do this
  @Nullable @Component private ProjectDependenciesResolver projectDependenciesResolver;

  private final SkaffoldFilesOutput skaffoldFilesOutput = new SkaffoldFilesOutput();

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    Preconditions.checkNotNull(projects);
    Preconditions.checkNotNull(session);
    Preconditions.checkNotNull(projectDependenciesResolver);
    checkJibVersion();

    for (MavenProject project : projects) {
      // Add pom configuration files
      skaffoldFilesOutput.addBuild(project.getFile().toPath());
      if ("pom".equals(project.getPackaging())) {
        // done if <packaging>pom</packaging>
        continue;
      }

      // Add sources directory (resolved by maven to be an absolute path)
      skaffoldFilesOutput.addInput(Paths.get(project.getBuild().getSourceDirectory()));

      for (Path directory : getKotlinSourceDirectories(project)) {
        skaffoldFilesOutput.addInput(directory);
      }

      // Add resources directory (resolved by maven to be an absolute path)
      project.getBuild().getResources().stream()
          .map(FileSet::getDirectory)
          .map(Paths::get)
          .forEach(skaffoldFilesOutput::addInput);

      // This seems weird, but we will only print out the jib "extraFiles" directory on projects
      // where the plugin is explicitly configured (even though _skaffold-files-v2 is a
      // jib-maven-plugin goal and is expected to run on all projects irrespective of their
      // configuring of the jib plugin).
      if (project.getPlugin(MavenProjectProperties.PLUGIN_KEY) != null) {
        // Add extra directory
        resolveExtraDirectories(project).forEach(skaffoldFilesOutput::addInput);
      }

      // See above note on "extraFiles"
      SkaffoldConfiguration.Watch watch = collectWatchParameters(project);
      resolveFiles(watch.buildIncludes, project).forEach(skaffoldFilesOutput::addBuild);
      resolveFiles(watch.includes, project).forEach(skaffoldFilesOutput::addInput);
      // we don't do any special pre-processing for ignore (input and ignore can overlap with exact
      // matches)
      resolveFiles(watch.excludes, project).forEach(skaffoldFilesOutput::addIgnore);

      // Grab non-project SNAPSHOT dependencies for this project
      // TODO: this whole sections relies on internal maven API, it could break. We need to explore
      // TODO: better ways to resolve dependencies using the public maven API.
      Set<String> projectArtifacts =
          projects.stream()
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
        resolutionResult.getDependencies().stream()
            .map(org.eclipse.aether.graph.Dependency::getArtifact)
            .filter(org.eclipse.aether.artifact.Artifact::isSnapshot)
            .map(org.eclipse.aether.artifact.Artifact::getFile)
            .map(File::toPath)
            .forEach(skaffoldFilesOutput::addInput);

      } catch (DependencyResolutionException ex) {
        throw new MojoExecutionException("Failed to resolve dependencies", ex);
      }
    }

    try {
      // Print JSON string
      System.out.println();
      System.out.println("BEGIN JIB JSON");
      System.out.println(skaffoldFilesOutput.getJsonString());
    } catch (IOException ex) {
      throw new MojoExecutionException(ex.getMessage(), ex);
    }
  }

  private List<Path> resolveExtraDirectories(MavenProject project) {
    return collectExtraDirectories(project).stream()
        .map(path -> path.isAbsolute() ? path : project.getBasedir().toPath().resolve(path))
        .collect(Collectors.toList());
  }

  private List<Path> collectExtraDirectories(MavenProject project) {
    // Try getting extra directory from project/session properties
    String property =
        MavenProjectProperties.getProperty(PropertyNames.EXTRA_DIRECTORIES_PATHS, project, session);
    if (property != null) {
      List<String> paths = ConfigurationPropertyValidator.parseListProperty(property);
      return paths.stream().map(Paths::get).collect(Collectors.toList());
    }

    // Try getting extra directory from project pom
    Plugin jibMavenPlugin = project.getPlugin(MavenProjectProperties.PLUGIN_KEY);
    if (jibMavenPlugin != null) {
      Xpp3Dom pluginConfiguration = (Xpp3Dom) jibMavenPlugin.getConfiguration();
      if (pluginConfiguration != null) {
        Xpp3Dom extraDirectoriesConfiguration = pluginConfiguration.getChild("extraDirectories");
        if (extraDirectoriesConfiguration != null) {
          Xpp3Dom paths = extraDirectoriesConfiguration.getChild("paths");
          if (paths != null) {
            // <extraDirectories><paths><path>...</path><path>...</path></paths></extraDirectories>
            // paths can contain either strings or ExtraDirectory objects
            List<Path> pathList = new ArrayList<>();
            for (Xpp3Dom path : paths.getChildren()) {
              Xpp3Dom from = path.getChild("from");
              if (from != null) {
                pathList.add(Paths.get(from.getValue()));
              } else {
                pathList.add(Paths.get(path.getValue()));
              }
            }
            return Collections.unmodifiableList(pathList);
          }
        }
      }
    }

    // Return default if not found
    Path projectBase = Preconditions.checkNotNull(project).getBasedir().getAbsoluteFile().toPath();
    Path srcMainJib = Paths.get("src", "main", "jib");
    return Collections.singletonList(projectBase.resolve(srcMainJib));
  }

  private SkaffoldConfiguration.Watch collectWatchParameters(MavenProject project) {
    // Try getting extra directory from project pom
    SkaffoldConfiguration.Watch watchConfig = new SkaffoldConfiguration.Watch();
    Plugin jibMavenPlugin = project.getPlugin(MavenProjectProperties.PLUGIN_KEY);
    if (jibMavenPlugin != null) {
      Xpp3Dom pluginConfiguration = (Xpp3Dom) jibMavenPlugin.getConfiguration();
      if (pluginConfiguration != null) {
        Xpp3Dom skaffold = pluginConfiguration.getChild("skaffold");
        if (skaffold != null) {
          Xpp3Dom watch = skaffold.getChild("watch");
          if (watch != null) {
            Xpp3Dom buildIncludes = watch.getChild("buildIncludes");
            if (buildIncludes != null) {
              watchConfig.buildIncludes = xpp3ToList(buildIncludes, File::new);
            }
            Xpp3Dom includes = watch.getChild("includes");
            if (includes != null) {
              watchConfig.includes = xpp3ToList(includes, File::new);
            }
            Xpp3Dom excludes = watch.getChild("excludes");
            if (excludes != null) {
              watchConfig.excludes = xpp3ToList(excludes, File::new);
            }
          }
        }
      }
    }
    return watchConfig;
  }

  private List<Path> resolveFiles(List<File> files, MavenProject project) {
    return files.stream()
        .map(File::toPath)
        .map(path -> path.isAbsolute() ? path : project.getBasedir().toPath().resolve(path))
        .collect(Collectors.toList());
  }

  private <T> List<T> xpp3ToList(Xpp3Dom node, Function<String, T> converter) {
    Preconditions.checkNotNull(node);
    return Arrays.stream(node.getChildren())
        .map(Xpp3Dom::getValue)
        .map(converter)
        .collect(Collectors.toList());
  }
}
