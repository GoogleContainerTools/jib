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

package com.google.cloud.tools.jib.maven;

import com.google.cloud.tools.jib.api.Containerizer;
import com.google.cloud.tools.jib.api.Jib;
import com.google.cloud.tools.jib.api.JibContainerBuilder;
import com.google.cloud.tools.jib.api.RegistryImage;
import com.google.cloud.tools.jib.configuration.BuildConfiguration;
import com.google.cloud.tools.jib.configuration.FilePermissions;
import com.google.cloud.tools.jib.configuration.credentials.Credential;
import com.google.cloud.tools.jib.event.DefaultEventDispatcher;
import com.google.cloud.tools.jib.event.EventDispatcher;
import com.google.cloud.tools.jib.filesystem.AbsoluteUnixPath;
import com.google.cloud.tools.jib.frontend.CredentialRetrieverFactory;
import com.google.cloud.tools.jib.frontend.ExposedPortsParser;
import com.google.cloud.tools.jib.frontend.JavaEntrypointConstructor;
import com.google.cloud.tools.jib.frontend.JavaLayerConfigurations;
import com.google.cloud.tools.jib.global.JibSystemProperties;
import com.google.cloud.tools.jib.image.ImageReference;
import com.google.cloud.tools.jib.image.InvalidImageReferenceException;
import com.google.cloud.tools.jib.maven.JibPluginConfiguration.PermissionConfiguration;
import com.google.cloud.tools.jib.plugins.common.ConfigurationPropertyValidator;
import com.google.cloud.tools.jib.plugins.common.DefaultCredentialRetrievers;
import com.google.cloud.tools.jib.plugins.common.ProjectProperties;
import com.google.cloud.tools.jib.plugins.common.PropertyNames;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import java.io.FileNotFoundException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

/** Configures and provides builders for the image building goals. */
class PluginConfigurationProcessor {

  /**
   * Returns true if the Maven packaging type is "war".
   *
   * @param jibPluginConfiguration the Jib plugin configuration
   * @return true if the Maven packaging type is "war"
   */
  private static boolean isWarPackaging(JibPluginConfiguration jibPluginConfiguration) {
    return "war".equals(jibPluginConfiguration.getProject().getPackaging());
  }

  /**
   * Gets the value of the {@code <container><appRoot>} parameter. If the parameter is empty,
   * returns {@link JavaLayerConfigurations#DEFAULT_WEB_APP_ROOT} for project with WAR packaging or
   * {@link JavaLayerConfigurations#DEFAULT_APP_ROOT} for other packaging.
   *
   * @param jibPluginConfiguration the Jib plugin configuration
   * @return the app root value
   * @throws MojoExecutionException if the app root is not an absolute path in Unix-style
   */
  static AbsoluteUnixPath getAppRootChecked(JibPluginConfiguration jibPluginConfiguration)
      throws MojoExecutionException {
    String appRoot = jibPluginConfiguration.getAppRoot();
    if (appRoot.isEmpty()) {
      appRoot =
          isWarPackaging(jibPluginConfiguration)
              ? JavaLayerConfigurations.DEFAULT_WEB_APP_ROOT
              : JavaLayerConfigurations.DEFAULT_APP_ROOT;
    }
    try {
      return AbsoluteUnixPath.get(appRoot);
    } catch (IllegalArgumentException ex) {
      throw new MojoExecutionException(
          "<container><appRoot> is not an absolute Unix-style path: " + appRoot);
    }
  }

  /**
   * Gets the value of the {@code <from><image>} parameter. If the parameter is null, returns
   * "gcr.io/distroless/java/jetty" for projects with WAR packaging or "gcr.io/distroless/java" for
   * other packaging.
   *
   * @param jibPluginConfiguration the Jib plugin configuration
   * @return the base image value
   */
  static String getBaseImage(JibPluginConfiguration jibPluginConfiguration) {
    String baseImage = jibPluginConfiguration.getBaseImage();
    if (baseImage == null) {
      return isWarPackaging(jibPluginConfiguration)
          ? "gcr.io/distroless/java/jetty"
          : "gcr.io/distroless/java";
    }
    return baseImage;
  }

  /**
   * Gets the extra directory path from a {@link JibPluginConfiguration}. Returns {@code (project
   * dir)/src/main/jib} if null.
   *
   * @param jibPluginConfiguration the build configuration
   * @return the resolved extra directory
   */
  static Path getExtraDirectoryPath(JibPluginConfiguration jibPluginConfiguration) {
    return jibPluginConfiguration
        .getExtraDirectoryPath()
        .orElseGet(
            () ->
                Preconditions.checkNotNull(jibPluginConfiguration.getProject())
                    .getBasedir()
                    .toPath()
                    .resolve("src")
                    .resolve("main")
                    .resolve("jib"));
  }

  /**
   * Validates and converts a list of {@link PermissionConfiguration} to an equivalent {@code
   * AbsoluteUnixPath->FilePermission} map.
   *
   * @param inputList the list to convert
   * @return the resulting map
   */
  @VisibleForTesting
  static Map<AbsoluteUnixPath, FilePermissions> convertPermissionsList(
      List<PermissionConfiguration> inputList) {
    HashMap<AbsoluteUnixPath, FilePermissions> permissionsMap = new HashMap<>();
    for (PermissionConfiguration permission : inputList) {
      if (permission.getFile() == null || permission.getMode() == null) {
        throw new IllegalArgumentException(
            "Incomplete <permission> configuration; requires <file> and <mode> fields to both be "
                + "non-null.");
      }
      AbsoluteUnixPath key = AbsoluteUnixPath.get(permission.getFile());
      FilePermissions value = FilePermissions.fromOctalString(permission.getMode());
      permissionsMap.put(key, value);
    }
    return permissionsMap;
  }

  /** Disables annoying Apache HTTP client logging. */
  static void disableHttpLogging() {
    System.setProperty(
        "org.apache.commons.logging.Log", "org.apache.commons.logging.impl.SimpleLog");
    System.setProperty("org.apache.commons.logging.simplelog.defaultlog", "error");
  }

  /**
   * Sets up {@link BuildConfiguration} that is common among the image building goals. This includes
   * setting up the base image reference/authorization, container configuration, cache
   * configuration, and layer configuration.
   *
   * @param logger the logger used to display messages
   * @param jibPluginConfiguration the {@link JibPluginConfiguration} providing the configuration
   *     data
   * @param projectProperties used for providing additional information
   * @return a new {@link PluginConfigurationProcessor} containing pre-configured builders
   * @throws MojoExecutionException if the http timeout system property is misconfigured
   */
  static PluginConfigurationProcessor processCommonConfiguration(
      Log logger,
      JibPluginConfiguration jibPluginConfiguration,
      MavenProjectProperties projectProperties)
      throws MojoExecutionException {
    try {
      JibSystemProperties.checkHttpTimeoutProperty();
    } catch (NumberFormatException ex) {
      throw new MojoExecutionException(ex.getMessage(), ex);
    }

    // TODO: Instead of disabling logging, have authentication credentials be provided
    disableHttpLogging();
    ImageReference baseImageReference =
        parseImageReference(getBaseImage(jibPluginConfiguration), "from");

    // Checks Maven settings for registry credentials.
    if (JibSystemProperties.isSendCredentialsOverHttpEnabled()) {
      logger.warn(
          "Authentication over HTTP is enabled. It is strongly recommended that you do not enable "
              + "this on a public network!");
    }
    MavenSettingsServerCredentials mavenSettingsServerCredentials =
        new MavenSettingsServerCredentials(
            Preconditions.checkNotNull(jibPluginConfiguration.getSession()).getSettings(),
            jibPluginConfiguration.getSettingsDecrypter(),
            logger);
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
            jibPluginConfiguration.getBaseImageAuth());
    if (optionalFromCredential.isPresent()) {
      defaultCredentialRetrievers.setKnownCredential(
          optionalFromCredential.get(), "jib-maven-plugin <from><auth> configuration");
    } else {
      optionalFromCredential =
          mavenSettingsServerCredentials.retrieve(baseImageReference.getRegistry());
      optionalFromCredential.ifPresent(
          fromCredential ->
              defaultCredentialRetrievers.setInferredCredential(
                  fromCredential, MavenSettingsServerCredentials.CREDENTIAL_SOURCE));
    }
    defaultCredentialRetrievers.setCredentialHelper(
        jibPluginConfiguration.getBaseImageCredentialHelperName());

    List<String> entrypoint = computeEntrypoint(logger, jibPluginConfiguration, projectProperties);

    RegistryImage baseImage = RegistryImage.named(baseImageReference);
    try {
      defaultCredentialRetrievers.asList().forEach(baseImage::addCredentialRetriever);
    } catch (FileNotFoundException ex) {
      throw new MojoExecutionException(ex.getMessage(), ex);
    }

    JibContainerBuilder jibContainerBuilder =
        Jib.from(baseImage)
            .setLayers(projectProperties.getJavaLayerConfigurations().getLayerConfigurations())
            .setEntrypoint(entrypoint)
            .setProgramArguments(jibPluginConfiguration.getArgs())
            .setEnvironment(jibPluginConfiguration.getEnvironment())
            .setExposedPorts(ExposedPortsParser.parse(jibPluginConfiguration.getExposedPorts()))
            .setLabels(jibPluginConfiguration.getLabels())
            .setUser(jibPluginConfiguration.getUser());
    if (jibPluginConfiguration.getUseCurrentTimestamp()) {
      logger.warn(
          "Setting image creation time to current time; your image may not be reproducible.");
      jibContainerBuilder.setCreationTime(Instant.now());
    }

    return new PluginConfigurationProcessor(
        jibContainerBuilder,
        baseImageReference,
        mavenSettingsServerCredentials,
        optionalFromCredential.isPresent());
  }

  /**
   * Configures a {@link Containerizer} with values pulled from project properties/build
   * configuration.
   *
   * @param containerizer the {@link Containerizer} to configure
   * @param jibPluginConfiguration the build configuration
   * @param projectProperties the project properties
   */
  static void configureContainerizer(
      Containerizer containerizer,
      JibPluginConfiguration jibPluginConfiguration,
      ProjectProperties projectProperties) {
    containerizer
        .setToolName(MavenProjectProperties.TOOL_NAME)
        .setEventHandlers(projectProperties.getEventHandlers())
        .setAllowInsecureRegistries(jibPluginConfiguration.getAllowInsecureRegistries())
        .setBaseImageLayersCache(Containerizer.DEFAULT_BASE_CACHE_DIRECTORY)
        .setApplicationLayersCache(projectProperties.getCacheDirectory());

    jibPluginConfiguration.getTargetImageAdditionalTags().forEach(containerizer::withAdditionalTag);

    if (jibPluginConfiguration.getUseOnlyProjectCache()) {
      containerizer.setBaseImageLayersCache(projectProperties.getCacheDirectory());
    }
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

  /**
   * Compute the container entrypoint, in this order:
   *
   * <ol>
   *   <li>the user specified one, if set
   *   <li>for a WAR project, null (it must be inherited from base image)
   *   <li>for a non-WAR project, by resolving the main class
   * </ol>
   *
   * @param logger the logger used to display messages.
   * @param jibPluginConfiguration the {@link JibPluginConfiguration} providing the configuration
   *     data
   * @param projectProperties used for providing additional information
   * @return the entrypoint
   * @throws MojoExecutionException if resolving the main class fails or the app root parameter is
   *     not an absolute path in Unix-style
   */
  @Nullable
  static List<String> computeEntrypoint(
      Log logger,
      JibPluginConfiguration jibPluginConfiguration,
      MavenProjectProperties projectProperties)
      throws MojoExecutionException {
    List<String> entrypointParameter = jibPluginConfiguration.getEntrypoint();
    if (entrypointParameter != null && !entrypointParameter.isEmpty()) {
      if (jibPluginConfiguration.getMainClass() != null
          || !jibPluginConfiguration.getJvmFlags().isEmpty()) {
        logger.warn("<mainClass> and <jvmFlags> are ignored when <entrypoint> is specified");
      }
      return entrypointParameter;
    }

    if (isWarPackaging(jibPluginConfiguration)) {
      return null;
    }

    String mainClass = projectProperties.getMainClass(jibPluginConfiguration);
    return JavaEntrypointConstructor.makeDefaultEntrypoint(
        getAppRootChecked(jibPluginConfiguration), jibPluginConfiguration.getJvmFlags(), mainClass);
  }

  private final JibContainerBuilder jibContainerBuilder;
  private final ImageReference baseImageReference;
  private final MavenSettingsServerCredentials mavenSettingsServerCredentials;
  private final boolean isBaseImageCredentialPresent;

  private PluginConfigurationProcessor(
      JibContainerBuilder jibContainerBuilder,
      ImageReference baseImageReference,
      MavenSettingsServerCredentials mavenSettingsServerCredentials,
      boolean isBaseImageCredentialPresent) {
    this.jibContainerBuilder = jibContainerBuilder;
    this.baseImageReference = baseImageReference;
    this.mavenSettingsServerCredentials = mavenSettingsServerCredentials;
    this.isBaseImageCredentialPresent = isBaseImageCredentialPresent;
  }

  JibContainerBuilder getJibContainerBuilder() {
    return jibContainerBuilder;
  }

  ImageReference getBaseImageReference() {
    return baseImageReference;
  }

  MavenSettingsServerCredentials getMavenSettingsServerCredentials() {
    return mavenSettingsServerCredentials;
  }

  boolean isBaseImageCredentialPresent() {
    return isBaseImageCredentialPresent;
  }
}
