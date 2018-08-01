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
import com.google.cloud.tools.jib.configuration.CacheConfiguration;
import com.google.cloud.tools.jib.configuration.ContainerConfiguration;
import com.google.cloud.tools.jib.configuration.ImageConfiguration;
import com.google.cloud.tools.jib.docker.DockerClient;
import com.google.cloud.tools.jib.frontend.BuildStepsExecutionException;
import com.google.cloud.tools.jib.frontend.BuildStepsRunner;
import com.google.cloud.tools.jib.frontend.ExposedPortsParser;
import com.google.cloud.tools.jib.frontend.HelpfulSuggestions;
import com.google.cloud.tools.jib.frontend.JavaEntrypointConstructor;
import com.google.cloud.tools.jib.frontend.SystemPropertyValidator;
import com.google.cloud.tools.jib.http.Authorization;
import com.google.cloud.tools.jib.image.ImageReference;
import com.google.cloud.tools.jib.registry.RegistryClient;
import com.google.cloud.tools.jib.registry.credentials.RegistryCredentials;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import java.time.Instant;
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
    if ("pom".equals(getProject().getPackaging())) {
      getLog().info("Skipping containerization because packaging is 'pom'...");
      return;
    }

    MavenJibLogger mavenJibLogger = new MavenJibLogger(getLog());
    handleDeprecatedParameters(mavenJibLogger);
    SystemPropertyValidator.checkHttpTimeoutProperty(MojoExecutionException::new);

    if (!new DockerClient().isDockerInstalled()) {
      throw new MojoExecutionException(HELPFUL_SUGGESTIONS.forDockerNotInstalled());
    }

    // Parses 'from' and 'to' into image reference.
    MavenProjectProperties mavenProjectProperties =
        MavenProjectProperties.getForProject(getProject(), mavenJibLogger, getExtraDirectory());
    ImageReference baseImage = parseImageReference(getBaseImage(), "from");
    ImageReference targetImage =
        mavenProjectProperties.getGeneratedTargetDockerTag(getTargetImage(), mavenJibLogger);

    // Checks Maven settings for registry credentials.
    if (Boolean.getBoolean("sendCredentialsOverHttp")) {
      mavenJibLogger.warn(
          "Authentication over HTTP is enabled. It is strongly recommended that you do not enable "
              + "this on a public network!");
    }
    MavenSettingsServerCredentials mavenSettingsServerCredentials =
        new MavenSettingsServerCredentials(
            Preconditions.checkNotNull(session).getSettings(), settingsDecrypter, mavenJibLogger);
    Authorization fromAuthorization = getBaseImageAuth();
    RegistryCredentials knownBaseRegistryCredentials =
        fromAuthorization != null
            ? new RegistryCredentials(
                "jib-maven-plugin <from><auth> configuration", fromAuthorization)
            : mavenSettingsServerCredentials.retrieve(baseImage.getRegistry());

    String mainClass = mavenProjectProperties.getMainClass(this);

    // Builds the BuildConfiguration.
    // TODO: Consolidate with BuildImageMojo.
    ImageConfiguration baseImageConfiguration =
        ImageConfiguration.builder(baseImage)
            .setCredentialHelper(getBaseImageCredentialHelperName())
            .setKnownRegistryCredentials(knownBaseRegistryCredentials)
            .build();

    ImageConfiguration targetImageConfiguration = ImageConfiguration.builder(targetImage).build();

    ContainerConfiguration.Builder containerConfigurationBuilder =
        ContainerConfiguration.builder()
            .setEntrypoint(
                JavaEntrypointConstructor.makeDefaultEntrypoint(getJvmFlags(), mainClass))
            .setProgramArguments(getArgs())
            .setEnvironment(getEnvironment())
            .setExposedPorts(ExposedPortsParser.parse(getExposedPorts()));
    if (getUseCurrentTimestamp()) {
      mavenJibLogger.warn(
          "Setting image creation time to current time; your image may not be reproducible.");
      containerConfigurationBuilder.setCreationTime(Instant.now());
    }

    BuildConfiguration.Builder buildConfigurationBuilder =
        BuildConfiguration.builder(mavenJibLogger)
            .setBaseImageConfiguration(baseImageConfiguration)
            .setTargetImageConfiguration(targetImageConfiguration)
            .setContainerConfiguration(containerConfigurationBuilder.build())
            .setAllowInsecureRegistries(getAllowInsecureRegistries())
            .setLayerConfigurations(mavenProjectProperties.getLayerConfigurations());
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
    MavenJibLogger.disableHttpLogging();

    RegistryClient.setUserAgentSuffix(USER_AGENT_SUFFIX);

    try {
      BuildStepsRunner.forBuildToDockerDaemon(buildConfiguration).build(HELPFUL_SUGGESTIONS);
      getLog().info("");

    } catch (CacheDirectoryCreationException | BuildStepsExecutionException ex) {
      throw new MojoExecutionException(ex.getMessage(), ex.getCause());
    }
  }
}
