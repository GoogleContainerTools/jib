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

import com.google.cloud.tools.jib.configuration.BuildConfiguration;
import com.google.cloud.tools.jib.configuration.CacheDirectoryCreationException;
import com.google.cloud.tools.jib.configuration.ImageConfiguration;
import com.google.cloud.tools.jib.docker.DockerClient;
import com.google.cloud.tools.jib.filesystem.AbsoluteUnixPath;
import com.google.cloud.tools.jib.image.ImageReference;
import com.google.cloud.tools.jib.image.InvalidImageReferenceException;
import com.google.cloud.tools.jib.plugins.common.BuildStepsExecutionException;
import com.google.cloud.tools.jib.plugins.common.BuildStepsRunner;
import com.google.cloud.tools.jib.plugins.common.ConfigurationPropertyValidator;
import com.google.cloud.tools.jib.plugins.common.HelpfulSuggestions;
import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

/** Builds a container image and exports to the default Docker daemon. */
@Mojo(
    name = BuildDockerMojo.GOAL_NAME,
    requiresDependencyResolution = ResolutionScope.RUNTIME_PLUS_SYSTEM)
public class BuildDockerMojo extends JibPluginConfiguration {

  @VisibleForTesting static final String GOAL_NAME = "dockerBuild";

  private static final String HELPFUL_SUGGESTIONS_PREFIX = "Build to Docker daemon failed";

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

    if (!new DockerClient().isDockerInstalled()) {
      throw new MojoExecutionException(
          HelpfulSuggestions.forDockerNotInstalled(HELPFUL_SUGGESTIONS_PREFIX));
    }

    AbsoluteUnixPath appRoot = PluginConfigurationProcessor.getAppRootChecked(this);
    MavenProjectProperties mavenProjectProperties =
        MavenProjectProperties.getForProject(getProject(), getLog(), getExtraDirectory(), appRoot);

    try {
      MavenHelpfulSuggestionsBuilder mavenHelpfulSuggestionsBuilder =
          new MavenHelpfulSuggestionsBuilder(HELPFUL_SUGGESTIONS_PREFIX, this);

      ImageReference targetImage =
          ConfigurationPropertyValidator.getGeneratedTargetDockerTag(
              getTargetImage(),
              mavenProjectProperties.getEventDispatcher(),
              getProject().getName(),
              getProject().getVersion(),
              mavenHelpfulSuggestionsBuilder.build());

      PluginConfigurationProcessor pluginConfigurationProcessor =
          PluginConfigurationProcessor.processCommonConfiguration(
              getLog(), this, mavenProjectProperties);

      BuildConfiguration buildConfiguration =
          pluginConfigurationProcessor
              .getBuildConfigurationBuilder()
              .setBaseImageConfiguration(
                  pluginConfigurationProcessor.getBaseImageConfigurationBuilder().build())
              .setTargetImageConfiguration(ImageConfiguration.builder(targetImage).build())
              .setAdditionalTargetImageTags(getTargetImageAdditionalTags())
              .setContainerConfiguration(
                  pluginConfigurationProcessor.getContainerConfigurationBuilder().build())
              .build();

      HelpfulSuggestions helpfulSuggestions =
          mavenHelpfulSuggestionsBuilder
              .setBaseImageReference(buildConfiguration.getBaseImageConfiguration().getImage())
              .setBaseImageHasConfiguredCredentials(
                  pluginConfigurationProcessor.isBaseImageCredentialPresent())
              .setTargetImageReference(buildConfiguration.getTargetImageConfiguration().getImage())
              .build();

      BuildStepsRunner.forBuildToDockerDaemon(buildConfiguration).build(helpfulSuggestions);
      getLog().info("");

    } catch (CacheDirectoryCreationException | InvalidImageReferenceException | IOException ex) {
      throw new MojoExecutionException(ex.getMessage(), ex);

    } catch (BuildStepsExecutionException ex) {
      throw new MojoExecutionException(ex.getMessage(), ex.getCause());
    }
  }
}
