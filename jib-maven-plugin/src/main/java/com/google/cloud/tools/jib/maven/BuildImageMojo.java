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
import com.google.cloud.tools.jib.frontend.BuildStepsExecutionException;
import com.google.cloud.tools.jib.frontend.BuildStepsRunner;
import com.google.cloud.tools.jib.frontend.ExposedPortsParser;
import com.google.cloud.tools.jib.frontend.HelpfulSuggestions;
import com.google.cloud.tools.jib.frontend.JavaEntrypointConstructor;
import com.google.cloud.tools.jib.frontend.SystemPropertyValidator;
import com.google.cloud.tools.jib.http.Authorization;
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
    // TODO: Consolidate all of these checks.
    if ("pom".equals(getProject().getPackaging())) {
      getLog().info("Skipping containerization because packaging is 'pom'...");
      return;
    }

    MavenJibLogger mavenJibLogger = new MavenJibLogger(getLog());
    handleDeprecatedParameters(mavenJibLogger);
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
    Authorization toAuthorization = getTargetImageAuth();
    RegistryCredentials knownTargetRegistryCredentials =
        toAuthorization != null
            ? new RegistryCredentials("jib-maven-plugin <to><auth> configuration", toAuthorization)
            : mavenSettingsServerCredentials.retrieve(targetImage.getRegistry());

    MavenProjectProperties mavenProjectProperties =
        MavenProjectProperties.getForProject(getProject(), mavenJibLogger, getExtraDirectory());
    String mainClass = mavenProjectProperties.getMainClass(this);

    // Builds the BuildConfiguration.
    ImageConfiguration baseImageConfiguration =
        ImageConfiguration.builder(baseImage)
            .setCredentialHelper(getBaseImageCredentialHelperName())
            .setKnownRegistryCredentials(knownBaseRegistryCredentials)
            .build();

    ImageConfiguration targetImageConfiguration =
        ImageConfiguration.builder(targetImage)
            .setCredentialHelper(getTargetImageCredentialHelperName())
            .setKnownRegistryCredentials(knownTargetRegistryCredentials)
            .build();

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
            .setTargetFormat(ImageFormat.valueOf(getFormat()).getManifestTemplateClass())
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
      BuildStepsRunner.forBuildImage(buildConfiguration).build(HELPFUL_SUGGESTIONS);
      getLog().info("");

    } catch (CacheDirectoryCreationException | BuildStepsExecutionException ex) {
      throw new MojoExecutionException(ex.getMessage(), ex.getCause());
    }
  }
}
