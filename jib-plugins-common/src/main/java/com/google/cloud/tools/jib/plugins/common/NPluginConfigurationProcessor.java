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

package com.google.cloud.tools.jib.plugins.common;

import com.google.api.client.repackaged.com.google.common.annotations.VisibleForTesting;
import com.google.cloud.tools.jib.api.Jib;
import com.google.cloud.tools.jib.api.JibContainerBuilder;
import com.google.cloud.tools.jib.api.RegistryImage;
import com.google.cloud.tools.jib.configuration.credentials.Credential;
import com.google.cloud.tools.jib.event.DefaultEventDispatcher;
import com.google.cloud.tools.jib.event.EventDispatcher;
import com.google.cloud.tools.jib.event.events.LogEvent;
import com.google.cloud.tools.jib.filesystem.AbsoluteUnixPath;
import com.google.cloud.tools.jib.frontend.CredentialRetrieverFactory;
import com.google.cloud.tools.jib.frontend.ExposedPortsParser;
import com.google.cloud.tools.jib.frontend.JavaEntrypointConstructor;
import com.google.cloud.tools.jib.frontend.JavaLayerConfigurations;
import com.google.cloud.tools.jib.global.JibSystemProperties;
import com.google.cloud.tools.jib.image.ImageReference;
import com.google.cloud.tools.jib.image.InvalidImageReferenceException;
import com.google.common.base.Verify;
import java.io.FileNotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;

// TODO: remove PluginConfigurationProcess classes in Maven and Gradle code, and rename this to
// PluginConfigurationProcess.
public class NPluginConfigurationProcessor {

  /**
   * Gets the value of the {@code appRoot} parameter. If the parameter is empty, returns {@link
   * JavaLayerConfigurations#DEFAULT_WEB_APP_ROOT} for WAR projects or {@link
   * JavaLayerConfigurations#DEFAULT_APP_ROOT} for other projects.
   *
   * @param rawConfiguration raw configuration data
   * @param projectProperties used for providing additional information
   * @return the app root value
   * @throws NotAbsoluteUnixPathException if {@code appRoot} value is not an absolute Unix path
   */
  static AbsoluteUnixPath getAppRootChecked(
      RawConfiguration rawConfiguration, ProjectProperties projectProperties)
      throws NotAbsoluteUnixPathException {
    String appRoot = rawConfiguration.getAppRoot();
    if (appRoot.isEmpty()) {
      appRoot =
          projectProperties.isWarProject()
              ? JavaLayerConfigurations.DEFAULT_WEB_APP_ROOT
              : JavaLayerConfigurations.DEFAULT_APP_ROOT;
    }
    try {
      return AbsoluteUnixPath.get(appRoot);
    } catch (IllegalArgumentException ex) {
      throw new NotAbsoluteUnixPathException(appRoot, ex);
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
   * @param rawConfiguration raw configuration data
   * @param projectProperties used for providing additional information
   * @return the entrypoint
   * @throws MainClassInferenceException if no valid main class is configured or discovered
   * @throws NotAbsoluteUnixPathException if {@code appRoot} value is not an absolute Unix path
   */
  @Nullable
  public static List<String> computeEntrypoint(
      RawConfiguration rawConfiguration, ProjectProperties projectProperties)
      throws MainClassInferenceException, NotAbsoluteUnixPathException {
    List<String> entrypointParameter = rawConfiguration.getEntrypoint();
    if (entrypointParameter != null && !entrypointParameter.isEmpty()) {
      if (rawConfiguration.getMainClass() != null || !rawConfiguration.getJvmFlags().isEmpty()) {
        new DefaultEventDispatcher(projectProperties.getEventHandlers())
            .dispatch(
                LogEvent.warn("mainClass and jvmFlags are ignored when entrypoint is specified"));
      }
      return entrypointParameter;
    }

    if (projectProperties.isWarProject()) {
      return null;
    }

    AbsoluteUnixPath appRoot = getAppRootChecked(rawConfiguration, projectProperties);
    String mainClass =
        MainClassResolver.resolveMainClass(rawConfiguration.getMainClass(), projectProperties);
    return JavaEntrypointConstructor.makeDefaultEntrypoint(
        appRoot, rawConfiguration.getJvmFlags(), mainClass);
  }

  /**
   * Gets the suitable value for the base image. If the raw base image parameter is null, returns
   * {@code "gcr.io/distroless/java/jetty"} for WAR projects or {@code "gcr.io/distroless/java"} for
   * non-WAR.
   */
  @VisibleForTesting
  static String getBaseImage(
      RawConfiguration rawConfiguration, ProjectProperties projectProperties) {
    String baseImage = rawConfiguration.getFromImage();
    if (baseImage == null) {
      return projectProperties.isWarProject()
          ? "gcr.io/distroless/java/jetty"
          : "gcr.io/distroless/java";
    }
    return baseImage;
  }

  public static NPluginConfigurationProcessor processCommonConfiguration(
      RawConfiguration rawConfiguration, ProjectProperties projectProperties)
      throws InvalidImageReferenceException, NumberFormatException, FileNotFoundException,
          MainClassInferenceException, NotAbsoluteUnixPathException,
          InferredAuthRetrievalException {
    JibSystemProperties.checkHttpTimeoutProperty();

    // TODO: Instead of disabling logging, have authentication credentials be provided

    // DON'T FORGET TO RESURRECT THIS!
    // disableHttpLogging();
    // DON'T FORGET TO RESURRECT THIS!
    ImageReference baseImageReference =
        ImageReference.parse(getBaseImage(rawConfiguration, projectProperties));

    EventDispatcher eventDispatcher =
        new DefaultEventDispatcher(projectProperties.getEventHandlers());
    if (JibSystemProperties.isSendCredentialsOverHttpEnabled()) {
      eventDispatcher.dispatch(
          LogEvent.warn(
              "Authentication over HTTP is enabled. It is strongly recommended that you do not "
                  + "enable this on a public network!"));
    }
    DefaultCredentialRetrievers defaultCredentialRetrievers =
        DefaultCredentialRetrievers.init(
            CredentialRetrieverFactory.forImage(baseImageReference, eventDispatcher));
    Optional<Credential> optionalFromCredential =
        ConfigurationPropertyValidator.getImageCredential(
            eventDispatcher,
            PropertyNames.FROM_AUTH_USERNAME,
            PropertyNames.FROM_AUTH_PASSWORD,
            rawConfiguration.getFromAuth());
    if (optionalFromCredential.isPresent()) {
      defaultCredentialRetrievers.setKnownCredential(
          optionalFromCredential.get(), rawConfiguration.getFromAuth().getPropertyDescriptor());
    } else {
      AuthProperty inferredAuth =
          rawConfiguration.getInferredAuth(baseImageReference.getRegistry());
      if (inferredAuth != null) {
        String username = Verify.verifyNotNull(inferredAuth.getUsername());
        String password = Verify.verifyNotNull(inferredAuth.getPassword());
        Credential credential = Credential.basic(username, password);
        defaultCredentialRetrievers.setInferredCredential(
            credential, inferredAuth.getPropertyDescriptor());
        optionalFromCredential = Optional.of(credential);
      }
    }
    defaultCredentialRetrievers.setCredentialHelper(rawConfiguration.getFromCredHelper());

    RegistryImage baseImage = RegistryImage.named(baseImageReference);
    defaultCredentialRetrievers.asList().forEach(baseImage::addCredentialRetriever);

    JibContainerBuilder jibContainerBuilder =
        Jib.from(baseImage)
            .setLayers(projectProperties.getJavaLayerConfigurations().getLayerConfigurations())
            .setEntrypoint(computeEntrypoint(rawConfiguration, projectProperties))
            .setProgramArguments(rawConfiguration.getProgramArguments())
            .setEnvironment(rawConfiguration.getEnvironment())
            .setExposedPorts(ExposedPortsParser.parse(rawConfiguration.getPorts()))
            .setLabels(rawConfiguration.getEnvironment())
            .setUser(rawConfiguration.getUser());
    if (rawConfiguration.getUseCurrentTimestamp()) {
      eventDispatcher.dispatch(
          LogEvent.warn(
              "Setting image creation time to current time; your image may not be reproducible."));
      jibContainerBuilder.setCreationTime(Instant.now());
    }

    return new NPluginConfigurationProcessor(
        jibContainerBuilder, baseImageReference, optionalFromCredential.isPresent());
  }

  private final JibContainerBuilder jibContainerBuilder;
  private final ImageReference baseImageReference;
  private final boolean isBaseImageCredentialPresent;

  private NPluginConfigurationProcessor(
      JibContainerBuilder jibContainerBuilder,
      ImageReference baseImageReference,
      boolean isBaseImageCredentialPresent) {
    this.jibContainerBuilder = jibContainerBuilder;
    this.baseImageReference = baseImageReference;
    this.isBaseImageCredentialPresent = isBaseImageCredentialPresent;
  }

  public JibContainerBuilder getJibContainerBuilder() {
    return jibContainerBuilder;
  }

  public ImageReference getBaseImageReference() {
    return baseImageReference;
  }

  public boolean isBaseImageCredentialPresent() {
    return isBaseImageCredentialPresent;
  }
}
