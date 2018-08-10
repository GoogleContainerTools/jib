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

import com.google.cloud.tools.jib.configuration.BuildConfiguration;
import com.google.cloud.tools.jib.configuration.CacheConfiguration;
import com.google.cloud.tools.jib.configuration.ContainerConfiguration;
import com.google.cloud.tools.jib.configuration.ImageConfiguration;
import com.google.cloud.tools.jib.frontend.ExposedPortsParser;
import com.google.cloud.tools.jib.frontend.JavaEntrypointConstructor;
import com.google.cloud.tools.jib.http.Authorization;
import com.google.cloud.tools.jib.image.ImageReference;
import com.google.cloud.tools.jib.image.InvalidImageReferenceException;
import com.google.cloud.tools.jib.plugins.common.ConfigurationPropertyValidator;
import com.google.cloud.tools.jib.plugins.common.MainClassInferenceException;
import com.google.cloud.tools.jib.plugins.common.MainClassResolver;
import com.google.cloud.tools.jib.registry.RegistryClient;
import com.google.cloud.tools.jib.registry.credentials.RegistryCredentials;
import com.google.common.base.Preconditions;
import java.time.Instant;
import org.apache.maven.plugin.MojoExecutionException;

/** Configures and provides builders for the image building goals. */
class PluginConfigurationProcessor {

  /** {@code User-Agent} header suffix to send to the registry. */
  private static final String USER_AGENT_SUFFIX = "jib-maven-plugin";

  /**
   * Sets up {@link BuildConfiguration} that is common among the image building goals. This includes
   * setting up the base image reference/authorization, container configuration, cache
   * configuration, and layer configuration.
   *
   * @param logger the logger used to display messages
   * @param validator used to validate configuration properties
   * @param jibPluginConfiguration the {@link JibPluginConfiguration} providing the configuration
   *     data
   * @param projectProperties used for providing additional information
   * @return a new {@link PluginConfigurationProcessor} containing pre-configured builders
   * @throws MojoExecutionException if the http timeout system property is misconfigured
   */
  static PluginConfigurationProcessor processCommonConfiguration(
      MavenJibLogger logger,
      ConfigurationPropertyValidator validator,
      JibPluginConfiguration jibPluginConfiguration,
      MavenProjectProperties projectProperties)
      throws MojoExecutionException {
    jibPluginConfiguration.handleDeprecatedParameters(logger);
    ConfigurationPropertyValidator.checkHttpTimeoutProperty(MojoExecutionException::new);

    // TODO: Instead of disabling logging, have authentication credentials be provided
    MavenJibLogger.disableHttpLogging();
    RegistryClient.setUserAgentSuffix(USER_AGENT_SUFFIX);

    ImageReference baseImage = parseImageReference(jibPluginConfiguration.getBaseImage(), "from");

    // Checks Maven settings for registry credentials.
    if (Boolean.getBoolean("sendCredentialsOverHttp")) {
      logger.warn(
          "Authentication over HTTP is enabled. It is strongly recommended that you do not enable "
              + "this on a public network!");
    }
    MavenSettingsServerCredentials mavenSettingsServerCredentials =
        new MavenSettingsServerCredentials(
            Preconditions.checkNotNull(jibPluginConfiguration.getSession()).getSettings(),
            jibPluginConfiguration.getSettingsDecrypter(),
            logger);
    Authorization fromAuthorization =
        validator.getImageAuth(
            "from",
            jibPluginConfiguration.getBaseImageAuth().getUsername(),
            jibPluginConfiguration.getBaseImageAuth().getPassword());
    RegistryCredentials knownBaseRegistryCredentials =
        fromAuthorization != null
            ? new RegistryCredentials(
                "jib-maven-plugin <from><auth> configuration", fromAuthorization)
            : mavenSettingsServerCredentials.retrieve(baseImage.getRegistry());
    ImageConfiguration.Builder baseImageConfiguration =
        ImageConfiguration.builder(baseImage)
            .setCredentialHelper(jibPluginConfiguration.getBaseImageCredentialHelperName())
            .setKnownRegistryCredentials(knownBaseRegistryCredentials);

    String mainClass;
    try {
      mainClass =
          MainClassResolver.resolveMainClass(
              jibPluginConfiguration.getMainClass(), projectProperties);
    } catch (MainClassInferenceException ex) {
      throw new MojoExecutionException(ex.getMessage(), ex);
    }
    ContainerConfiguration.Builder containerConfigurationBuilder =
        ContainerConfiguration.builder()
            .setEntrypoint(
                JavaEntrypointConstructor.makeDefaultEntrypoint(
                    jibPluginConfiguration.getJvmFlags(), mainClass))
            .setProgramArguments(jibPluginConfiguration.getArgs())
            .setEnvironment(jibPluginConfiguration.getEnvironment())
            .setExposedPorts(ExposedPortsParser.parse(jibPluginConfiguration.getExposedPorts()));
    if (jibPluginConfiguration.getUseCurrentTimestamp()) {
      logger.warn(
          "Setting image creation time to current time; your image may not be reproducible.");
      containerConfigurationBuilder.setCreationTime(Instant.now());
    }

    BuildConfiguration.Builder buildConfigurationBuilder =
        BuildConfiguration.builder(logger)
            .setAllowInsecureRegistries(jibPluginConfiguration.getAllowInsecureRegistries())
            .setLayerConfigurations(
                projectProperties.getJavaLayerConfigurations().getLayerConfigurations());
    CacheConfiguration applicationLayersCacheConfiguration =
        CacheConfiguration.forPath(projectProperties.getCacheDirectory());
    buildConfigurationBuilder.setApplicationLayersCacheConfiguration(
        applicationLayersCacheConfiguration);
    if (jibPluginConfiguration.getUseOnlyProjectCache()) {
      buildConfigurationBuilder.setBaseImageLayersCacheConfiguration(
          applicationLayersCacheConfiguration);
    }

    return new PluginConfigurationProcessor(
        buildConfigurationBuilder,
        baseImageConfiguration,
        containerConfigurationBuilder,
        mavenSettingsServerCredentials);
  }

  /**
   * @param image the image reference string to parse.
   * @param type name of the parameter being parsed (e.g. "to" or "from").
   * @return the {@link ImageReference} parsed from {@code from}.
   */
  static ImageReference parseImageReference(String image, String type) {
    try {
      return ImageReference.parse(image);
    } catch (InvalidImageReferenceException ex) {
      throw new IllegalStateException("Parameter '" + type + "' is invalid", ex);
    }
  }

  private final BuildConfiguration.Builder buildConfigurationBuilder;
  private final ImageConfiguration.Builder baseImageConfigurationBuilder;
  private final ContainerConfiguration.Builder containerConfigurationBuilder;
  private final MavenSettingsServerCredentials mavenSettingsServerCredentials;

  private PluginConfigurationProcessor(
      BuildConfiguration.Builder buildConfigurationBuilder,
      ImageConfiguration.Builder baseImageConfigurationBuilder,
      ContainerConfiguration.Builder containerConfigurationBuilder,
      MavenSettingsServerCredentials mavenSettingsServerCredentials) {
    this.buildConfigurationBuilder = buildConfigurationBuilder;
    this.baseImageConfigurationBuilder = baseImageConfigurationBuilder;
    this.containerConfigurationBuilder = containerConfigurationBuilder;
    this.mavenSettingsServerCredentials = mavenSettingsServerCredentials;
  }

  BuildConfiguration.Builder getBuildConfigurationBuilder() {
    return buildConfigurationBuilder;
  }

  ImageConfiguration.Builder getBaseImageConfigurationBuilder() {
    return baseImageConfigurationBuilder;
  }

  ContainerConfiguration.Builder getContainerConfigurationBuilder() {
    return containerConfigurationBuilder;
  }

  MavenSettingsServerCredentials getMavenSettingsServerCredentials() {
    return mavenSettingsServerCredentials;
  }
}
