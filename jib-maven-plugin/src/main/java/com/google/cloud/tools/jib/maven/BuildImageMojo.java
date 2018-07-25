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
import com.google.cloud.tools.jib.image.ImageFormat;
import com.google.cloud.tools.jib.image.ImageReference;
import com.google.cloud.tools.jib.registry.RegistryClient;
import com.google.cloud.tools.jib.registry.credentials.RegistryCredentials;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import java.time.Instant;
import java.util.Arrays;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

/** Builds a container image. */
@Mojo(
    name = BuildImageMojo.GOAL_NAME,
    requiresDependencyResolution = ResolutionScope.RUNTIME_PLUS_SYSTEM)
public class BuildImageMojo extends JibPluginConfiguration {

  @VisibleForTesting static final String GOAL_NAME = "build";

  /** {@code User-Agent} header suffix to send to the registry. */
  private static final String USER_AGENT_SUFFIX = "jib-maven-plugin";

  private static final HelpfulSuggestions HELPFUL_SUGGESTIONS =
      HelpfulSuggestionsProvider.get("Build image failed");

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    MavenBuildLogger mavenBuildLogger = new MavenBuildLogger(getLog());
    handleDeprecatedParameters(mavenBuildLogger);
    SystemPropertyValidator.checkHttpTimeoutProperty(MojoExecutionException::new);

    // Validates 'format'.
    if (Arrays.stream(ImageFormat.values()).noneMatch(value -> value.name().equals(getFormat()))) {
      throw new MojoFailureException(
          "<format> parameter is configured with value '"
              + getFormat()
              + "', but the only valid configuration options are '"
              + ImageFormat.Docker
              + "' and '"
              + ImageFormat.OCI
              + "'.");
    }

    // Parses 'from' into image reference.
    ImageReference baseImage = parseImageReference(getBaseImage(), "from");

    // Parses 'to' into image reference.
    if (Strings.isNullOrEmpty(getTargetImage())) {
      throw new MojoFailureException(
          HelpfulSuggestionsProvider.get("Missing target image parameter")
              .forToNotConfigured(
                  "<to><image>", "pom.xml", "mvn compile jib:build -Dimage=<your image name>"));
    }
    ImageReference targetImage = parseImageReference(getTargetImage(), "to");

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
    RegistryCredentials knownTargetRegistryCredentials =
        mavenSettingsServerCredentials.retrieve(targetImage.getRegistry());

    MavenProjectProperties mavenProjectProperties =
        MavenProjectProperties.getForProject(getProject(), mavenBuildLogger, getExtraDirectory());
    String mainClass = mavenProjectProperties.getMainClass(this);

    // Builds the BuildConfiguration.
    BuildConfiguration.Builder buildConfigurationBuilder =
        BuildConfiguration.builder(mavenBuildLogger)
            .setBaseImage(baseImage)
            .setBaseImageCredentialHelperName(getBaseImageCredentialHelperName())
            .setKnownBaseRegistryCredentials(knownBaseRegistryCredentials)
            .setTargetImage(targetImage)
            .setTargetImageCredentialHelperName(getTargetImageCredentialHelperName())
            .setKnownTargetRegistryCredentials(knownTargetRegistryCredentials)
            .setJavaArguments(getArgs())
            .setEnvironment(getEnvironment())
            .setExposedPorts(ExposedPortsParser.parse(getExposedPorts()))
            .setTargetFormat(ImageFormat.valueOf(getFormat()).getManifestTemplateClass())
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
      BuildStepsRunner.forBuildImage(buildConfiguration).build(HELPFUL_SUGGESTIONS);
      getLog().info("");

    } catch (CacheDirectoryCreationException | BuildStepsExecutionException ex) {
      throw new MojoExecutionException(ex.getMessage(), ex.getCause());
    }
  }
}
