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
import com.google.cloud.tools.jib.frontend.BuildStepsExecutionException;
import com.google.cloud.tools.jib.frontend.BuildStepsRunner;
import com.google.cloud.tools.jib.frontend.ExposedPortsParser;
import com.google.cloud.tools.jib.frontend.HelpfulSuggestions;
import com.google.cloud.tools.jib.frontend.JavaEntrypointConstructor;
import com.google.cloud.tools.jib.frontend.SystemPropertyValidator;
import com.google.cloud.tools.jib.image.ImageReference;
import com.google.cloud.tools.jib.registry.RegistryClient;
import com.google.cloud.tools.jib.registry.credentials.RegistryCredentials;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import java.nio.file.Paths;
import java.time.Instant;
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

  /** {@code User-Agent} header suffix to send to the registry. */
  private static final String USER_AGENT_SUFFIX = "jib-maven-plugin";

  private static final HelpfulSuggestions HELPFUL_SUGGESTIONS =
      HelpfulSuggestionsProvider.get("Building image tarball failed");

  @Override
  public void execute() throws MojoExecutionException {
    MavenBuildLogger mavenBuildLogger = new MavenBuildLogger(getLog());
    handleDeprecatedParameters(mavenBuildLogger);
    SystemPropertyValidator.checkHttpTimeoutProperty(MojoExecutionException::new);

    // Parses 'from' and 'to' into image reference.
    MavenProjectProperties mavenProjectProperties =
        MavenProjectProperties.getForProject(getProject(), mavenBuildLogger, getExtraDirectory());
    ImageReference baseImage = parseImageReference(getBaseImage(), "from");
    ImageReference targetImage =
        mavenProjectProperties.getGeneratedTargetDockerTag(getTargetImage(), mavenBuildLogger);

    // Checks Maven settings for registry credentials.
    if (Boolean.getBoolean("sendCredentialsOverHttp")) {
      mavenBuildLogger.warn(
          "Authentication over HTTP is enabled. It is strongly recommended that you do not enable "
              + "this on a public network!");
    }
    MavenSettingsServerCredentials mavenSettingsServerCredentials =
        new MavenSettingsServerCredentials(
            Preconditions.checkNotNull(session).getSettings(), settingsDecrypter, mavenBuildLogger);
    RegistryCredentials knownBaseRegistryCredentials =
        mavenSettingsServerCredentials.retrieve(baseImage.getRegistry());

    String mainClass = mavenProjectProperties.getMainClass(this);

    // Builds the BuildConfiguration.
    // TODO: Consolidate with BuildImageMojo.
    BuildConfiguration.Builder buildConfigurationBuilder =
        BuildConfiguration.builder(mavenBuildLogger)
            .setBaseImage(baseImage)
            .setBaseImageCredentialHelperName(getBaseImageCredentialHelperName())
            .setKnownBaseRegistryCredentials(knownBaseRegistryCredentials)
            .setTargetImage(targetImage)
            .setJavaArguments(getArgs())
            .setEnvironment(getEnvironment())
            .setExposedPorts(ExposedPortsParser.parse(getExposedPorts()))
            .setAllowInsecureRegistries(getAllowInsecureRegistries())
            .setLayerConfigurations(mavenProjectProperties.getLayerConfigurations())
            .setEntrypoint(
                JavaEntrypointConstructor.makeDefaultEntrypoint(getJvmFlags(), mainClass));
    CacheConfiguration applicationLayersCacheConfiguration =
        CacheConfiguration.forPath(mavenProjectProperties.getCacheDirectory());
    buildConfigurationBuilder.setApplicationLayersCacheConfiguration(
        applicationLayersCacheConfiguration);
    if (getUseOnlyProjectCache()) {
      buildConfigurationBuilder.setBaseImageLayersCacheConfiguration(
          applicationLayersCacheConfiguration);
    }
    if (getUseCurrentTimestamp()) {
      mavenBuildLogger.warn(
          "Setting image creation time to current time; your image may not be reproducible.");
      buildConfigurationBuilder.setCreationTime(Instant.now());
    }

    BuildConfiguration buildConfiguration = buildConfigurationBuilder.build();

    // TODO: Instead of disabling logging, have authentication credentials be provided
    MavenBuildLogger.disableHttpLogging();

    RegistryClient.setUserAgentSuffix(USER_AGENT_SUFFIX);

    try {
      BuildStepsRunner.forBuildTar(
              Paths.get(getProject().getBuild().getDirectory()).resolve("jib-image.tar"),
              buildConfiguration)
          .build(HELPFUL_SUGGESTIONS);
      getLog().info("");

    } catch (CacheDirectoryCreationException | BuildStepsExecutionException ex) {
      throw new MojoExecutionException(ex.getMessage(), ex.getCause());
    }
  }
}
