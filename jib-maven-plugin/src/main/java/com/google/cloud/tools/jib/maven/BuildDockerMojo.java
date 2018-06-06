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
import com.google.cloud.tools.jib.docker.DockerClient;
import com.google.cloud.tools.jib.frontend.BuildStepsExecutionException;
import com.google.cloud.tools.jib.frontend.BuildStepsRunner;
import com.google.cloud.tools.jib.frontend.CacheDirectoryCreationException;
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
  requiresDependencyResolution = ResolutionScope.RUNTIME_PLUS_SYSTEM
)
public class BuildDockerMojo extends JibPluginConfiguration {

  @VisibleForTesting static final String GOAL_NAME = "dockerBuild";

  /** {@code User-Agent} header suffix to send to the registry. */
  private static final String USER_AGENT_SUFFIX = "jib-maven-plugin";

  private static final HelpfulSuggestions HELPFUL_SUGGESTIONS =
      HelpfulSuggestionsProvider.get("Build to Docker daemon failed");

  @Override
  public void execute() throws MojoExecutionException {
    if (!new DockerClient().isDockerInstalled()) {
      throw new MojoExecutionException(HELPFUL_SUGGESTIONS.forDockerNotInstalled());
    }

    // Parses 'from' and 'to' into image reference.
    ImageReference baseImage = parseBaseImageReference(getBaseImage());

    // TODO: Validate that project name and version are valid repository/tag
    ImageReference targetImage =
        Strings.isNullOrEmpty(getTargetImage())
            ? ImageReference.of(null, getProject().getName(), getProject().getVersion())
            : parseTargetImageReference(getTargetImage());

    // Checks Maven settings for registry credentials.
    MavenSettingsServerCredentials mavenSettingsServerCredentials =
        new MavenSettingsServerCredentials(Preconditions.checkNotNull(session).getSettings());
    RegistryCredentials knownBaseRegistryCredentials =
        mavenSettingsServerCredentials.retrieve(baseImage.getRegistry());

    MavenBuildLogger mavenBuildLogger = new MavenBuildLogger(getLog());
    MavenProjectProperties mavenProjectProperties =
        MavenProjectProperties.getForProject(getProject(), mavenBuildLogger);
    String mainClass = mavenProjectProperties.getMainClass(this);

    BuildConfiguration buildConfiguration =
        BuildConfiguration.builder(mavenBuildLogger)
            .setBaseImage(baseImage)
            .setBaseImageCredentialHelperName(getBaseImageCredentialHelperName())
            .setKnownBaseRegistryCredentials(knownBaseRegistryCredentials)
            .setTargetImage(targetImage)
            .setMainClass(mainClass)
            .setJavaArguments(getArgs())
            .setJvmFlags(getJvmFlags())
            .setEnvironment(getEnvironment())
            .build();

    // TODO: Instead of disabling logging, have authentication credentials be provided
    MavenProjectProperties.disableHttpLogging();

    RegistryClient.setUserAgentSuffix(USER_AGENT_SUFFIX);

    try {
      BuildStepsRunner.forBuildToDockerDaemon(
              buildConfiguration,
              mavenProjectProperties.getSourceFilesConfiguration(),
              mavenProjectProperties.getCacheDirectory(),
              getUseOnlyProjectCache())
          .build(HELPFUL_SUGGESTIONS);
      getLog().info("");

    } catch (CacheDirectoryCreationException | BuildStepsExecutionException ex) {
      throw new MojoExecutionException(ex.getMessage(), ex.getCause());
    }
  }
}
