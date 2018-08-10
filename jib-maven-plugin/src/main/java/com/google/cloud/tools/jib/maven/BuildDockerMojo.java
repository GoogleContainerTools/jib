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

import com.google.cloud.tools.jib.cache.CacheDirectoryCreationException;
import com.google.cloud.tools.jib.configuration.BuildConfiguration;
import com.google.cloud.tools.jib.configuration.ImageConfiguration;
import com.google.cloud.tools.jib.docker.DockerClient;
import com.google.cloud.tools.jib.image.ImageReference;
import com.google.cloud.tools.jib.image.InvalidImageReferenceException;
import com.google.cloud.tools.jib.plugins.common.BuildStepsExecutionException;
import com.google.cloud.tools.jib.plugins.common.BuildStepsRunner;
import com.google.cloud.tools.jib.plugins.common.ConfigurationPropertyValidator;
import com.google.cloud.tools.jib.plugins.common.HelpfulSuggestions;
import com.google.common.annotations.VisibleForTesting;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

/** Builds a container image and exports to the default Docker daemon. */
@Mojo(
    name = BuildDockerMojo.GOAL_NAME,
    requiresDependencyResolution = ResolutionScope.RUNTIME_PLUS_SYSTEM)
public class BuildDockerMojo extends JibPluginConfiguration {

  @VisibleForTesting static final String GOAL_NAME = "dockerBuild";

  private static final HelpfulSuggestions HELPFUL_SUGGESTIONS =
      HelpfulSuggestionsProvider.get("Build to Docker daemon failed");

  @Override
  public void execute() throws MojoExecutionException {
    if ("pom".equals(getProject().getPackaging())) {
      getLog().info("Skipping containerization because packaging is 'pom'...");
      return;
    }

    if (!new DockerClient().isDockerInstalled()) {
      throw new MojoExecutionException(HELPFUL_SUGGESTIONS.forDockerNotInstalled());
    }

    MavenJibLogger mavenJibLogger = new MavenJibLogger(getLog());
    MavenProjectProperties mavenProjectProperties =
        MavenProjectProperties.getForProject(getProject(), mavenJibLogger, getExtraDirectory());
    ConfigurationPropertyValidator validator =
        ConfigurationPropertyValidator.newMavenPropertyValidator(mavenJibLogger);
    PluginConfigurationProcessor pluginConfigurationProcessor =
        PluginConfigurationProcessor.processCommonConfiguration(
            mavenJibLogger, validator, this, mavenProjectProperties);

    ImageReference targetImage;
    try {
      targetImage =
          validator.getGeneratedTargetDockerTag(
              getTargetImage(), getProject().getName(), getProject().getVersion());
    } catch (InvalidImageReferenceException ex) {
      throw new MojoExecutionException(ex.getMessage());
    }

    BuildConfiguration buildConfiguration =
        pluginConfigurationProcessor
            .getBuildConfigurationBuilder()
            .setBaseImageConfiguration(
                pluginConfigurationProcessor.getBaseImageConfigurationBuilder().build())
            .setTargetImageConfiguration(ImageConfiguration.builder(targetImage).build())
            .setContainerConfiguration(
                pluginConfigurationProcessor.getContainerConfigurationBuilder().build())
            .build();

    try {
      BuildStepsRunner.forBuildToDockerDaemon(buildConfiguration).build(HELPFUL_SUGGESTIONS);
      getLog().info("");

    } catch (CacheDirectoryCreationException | BuildStepsExecutionException ex) {
      throw new MojoExecutionException(ex.getMessage(), ex.getCause());
    }
  }
}
