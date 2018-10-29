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

package com.google.cloud.tools.jib.maven;

import com.google.cloud.tools.jib.filesystem.AbsoluteUnixPath;
import com.google.cloud.tools.jib.frontend.ExposedPortsParser;
import com.google.cloud.tools.jib.frontend.JavaDockerContextGenerator;
import com.google.cloud.tools.jib.global.JibSystemProperties;
import com.google.cloud.tools.jib.plugins.common.HelpfulSuggestions;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.io.InsecureRecursiveDeleteException;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;
import javax.annotation.Nullable;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

/** Exports to a Docker context. */
@Mojo(
    name = DockerContextMojo.GOAL_NAME,
    requiresDependencyResolution = ResolutionScope.RUNTIME_PLUS_SYSTEM)
public class DockerContextMojo extends JibPluginConfiguration {

  @VisibleForTesting static final String GOAL_NAME = "exportDockerContext";

  @Nullable
  @Parameter(
      property = "jibTargetDir",
      defaultValue = "${project.build.directory}/jib-docker-context",
      required = true)
  @VisibleForTesting
  String targetDir;

  @Override
  public void execute() throws MojoExecutionException {
    if (isSkipped()) {
      getLog().info("Skipping containerization because jib-maven-plugin: skip = true");
      return;
    }
    if ("pom".equals(getProject().getPackaging())) {
      getLog().info("Skipping containerization because packaging is 'pom'...");
      return;
    }

    try {
      JibSystemProperties.checkHttpTimeoutProperty();
    } catch (NumberFormatException ex) {
      throw new MojoExecutionException(ex.getMessage(), ex);
    }

    // TODO: Instead of disabling logging, have authentication credentials be provided
    PluginConfigurationProcessor.disableHttpLogging();

    Preconditions.checkNotNull(targetDir);

    AbsoluteUnixPath appRoot = PluginConfigurationProcessor.getAppRootChecked(this);
    MavenProjectProperties mavenProjectProperties =
        MavenProjectProperties.getForProject(
            getProject(),
            getLog(),
            PluginConfigurationProcessor.getExtraDirectoryPath(this),
            PluginConfigurationProcessor.convertPermissionsList(getExtraDirectoryPermissions()),
            appRoot);

    List<String> entrypoint =
        PluginConfigurationProcessor.computeEntrypoint(getLog(), this, mavenProjectProperties);

    try {
      // Validate port input, but don't save the output because we don't want the ranges expanded
      // here.
      ExposedPortsParser.parse(getExposedPorts());

      new JavaDockerContextGenerator(mavenProjectProperties.getJavaLayerConfigurations())
          .setBaseImage(PluginConfigurationProcessor.getBaseImage(this))
          .setEntrypoint(entrypoint)
          .setProgramArguments(getArgs())
          .setExposedPorts(getExposedPorts())
          .setEnvironment(getEnvironment())
          .setLabels(getLabels())
          .setUser(getUser())
          .generate(Paths.get(targetDir));

      getLog().info("Created Docker context at " + targetDir);

    } catch (InsecureRecursiveDeleteException ex) {
      throw new MojoExecutionException(
          HelpfulSuggestions.forDockerContextInsecureRecursiveDelete(
              "Export Docker context failed because cannot clear directory '"
                  + targetDir
                  + "' safely",
              targetDir),
          ex);

    } catch (IOException ex) {
      throw new MojoExecutionException(
          HelpfulSuggestions.suggest(
              "Export Docker context failed", "check if `targetDir` is set correctly"),
          ex);
    }
  }
}
