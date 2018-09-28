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

package com.google.cloud.tools.jib.gradle;

import com.google.api.client.http.HttpTransport;
import com.google.cloud.tools.jib.configuration.BuildConfiguration;
import com.google.cloud.tools.jib.configuration.CacheConfiguration;
import com.google.cloud.tools.jib.configuration.ContainerConfiguration;
import com.google.cloud.tools.jib.configuration.ImageConfiguration;
import com.google.cloud.tools.jib.configuration.credentials.Credential;
import com.google.cloud.tools.jib.filesystem.AbsoluteUnixPath;
import com.google.cloud.tools.jib.frontend.CredentialRetrieverFactory;
import com.google.cloud.tools.jib.frontend.ExposedPortsParser;
import com.google.cloud.tools.jib.frontend.JavaEntrypointConstructor;
import com.google.cloud.tools.jib.global.JibSystemProperties;
import com.google.cloud.tools.jib.image.ImageReference;
import com.google.cloud.tools.jib.image.InvalidImageReferenceException;
import com.google.cloud.tools.jib.plugins.common.ConfigurationPropertyValidator;
import com.google.cloud.tools.jib.plugins.common.DefaultCredentialRetrievers;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import org.gradle.api.GradleException;
import org.gradle.api.logging.Logger;
import org.gradle.internal.logging.events.LogEvent;
import org.gradle.internal.logging.events.OutputEventListener;
import org.gradle.internal.logging.slf4j.OutputEventListenerBackedLoggerContext;
import org.slf4j.LoggerFactory;

/** Configures and provides builders for the image building tasks. */
class PluginConfigurationProcessor {

  /**
   * Gets the value of the {@code container.appRoot} parameter. Throws {@link GradleException} if it
   * is not an absolute path in Unix-style.
   *
   * @param jibExtension the Jib plugin extension
   * @return the app root value
   * @throws GradleException if the app root is not an absolute path in Unix-style
   */
  static AbsoluteUnixPath getAppRootChecked(JibExtension jibExtension) {
    String appRoot = jibExtension.getContainer().getAppRoot();
    try {
      return AbsoluteUnixPath.get(appRoot);
    } catch (IllegalArgumentException ex) {
      throw new GradleException("container.appRoot is not an absolute Unix-style path: " + appRoot);
    }
  }

  /** Disables annoying Apache HTTP client logging. */
  static void disableHttpLogging() {
    // Disables Apache HTTP client logging.
    OutputEventListenerBackedLoggerContext context =
        (OutputEventListenerBackedLoggerContext) LoggerFactory.getILoggerFactory();
    OutputEventListener defaultOutputEventListener = context.getOutputEventListener();
    context.setOutputEventListener(
        event -> {
          LogEvent logEvent = (LogEvent) event;
          if (!logEvent.getCategory().contains("org.apache")) {
            defaultOutputEventListener.onOutput(event);
          }
        });

    // Disables Google HTTP client logging.
    java.util.logging.Logger.getLogger(HttpTransport.class.getName()).setLevel(Level.OFF);
  }

  /**
   * Sets up {@link BuildConfiguration} that is common among the image building tasks. This includes
   * setting up the base image reference/authorization, container configuration, cache
   * configuration, and layer configuration.
   *
   * @param logger the logger used to display messages.
   * @param jibExtension the {@link JibExtension} providing the configuration data
   * @param projectProperties used for providing additional information
   * @return a new {@link PluginConfigurationProcessor} containing pre-configured builders
   * @throws InvalidImageReferenceException if parsing the base image configuration fails
   */
  static PluginConfigurationProcessor processCommonConfiguration(
      Logger logger, JibExtension jibExtension, GradleProjectProperties projectProperties)
      throws InvalidImageReferenceException, NumberFormatException {
    JibSystemProperties.checkHttpTimeoutProperty();

    // TODO: Instead of disabling logging, have authentication credentials be provided
    disableHttpLogging();
    ImageReference baseImage = ImageReference.parse(jibExtension.getBaseImage());

    if (JibSystemProperties.isSendCredentialsOverHttpEnabled()) {
      logger.warn(
          "Authentication over HTTP is enabled. It is strongly recommended that you do not enable "
              + "this on a public network!");
    }
    DefaultCredentialRetrievers defaultCredentialRetrievers =
        DefaultCredentialRetrievers.init(
            CredentialRetrieverFactory.forImage(baseImage, projectProperties.getEventDispatcher()));
    Optional<Credential> optionalFromCredential =
        ConfigurationPropertyValidator.getImageCredential(
            projectProperties.getEventDispatcher(),
            "jib.from.auth.username",
            "jib.from.auth.password",
            jibExtension.getFrom().getAuth());
    optionalFromCredential.ifPresent(
        fromCredential ->
            defaultCredentialRetrievers.setKnownCredential(fromCredential, "jib.from.auth"));
    defaultCredentialRetrievers.setCredentialHelperSuffix(jibExtension.getFrom().getCredHelper());

    ImageConfiguration.Builder baseImageConfigurationBuilder =
        ImageConfiguration.builder(baseImage)
            .setCredentialRetrievers(defaultCredentialRetrievers.asList());

    List<String> entrypoint = computeEntrypoint(logger, jibExtension, projectProperties);
    ContainerConfiguration.Builder containerConfigurationBuilder =
        ContainerConfiguration.builder()
            .setEntrypoint(entrypoint)
            .setEnvironment(jibExtension.getEnvironment())
            .setProgramArguments(jibExtension.getContainer().getArgs())
            .setExposedPorts(ExposedPortsParser.parse(jibExtension.getExposedPorts()))
            .setLabels(jibExtension.getLabels());
    if (jibExtension.getUseCurrentTimestamp()) {
      logger.warn(
          "Setting image creation time to current time; your image may not be reproducible.");
      containerConfigurationBuilder.setCreationTime(Instant.now());
    }

    BuildConfiguration.Builder buildConfigurationBuilder =
        BuildConfiguration.builder()
            .setToolName(GradleProjectProperties.TOOL_NAME)
            .setEventDispatcher(projectProperties.getEventDispatcher())
            .setAllowInsecureRegistries(jibExtension.getAllowInsecureRegistries())
            .setLayerConfigurations(
                projectProperties.getJavaLayerConfigurations().getLayerConfigurations());
    CacheConfiguration applicationLayersCacheConfiguration =
        CacheConfiguration.forPath(projectProperties.getCacheDirectory());
    buildConfigurationBuilder.setApplicationLayersCacheConfiguration(
        applicationLayersCacheConfiguration);
    if (jibExtension.getUseOnlyProjectCache()) {
      buildConfigurationBuilder.setBaseImageLayersCacheConfiguration(
          applicationLayersCacheConfiguration);
    }

    return new PluginConfigurationProcessor(
        buildConfigurationBuilder,
        baseImageConfigurationBuilder,
        containerConfigurationBuilder,
        optionalFromCredential.isPresent());
  }

  /**
   * Compute the container entrypoint, in this order :
   *
   * <ol>
   *   <li>the user specified one, if set
   *   <li>for a war project, the jetty default one
   *   <li>for a jar project, by resolving the main class
   * </ol>
   *
   * @param logger the logger used to display messages.
   * @param jibExtension the {@link JibExtension} providing the configuration data
   * @param projectProperties used for providing additional information
   * @return the entrypoint
   */
  static List<String> computeEntrypoint(
      Logger logger, JibExtension jibExtension, GradleProjectProperties projectProperties) {
    List<String> entrypoint = jibExtension.getContainer().getEntrypoint();
    if (!entrypoint.isEmpty()) {
      if (jibExtension.getContainer().getMainClass() != null
          || !jibExtension.getContainer().getJvmFlags().isEmpty()) {
        logger.warn("mainClass and jvmFlags are ignored when entrypoint is specified");
      }
    } else {
      if (projectProperties.isWarProject()) {
        entrypoint = JavaEntrypointConstructor.makeDistrolessJettyEntrypoint();
      } else {
        String mainClass = projectProperties.getMainClass(jibExtension);
        entrypoint =
            JavaEntrypointConstructor.makeDefaultEntrypoint(
                AbsoluteUnixPath.get(jibExtension.getContainer().getAppRoot()),
                jibExtension.getContainer().getJvmFlags(),
                mainClass);
      }
    }
    return entrypoint;
  }

  private final BuildConfiguration.Builder buildConfigurationBuilder;
  private final ImageConfiguration.Builder baseImageConfigurationBuilder;
  private final ContainerConfiguration.Builder containerConfigurationBuilder;
  private final boolean isBaseImageCredentialPresent;

  private PluginConfigurationProcessor(
      BuildConfiguration.Builder buildConfigurationBuilder,
      ImageConfiguration.Builder baseImageConfigurationBuilder,
      ContainerConfiguration.Builder containerConfigurationBuilder,
      boolean isBaseImageCredentialPresent) {
    this.buildConfigurationBuilder = buildConfigurationBuilder;
    this.baseImageConfigurationBuilder = baseImageConfigurationBuilder;
    this.containerConfigurationBuilder = containerConfigurationBuilder;
    this.isBaseImageCredentialPresent = isBaseImageCredentialPresent;
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

  boolean isBaseImageCredentialPresent() {
    return isBaseImageCredentialPresent;
  }
}
