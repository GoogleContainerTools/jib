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

import com.google.cloud.tools.jib.cache.CacheDirectoryCreationException;
import com.google.cloud.tools.jib.configuration.BuildConfiguration;
import com.google.cloud.tools.jib.configuration.ImageConfiguration;
import com.google.cloud.tools.jib.image.ImageReference;
import com.google.cloud.tools.jib.image.InvalidImageReferenceException;
import com.google.cloud.tools.jib.plugins.common.BuildStepsExecutionException;
import com.google.cloud.tools.jib.plugins.common.BuildStepsRunner;
import com.google.cloud.tools.jib.plugins.common.ConfigurationPropertyValidator;
import com.google.cloud.tools.jib.plugins.common.HelpfulSuggestions;
import com.google.common.annotations.VisibleForTesting;
import java.nio.file.Paths;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

/**
 * Builds a container image and exports to disk at {@code ${project.build.directory}/jib-image.tar}.
 */
@Mojo(
    name = BuildTarMojo.GOAL_NAME,
    requiresDependencyResolution = ResolutionScope.RUNTIME_PLUS_SYSTEM)
public class BuildTarMojo extends JibPluginConfiguration {

  @VisibleForTesting static final String GOAL_NAME = "buildTar";

  private static final String HELPFUL_SUGGESTIONS_PREFIX = "Building image tarball failed";

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

    MavenJibLogger mavenJibLogger = new MavenJibLogger(getLog());
    MavenProjectProperties mavenProjectProperties =
        MavenProjectProperties.getForProject(getProject(), mavenJibLogger, getExtraDirectory());

    try {
      MavenHelpfulSuggestionsBuilder mavenHelpfulSuggestionsBuilder =
          new MavenHelpfulSuggestionsBuilder(HELPFUL_SUGGESTIONS_PREFIX, this);

      ImageReference targetImage =
          ConfigurationPropertyValidator.getGeneratedTargetDockerTag(
              getTargetImage(),
              mavenJibLogger,
              getProject().getName(),
              getProject().getVersion(),
              mavenHelpfulSuggestionsBuilder.build());

      PluginConfigurationProcessor pluginConfigurationProcessor =
          PluginConfigurationProcessor.processCommonConfiguration(
              mavenJibLogger, this, mavenProjectProperties);

      BuildConfiguration buildConfiguration =
          pluginConfigurationProcessor
              .getBuildConfigurationBuilder()
              .setBaseImageConfiguration(
                  pluginConfigurationProcessor.getBaseImageConfigurationBuilder().build())
              .setTargetImageConfiguration(ImageConfiguration.builder(targetImage).build())
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

      BuildStepsRunner.forBuildTar(
              Paths.get(getProject().getBuild().getDirectory()).resolve("jib-image.tar"),
              buildConfiguration)
          .build(helpfulSuggestions);
      getLog().info("");

    } catch (CacheDirectoryCreationException
        | BuildStepsExecutionException
        | InvalidImageReferenceException ex) {
      throw new MojoExecutionException(ex.getMessage(), ex.getCause());
    }
  }
}
