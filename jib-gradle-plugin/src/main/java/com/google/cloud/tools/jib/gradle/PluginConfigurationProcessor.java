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
import com.google.cloud.tools.jib.api.Containerizer;
import com.google.cloud.tools.jib.api.Jib;
import com.google.cloud.tools.jib.api.JibContainerBuilder;
import com.google.cloud.tools.jib.api.RegistryImage;
import com.google.cloud.tools.jib.configuration.BuildConfiguration;
import com.google.cloud.tools.jib.configuration.credentials.Credential;
import com.google.cloud.tools.jib.event.DefaultEventDispatcher;
import com.google.cloud.tools.jib.event.EventDispatcher;
import com.google.cloud.tools.jib.filesystem.AbsoluteUnixPath;
import com.google.cloud.tools.jib.frontend.CredentialRetrieverFactory;
import com.google.cloud.tools.jib.frontend.ExposedPortsParser;
import com.google.cloud.tools.jib.frontend.JavaEntrypointConstructor;
import com.google.cloud.tools.jib.global.JibSystemProperties;
import com.google.cloud.tools.jib.image.ImageReference;
import com.google.cloud.tools.jib.image.InvalidImageReferenceException;
import com.google.cloud.tools.jib.plugins.common.ConfigurationPropertyValidator;
import com.google.cloud.tools.jib.plugins.common.DefaultCredentialRetrievers;
import com.google.cloud.tools.jib.plugins.common.ProjectProperties;
import com.google.cloud.tools.jib.plugins.common.PropertyNames;
import com.google.common.base.Preconditions;
import java.io.FileNotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import javax.annotation.Nullable;
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
   * @param jibExtension the {@link JibExtension} providing the configuration data
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
   * @param logger the logger used to display messages
   * @param jibExtension the {@link JibExtension} providing the configuration data
   * @param projectProperties used for providing additional information
   * @return a new {@link PluginConfigurationProcessor} containing pre-configured builders
   * @throws InvalidImageReferenceException if parsing the base image configuration fails
   */
  static PluginConfigurationProcessor processCommonConfiguration(
      Logger logger, JibExtension jibExtension, GradleProjectProperties projectProperties)
      throws InvalidImageReferenceException, NumberFormatException, FileNotFoundException {
    JibSystemProperties.checkHttpTimeoutProperty();

    // TODO: Instead of disabling logging, have authentication credentials be provided
    disableHttpLogging();
    ImageReference baseImageReference =
        ImageReference.parse(Preconditions.checkNotNull(jibExtension.getFrom().getImage()));

    if (JibSystemProperties.isSendCredentialsOverHttpEnabled()) {
      logger.warn(
          "Authentication over HTTP is enabled. It is strongly recommended that you do not enable "
              + "this on a public network!");
    }
    EventDispatcher eventDispatcher =
        new DefaultEventDispatcher(projectProperties.getEventHandlers());
    DefaultCredentialRetrievers defaultCredentialRetrievers =
        DefaultCredentialRetrievers.init(
            CredentialRetrieverFactory.forImage(baseImageReference, eventDispatcher));
    Optional<Credential> optionalFromCredential =
        ConfigurationPropertyValidator.getImageCredential(
            eventDispatcher,
            PropertyNames.FROM_AUTH_USERNAME,
            PropertyNames.FROM_AUTH_PASSWORD,
            jibExtension.getFrom().getAuth());
    optionalFromCredential.ifPresent(
        fromCredential ->
            defaultCredentialRetrievers.setKnownCredential(fromCredential, "jib.from.auth"));
    defaultCredentialRetrievers.setCredentialHelper(jibExtension.getFrom().getCredHelper());

    List<String> entrypoint = computeEntrypoint(logger, jibExtension, projectProperties);

    RegistryImage baseImage = RegistryImage.named(baseImageReference);
    defaultCredentialRetrievers.asList().forEach(baseImage::addCredentialRetriever);

    JibContainerBuilder jibContainerBuilder =
        Jib.from(baseImage)
            .setLayers(projectProperties.getJavaLayerConfigurations().getLayerConfigurations())
            .setEntrypoint(entrypoint)
            .setProgramArguments(jibExtension.getContainer().getArgs())
            .setEnvironment(jibExtension.getContainer().getEnvironment())
            .setExposedPorts(ExposedPortsParser.parse(jibExtension.getContainer().getPorts()))
            .setLabels(jibExtension.getContainer().getLabels())
            .setUser(jibExtension.getContainer().getUser());
    if (jibExtension.getContainer().getUseCurrentTimestamp()) {
      logger.warn(
          "Setting image creation time to current time; your image may not be reproducible.");
      jibContainerBuilder.setCreationTime(Instant.now());
    }

    return new PluginConfigurationProcessor(
        jibContainerBuilder, baseImageReference, optionalFromCredential.isPresent());
  }

  static void configureContainerizer(
      Containerizer containerizer, JibExtension jibExtension, ProjectProperties projectProperties) {
    containerizer
        .setToolName(GradleProjectProperties.TOOL_NAME)
        .setEventHandlers(projectProperties.getEventHandlers())
        .setAllowInsecureRegistries(jibExtension.getAllowInsecureRegistries())
        .setBaseImageLayersCache(Containerizer.DEFAULT_BASE_CACHE_DIRECTORY)
        .setApplicationLayersCache(projectProperties.getCacheDirectory());

    jibExtension.getTo().getTags().forEach(containerizer::withAdditionalTag);

    if (jibExtension.getUseOnlyProjectCache()) {
      containerizer.setBaseImageLayersCache(projectProperties.getCacheDirectory());
    }
  }

  /**
   * Compute the container entrypoint, in this order:
   *
   * <ol>
   *   <li>the user specified one, if set
   *   <li>for a WAR project, null (it must be inherited from base image)
   *   <li>for a non-WAR project, by resolving the main class
   * </ol>
   *
   * @param logger the logger used to display messages
   * @param jibExtension the {@link JibExtension} providing the configuration data
   * @param projectProperties used for providing additional information
   * @return the entrypoint
   */
  @Nullable
  static List<String> computeEntrypoint(
      Logger logger, JibExtension jibExtension, GradleProjectProperties projectProperties) {
    ContainerParameters parameters = jibExtension.getContainer();
    if (parameters.getEntrypoint() != null && !parameters.getEntrypoint().isEmpty()) {
      if (parameters.getMainClass() != null || !parameters.getJvmFlags().isEmpty()) {
        logger.warn("mainClass and jvmFlags are ignored when entrypoint is specified");
      }
      return parameters.getEntrypoint();
    }

    if (projectProperties.isWarProject()) {
      return null;
    }

    String mainClass = projectProperties.getMainClass(jibExtension);
    return JavaEntrypointConstructor.makeDefaultEntrypoint(
        AbsoluteUnixPath.get(parameters.getAppRoot()), parameters.getJvmFlags(), mainClass);
  }

  private final JibContainerBuilder jibContainerBuilder;
  private final ImageReference baseImageReference;
  private final boolean isBaseImageCredentialPresent;

  private PluginConfigurationProcessor(
      JibContainerBuilder jibContainerBuilder,
      ImageReference baseImageReference,
      boolean isBaseImageCredentialPresent) {
    this.jibContainerBuilder = jibContainerBuilder;
    this.baseImageReference = baseImageReference;
    this.isBaseImageCredentialPresent = isBaseImageCredentialPresent;
  }

  JibContainerBuilder getJibContainerBuilder() {
    return jibContainerBuilder;
  }

  ImageReference getBaseImageReference() {
    return baseImageReference;
  }

  boolean isBaseImageCredentialPresent() {
    return isBaseImageCredentialPresent;
  }
}
