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

import com.google.cloud.tools.jib.api.Containerizer;
import com.google.cloud.tools.jib.api.DockerDaemonImage;
import com.google.cloud.tools.jib.api.JibContainerBuilder;
import com.google.cloud.tools.jib.configuration.CacheDirectoryCreationException;
import com.google.cloud.tools.jib.docker.DockerClient;
import com.google.cloud.tools.jib.event.DefaultEventDispatcher;
import com.google.cloud.tools.jib.event.EventDispatcher;
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

  private static final DockerClient DOCKER_CLIENT = DockerClient.newClient();

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

    if (!DOCKER_CLIENT.isDockerInstalled()) {
      throw new MojoExecutionException(
          HelpfulSuggestions.forDockerNotInstalled(HELPFUL_SUGGESTIONS_PREFIX));
    }

    AbsoluteUnixPath appRoot = PluginConfigurationProcessor.getAppRootChecked(this);
    MavenProjectProperties mavenProjectProperties =
        MavenProjectProperties.getForProject(getProject(), getLog(), getExtraDirectory(), appRoot);

    try {
      MavenHelpfulSuggestionsBuilder mavenHelpfulSuggestionsBuilder =
          new MavenHelpfulSuggestionsBuilder(HELPFUL_SUGGESTIONS_PREFIX, this);

      EventDispatcher eventDispatcher =
          new DefaultEventDispatcher(mavenProjectProperties.getEventHandlers());
      ImageReference targetImageReference =
          ConfigurationPropertyValidator.getGeneratedTargetDockerTag(
              getTargetImage(),
              eventDispatcher,
              getProject().getName(),
              getProject().getVersion(),
              mavenHelpfulSuggestionsBuilder.build());
      DockerDaemonImage targetImage = DockerDaemonImage.named(targetImageReference);

      PluginConfigurationProcessor pluginConfigurationProcessor =
          PluginConfigurationProcessor.processCommonConfiguration(
              getLog(), this, mavenProjectProperties);

      JibContainerBuilder jibContainerBuilder =
          pluginConfigurationProcessor.getJibContainerBuilder();
      Containerizer containerizer = Containerizer.to(targetImage);
      PluginConfigurationProcessor.configureContainerizer(
          containerizer, this, mavenProjectProperties);

      HelpfulSuggestions helpfulSuggestions =
          mavenHelpfulSuggestionsBuilder
              .setBaseImageReference(pluginConfigurationProcessor.getBaseImageReference())
              .setBaseImageHasConfiguredCredentials(
                  pluginConfigurationProcessor.isBaseImageCredentialPresent())
              .setTargetImageReference(targetImageReference)
              .build();

      BuildStepsRunner.forBuildToDockerDaemon(targetImageReference, getTargetImageAdditionalTags())
          .build(
              jibContainerBuilder,
              containerizer,
              eventDispatcher,
              mavenProjectProperties.getJavaLayerConfigurations().getLayerConfigurations(),
              helpfulSuggestions);
      getLog().info("");

    } catch (InvalidImageReferenceException | IOException | CacheDirectoryCreationException ex) {
      throw new MojoExecutionException(ex.getMessage(), ex);

    } catch (BuildStepsExecutionException ex) {
      throw new MojoExecutionException(ex.getMessage(), ex.getCause());
    }
  }
}
