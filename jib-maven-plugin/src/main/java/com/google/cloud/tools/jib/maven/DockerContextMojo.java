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
import com.google.cloud.tools.jib.docker.DockerContextGenerator;
import com.google.common.base.Preconditions;
import com.google.common.io.InsecureRecursiveDeleteException;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

/** Exports to a Docker context. This is an <b>incubating</b> feature. */
@Mojo(name = "dockercontext", requiresDependencyResolution = ResolutionScope.RUNTIME_PLUS_SYSTEM)
public class DockerContextMojo extends AbstractMojo {

  @Nullable
  @Parameter(defaultValue = "${project}", readonly = true)
  private MavenProject project;

  @Nullable
  @Parameter(
    property = "jib.dockerDir",
    defaultValue = "${project.build.directory}/jib-dockercontext",
    required = true
  )
  private String targetDir;

  @Nullable
  @Parameter(defaultValue = "gcr.io/distroless/java", required = true)
  private String from;

  @Parameter private List<String> jvmFlags = Collections.emptyList();

  @Nullable @Parameter private Map<String, String> environment;

  @Nullable @Parameter private String mainClass;

  @Override
  public void execute() throws MojoExecutionException {
    // These @Nullable parameters should never be actually null.
    Preconditions.checkNotNull(project);
    Preconditions.checkNotNull(targetDir);
    Preconditions.checkNotNull(from);

    ProjectProperties projectProperties = new ProjectProperties(project, getLog());

    // TODO: Refactor with BuildImageMojo.
    if (mainClass == null) {
      mainClass = projectProperties.getMainClassFromMavenJarPlugin();
      if (mainClass == null) {
        throwMojoExecutionExceptionWithHelpMessage(
            new MojoFailureException("Could not find main class specified in maven-jar-plugin"),
            "add a `mainClass` configuration to jib-maven-plugin");
      }
    }
    Preconditions.checkNotNull(mainClass);
    if (!BuildConfiguration.isValidJavaClass(mainClass)) {
      getLog().warn("'mainClass' is not a valid Java class : " + mainClass);
    }

    try {
      new DockerContextGenerator(projectProperties.getSourceFilesConfiguration())
          .setBaseImage(from)
          .setJvmFlags(jvmFlags)
          .setMainClass(mainClass)
          .generate(Paths.get(targetDir));

      getLog().info("Created Docker context at " + targetDir);

    } catch (InsecureRecursiveDeleteException ex) {
      throwMojoExecutionExceptionWithHelpMessage(
          ex,
          "cannot clear directory '"
              + targetDir
              + "' safely - clear it manually before creating the Docker context");

    } catch (IOException ex) {
      throwMojoExecutionExceptionWithHelpMessage(ex, "check if `targetDir` is set correctly");
    }
  }

  /**
   * Wraps an exception in a {@link MojoExecutionException} and provides a suggestion on how to fix
   * the error.
   */
  private <T extends Throwable> void throwMojoExecutionExceptionWithHelpMessage(
      T ex, @Nullable String suggestion) throws MojoExecutionException {
    StringBuilder message = new StringBuilder("Export Docker context failed");
    if (suggestion != null) {
      message.append(", perhaps you should ");
      message.append(suggestion);
    }
    throw new MojoExecutionException(message.toString(), ex);
  }
}
