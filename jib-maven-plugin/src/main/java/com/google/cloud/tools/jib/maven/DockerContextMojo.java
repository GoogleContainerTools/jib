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

import com.google.cloud.tools.jib.docker.DockerContextGenerator;
import com.google.cloud.tools.jib.frontend.MainClassFinder;
import com.google.common.base.Preconditions;
import com.google.common.io.InsecureRecursiveDeleteException;
import java.io.IOException;
import java.nio.file.Paths;
import javax.annotation.Nullable;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

/** Exports to a Docker context. This is an <b>incubating</b> feature. */
@Mojo(name = "dockercontext", requiresDependencyResolution = ResolutionScope.RUNTIME_PLUS_SYSTEM)
public class DockerContextMojo extends JibPluginConfiguration {

  @Nullable
  @Parameter(
    property = "jib.dockerDir",
    defaultValue = "${project.build.directory}/jib-dockercontext",
    required = true
  )
  private String targetDir;

  @Override
  public void execute() throws MojoExecutionException {
    Preconditions.checkNotNull(targetDir);

    MavenBuildLogger mavenBuildLogger = new MavenBuildLogger(getLog());
    MavenProjectProperties mavenProjectProperties =
        MavenProjectProperties.getForProject(getProject(), mavenBuildLogger);

    try {
      new DockerContextGenerator(mavenProjectProperties.getSourceFilesConfiguration())
          .setBaseImage(getBaseImage())
          .setJvmFlags(getJvmFlags())
          .setMainClass(MainClassFinder.resolveMainClass(getMainClass(), mavenProjectProperties))
          .generate(Paths.get(targetDir));

      mavenBuildLogger.info("Created Docker context at " + targetDir);

    } catch (InsecureRecursiveDeleteException ex) {
      throw new MojoExecutionException(
          HelpfulSuggestionsProvider.get(
                  "Export Docker context failed because cannot clear directory '"
                      + targetDir
                      + "' safely")
              .forDockerContextInsecureRecursiveDelete(targetDir),
          ex);

    } catch (IOException ex) {
      throw new MojoExecutionException(
          HelpfulSuggestionsProvider.get("Export Docker context failed")
              .suggest("check if `targetDir` is set correctly"),
          ex);
    }
  }
}
