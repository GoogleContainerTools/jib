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
import com.google.cloud.tools.jib.api.JibContainerBuilder;
import com.google.cloud.tools.jib.api.TarImage;
import com.google.cloud.tools.jib.configuration.CacheDirectoryCreationException;
import com.google.cloud.tools.jib.event.DefaultEventDispatcher;
import com.google.cloud.tools.jib.event.EventDispatcher;
import com.google.cloud.tools.jib.filesystem.AbsoluteUnixPath;
import com.google.cloud.tools.jib.image.ImageReference;
import com.google.cloud.tools.jib.image.InvalidImageReferenceException;
import com.google.cloud.tools.jib.plugins.common.AppRootInvalidException;
import com.google.cloud.tools.jib.plugins.common.BuildStepsExecutionException;
import com.google.cloud.tools.jib.plugins.common.BuildStepsRunner;
import com.google.cloud.tools.jib.plugins.common.ConfigurationPropertyValidator;
import com.google.cloud.tools.jib.plugins.common.HelpfulSuggestions;
import com.google.cloud.tools.jib.plugins.common.InferredAuthRetrievalException;
import com.google.cloud.tools.jib.plugins.common.MainClassInferenceException;
import com.google.cloud.tools.jib.plugins.common.PluginConfigurationProcessor;
import com.google.cloud.tools.jib.plugins.common.RawConfiguration;
import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.nio.file.Path;
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

    MojoCommon.disableHttpLogging();
    try {
      AbsoluteUnixPath appRoot = MojoCommon.getAppRootChecked(this);
      MavenProjectProperties projectProperties =
          MavenProjectProperties.getForProject(
              getProject(),
              getLog(),
              MojoCommon.getExtraDirectoryPath(this),
              MojoCommon.convertPermissionsList(getExtraDirectoryPermissions()),
              appRoot);
      EventDispatcher eventDispatcher =
          new DefaultEventDispatcher(projectProperties.getEventHandlers());
      RawConfiguration rawConfiguration = new MavenRawConfiguration(this, eventDispatcher);

      MavenHelpfulSuggestionsBuilder mavenHelpfulSuggestionsBuilder =
          new MavenHelpfulSuggestionsBuilder(HELPFUL_SUGGESTIONS_PREFIX, this);

      ImageReference targetImageReference =
          ConfigurationPropertyValidator.getGeneratedTargetDockerTag(
              getTargetImage(),
              eventDispatcher,
              getProject().getName(),
              getProject().getVersion(),
              mavenHelpfulSuggestionsBuilder.build());
      Path buildOutput = Paths.get(getProject().getBuild().getDirectory());
      Path tarOutputPath = buildOutput.resolve("jib-image.tar");
      TarImage targetImage = TarImage.named(targetImageReference).saveTo(tarOutputPath);

      PluginConfigurationProcessor pluginConfigurationProcessor =
          PluginConfigurationProcessor.processCommonConfiguration(
              rawConfiguration, projectProperties);

      JibContainerBuilder jibContainerBuilder =
          pluginConfigurationProcessor.getJibContainerBuilder();
      Containerizer containerizer = Containerizer.to(targetImage);
      PluginConfigurationProcessor.configureContainerizer(
          containerizer, rawConfiguration, projectProperties, MavenProjectProperties.TOOL_NAME);

      HelpfulSuggestions helpfulSuggestions =
          mavenHelpfulSuggestionsBuilder
              .setBaseImageReference(pluginConfigurationProcessor.getBaseImageReference())
              .setBaseImageHasConfiguredCredentials(
                  pluginConfigurationProcessor.isBaseImageCredentialPresent())
              .setTargetImageReference(targetImageReference)
              .build();

      BuildStepsRunner.forBuildTar(tarOutputPath)
          .writeImageDigest(buildOutput.resolve("jib-image.digest"))
          .build(
              jibContainerBuilder,
              containerizer,
              eventDispatcher,
              projectProperties.getJavaLayerConfigurations().getLayerConfigurations(),
              helpfulSuggestions);
      getLog().info("");

    } catch (AppRootInvalidException ex) {
      throw new MojoExecutionException(
          "<container><appRoot> is not an absolute Unix-style path: " + ex.getInvalidAppRoot());

    } catch (InvalidImageReferenceException
        | IOException
        | CacheDirectoryCreationException
        | MainClassInferenceException
        | InferredAuthRetrievalException ex) {
      throw new MojoExecutionException(ex.getMessage(), ex);

    } catch (BuildStepsExecutionException ex) {
      throw new MojoExecutionException(ex.getMessage(), ex.getCause());
    }
  }
}
