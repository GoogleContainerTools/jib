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
import com.google.cloud.tools.jib.cache.CacheDirectoryCreationException;
import com.google.cloud.tools.jib.configuration.CacheConfiguration;
import com.google.cloud.tools.jib.docker.DockerClient;
import com.google.cloud.tools.jib.frontend.BuildStepsExecutionException;
import com.google.cloud.tools.jib.frontend.BuildStepsRunner;
import com.google.cloud.tools.jib.frontend.ExposedPortsParser;
import com.google.cloud.tools.jib.frontend.HelpfulSuggestions;
import com.google.cloud.tools.jib.image.ImageReference;
import com.google.cloud.tools.jib.registry.RegistryClient;
import com.google.cloud.tools.jib.registry.credentials.RegistryCredentials;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

/** Builds a container image and exports to the default Docker daemon. */
@Mojo(
    name = BuildDockerMojo.GOAL_NAME,
    requiresDependencyResolution = ResolutionScope.RUNTIME_PLUS_SYSTEM)
public class BuildDockerMojo extends JibPluginConfiguration {

  @VisibleForTesting static final String GOAL_NAME = "dockerBuild";

  /** {@code User-Agent} header suffix to send to the registry. */
  private static final String USER_AGENT_SUFFIX = "jib-maven-plugin";

  private static final HelpfulSuggestions HELPFUL_SUGGESTIONS =
      HelpfulSuggestionsProvider.get("Build to Docker daemon failed");

  @Override
  public void execute() throws MojoExecutionException {
    MavenBuildLogger mavenBuildLogger = new MavenBuildLogger(getLog());
    handleDeprecatedParameters(mavenBuildLogger);

    if (!new DockerClient().isDockerInstalled()) {
      throw new MojoExecutionException(HELPFUL_SUGGESTIONS.forDockerNotInstalled());
    }

    // Parses 'from' and 'to' into image reference.
    ImageReference baseImage = parseImageReference(getBaseImage(), "from");
    ImageReference targetImage = getDockerTag(mavenBuildLogger);

    // Checks Maven settings for registry credentials.
    MavenSettingsServerCredentials mavenSettingsServerCredentials =
        new MavenSettingsServerCredentials(Preconditions.checkNotNull(session).getSettings());
    RegistryCredentials knownBaseRegistryCredentials =
        mavenSettingsServerCredentials.retrieve(baseImage.getRegistry());

    MavenProjectProperties mavenProjectProperties =
        MavenProjectProperties.getForProject(getProject(), mavenBuildLogger);
    String mainClass = mavenProjectProperties.getMainClass(this);

    // Builds the BuildConfiguration.
    // TODO: Consolidate with BuildImageMojo.
    BuildConfiguration.Builder buildConfigurationBuilder =
        BuildConfiguration.builder(mavenBuildLogger)
            .setBaseImage(baseImage)
            .setBaseImageCredentialHelperName(getBaseImageCredentialHelperName())
            .setKnownBaseRegistryCredentials(knownBaseRegistryCredentials)
            .setTargetImage(targetImage)
            .setMainClass(mainClass)
            .setJavaArguments(getArgs())
            .setJvmFlags(getJvmFlags())
            .setEnvironment(getEnvironment())
            .setExposedPorts(ExposedPortsParser.parse(getExposedPorts(), mavenBuildLogger))
            .setAllowHttp(getAllowInsecureRegistries());
    CacheConfiguration applicationLayersCacheConfiguration =
        CacheConfiguration.forPath(mavenProjectProperties.getCacheDirectory());
    buildConfigurationBuilder.setApplicationLayersCacheConfiguration(
        applicationLayersCacheConfiguration);
    if (getUseOnlyProjectCache()) {
      buildConfigurationBuilder.setBaseImageLayersCacheConfiguration(
          applicationLayersCacheConfiguration);
    }
    BuildConfiguration buildConfiguration = buildConfigurationBuilder.build();

    // TODO: Instead of disabling logging, have authentication credentials be provided
    MavenBuildLogger.disableHttpLogging();

    RegistryClient.setUserAgentSuffix(USER_AGENT_SUFFIX);

    try {
      BuildStepsRunner.forBuildToDockerDaemon(
              buildConfiguration, mavenProjectProperties.getSourceFilesConfiguration())
          .build(HELPFUL_SUGGESTIONS);
      getLog().info("");

    } catch (CacheDirectoryCreationException | BuildStepsExecutionException ex) {
      throw new MojoExecutionException(ex.getMessage(), ex.getCause());
    }
  }

  /**
   * Returns an {@link ImageReference} parsed from the configured target image, or one of the form
   * {@code project-name:project-version} if target image is not configured
   *
   * @param mavenBuildLogger the logger used to notify users of the target image parameter
   * @return an {@link ImageReference} parsed from the configured target image, or one of the form
   *     {@code project-name:project-version} if target image is not configured
   */
  ImageReference getDockerTag(MavenBuildLogger mavenBuildLogger) {
    if (Strings.isNullOrEmpty(getTargetImage())) {
      // TODO: Validate that project name and version are valid repository/tag
      // TODO: Use HelpfulSuggestions
      mavenBuildLogger.lifecycle(
          "Tagging image with generated image reference "
              + getProject().getName()
              + ":"
              + getProject().getVersion()
              + ". If you'd like to specify a different tag, you can set the <to><image> parameter "
              + "in your pom.xml, or use the -Dimage=<MY IMAGE> commandline flag.");
      return ImageReference.of(null, getProject().getName(), getProject().getVersion());
    } else {
      return parseImageReference(getTargetImage(), "to");
    }
  }
}
