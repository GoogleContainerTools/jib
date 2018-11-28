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

import com.google.cloud.tools.jib.api.Containerizer;
import com.google.cloud.tools.jib.api.DockerDaemonImage;
import com.google.cloud.tools.jib.api.Jib;
import com.google.cloud.tools.jib.api.JibContainerBuilder;
import com.google.cloud.tools.jib.api.RegistryImage;
import com.google.cloud.tools.jib.api.TarImage;
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
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Verify;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Configures and provides {@code JibContainerBuilder} for the image building tasks based on raw
 * plugin configuration values and project properties.
 */
public class PluginConfigurationProcessor {

  @FunctionalInterface
  private interface InferredAuthProvider {

    Optional<AuthProperty> getInferredAuth(String registry) throws InferredAuthRetrievalException;
  }

  /**
   * Compute the container entrypoint, in this order:
   *
   * <ol>
   *   <li>null (inheriting from the base image), if the user specified value is {@code INHERIT}
   *   <li>the user specified one, if set
   *   <li>for a WAR project, null (it must be inherited from base image)
   *   <li>for a non-WAR project, by resolving the main class
   * </ol>
   *
   * @param rawConfiguration raw configuration data
   * @param projectProperties used for providing additional information
   * @return the entrypoint
   * @throws MainClassInferenceException if no valid main class is configured or discovered
   * @throws InvalidAppRootException if {@code appRoot} value is not an absolute Unix path
   */
  @Nullable
  public static List<String> computeEntrypoint(
      RawConfiguration rawConfiguration, ProjectProperties projectProperties)
      throws MainClassInferenceException, InvalidAppRootException {
    Optional<List<String>> rawEntrypoint = rawConfiguration.getEntrypoint();
    if (rawEntrypoint.isPresent() && !rawEntrypoint.get().isEmpty()) {
      if (rawConfiguration.getMainClass().isPresent()
          || !rawConfiguration.getJvmFlags().isEmpty()) {
        new DefaultEventDispatcher(projectProperties.getEventHandlers())
            .dispatch(
                LogEvent.warn("mainClass and jvmFlags are ignored when entrypoint is specified"));
      }

      if (rawEntrypoint.get().size() == 1 && "INHERIT".equals(rawEntrypoint.get().get(0))) {
        return null;
      }
      return rawEntrypoint.get();
    }

    if (projectProperties.isWarProject()) {
      return null;
    }

    AbsoluteUnixPath appRoot = getAppRootChecked(rawConfiguration, projectProperties);
    String mainClass =
        MainClassResolver.resolveMainClass(
            rawConfiguration.getMainClass().orElse(null), projectProperties);
    return JavaEntrypointConstructor.makeDefaultEntrypoint(
        appRoot, rawConfiguration.getJvmFlags(), mainClass);
  }

  /**
   * Gets the suitable value for the base image. If the raw base image parameter is null, returns
   * {@code "gcr.io/distroless/java/jetty"} for WAR projects or {@code "gcr.io/distroless/java"} for
   * non-WAR.
   *
   * @param rawConfiguration raw configuration data
   * @param projectProperties used for providing additional information
   * @return the base image
   */
  public static String getBaseImage(
      RawConfiguration rawConfiguration, ProjectProperties projectProperties) {
    return rawConfiguration
        .getFromImage()
        .orElse(
            projectProperties.isWarProject()
                ? "gcr.io/distroless/java/jetty"
                : "gcr.io/distroless/java");
  }

  public static PluginConfigurationProcessor processCommonConfigurationForDockerDaemonImage(
      RawConfiguration rawConfiguration,
      ProjectProperties projectProperties,
      @Nullable Path dockerExecutable,
      @Nullable Map<String, String> dockerEnvironment,
      HelpfulSuggestions helpfulSuggestions)
      throws InvalidImageReferenceException, MainClassInferenceException, InvalidAppRootException,
          InferredAuthRetrievalException, IOException, InvalidWorkingDirectoryException,
          InvalidContainerVolumeException {
    ImageReference targetImageReference =
        getGeneratedTargetDockerTag(rawConfiguration, projectProperties, helpfulSuggestions);
    DockerDaemonImage targetImage = DockerDaemonImage.named(targetImageReference);
    if (dockerExecutable != null) {
      targetImage.setDockerExecutable(dockerExecutable);
    }
    if (dockerEnvironment != null) {
      targetImage.setDockerEnvironment(dockerEnvironment);
    }
    Containerizer containerizer = Containerizer.to(targetImage);

    return processCommonConfiguration(
        rawConfiguration, projectProperties, containerizer, targetImageReference, false);
  }

  public static PluginConfigurationProcessor processCommonConfigurationForTarImage(
      RawConfiguration rawConfiguration,
      ProjectProperties projectProperties,
      Path tarImagePath,
      HelpfulSuggestions helpfulSuggestions)
      throws InvalidImageReferenceException, MainClassInferenceException, InvalidAppRootException,
          InferredAuthRetrievalException, IOException, InvalidWorkingDirectoryException,
          InvalidContainerVolumeException {
    ImageReference targetImageReference =
        getGeneratedTargetDockerTag(rawConfiguration, projectProperties, helpfulSuggestions);
    TarImage targetImage = TarImage.named(targetImageReference).saveTo(tarImagePath);
    Containerizer containerizer = Containerizer.to(targetImage);

    return processCommonConfiguration(
        rawConfiguration, projectProperties, containerizer, targetImageReference, false);
  }

  public static PluginConfigurationProcessor processCommonConfigurationForRegistryImage(
      RawConfiguration rawConfiguration, ProjectProperties projectProperties)
      throws InferredAuthRetrievalException, InvalidImageReferenceException,
          MainClassInferenceException, InvalidAppRootException, IOException,
          InvalidWorkingDirectoryException, InvalidContainerVolumeException {
    Preconditions.checkArgument(rawConfiguration.getToImage().isPresent());

    ImageReference targetImageReference = ImageReference.parse(rawConfiguration.getToImage().get());
    RegistryImage targetImage = RegistryImage.named(targetImageReference);

    EventDispatcher eventDispatcher =
        new DefaultEventDispatcher(projectProperties.getEventHandlers());
    boolean isTargetImageCredentialPresent =
        configureCredentialRetrievers(
            eventDispatcher,
            targetImage,
            targetImageReference,
            PropertyNames.TO_AUTH_USERNAME,
            PropertyNames.TO_AUTH_PASSWORD,
            rawConfiguration.getToAuth(),
            rawConfiguration.getAuthDescriptor("to"),
            rawConfiguration.getUsernameAuthDescriptor("to"),
            rawConfiguration.getPasswordAuthDescriptor("to"),
            rawConfiguration::getInferredAuth,
            rawConfiguration.getInferredAuthDescriptor(),
            rawConfiguration.getToCredHelper().orElse(null));

    PluginConfigurationProcessor processor =
        processCommonConfiguration(
            rawConfiguration,
            projectProperties,
            Containerizer.to(targetImage),
            targetImageReference,
            isTargetImageCredentialPresent);
    processor.getJibContainerBuilder().setFormat(rawConfiguration.getImageFormat());
    return processor;
  }

  @VisibleForTesting
  static PluginConfigurationProcessor processCommonConfiguration(
      RawConfiguration rawConfiguration,
      ProjectProperties projectProperties,
      Containerizer containerizer,
      ImageReference targetImageReference,
      boolean isTargetImageCredentialPresent)
      throws InvalidImageReferenceException, MainClassInferenceException, InvalidAppRootException,
          InferredAuthRetrievalException, IOException, InvalidWorkingDirectoryException,
          InvalidContainerVolumeException {
    JibSystemProperties.checkHttpTimeoutProperty();

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

    RegistryImage baseImage = RegistryImage.named(baseImageReference);
    boolean isBaseImageCredentialPresent =
        configureCredentialRetrievers(
            eventDispatcher,
            baseImage,
            baseImageReference,
            PropertyNames.FROM_AUTH_USERNAME,
            PropertyNames.FROM_AUTH_PASSWORD,
            rawConfiguration.getFromAuth(),
            rawConfiguration.getAuthDescriptor("from"),
            rawConfiguration.getUsernameAuthDescriptor("from"),
            rawConfiguration.getPasswordAuthDescriptor("from"),
            rawConfiguration::getInferredAuth,
            rawConfiguration.getInferredAuthDescriptor(),
            rawConfiguration.getFromCredHelper().orElse(null));

    JibContainerBuilder jibContainerBuilder =
        Jib.from(baseImage)
            .setLayers(projectProperties.getJavaLayerConfigurations().getLayerConfigurations())
            .setEntrypoint(computeEntrypoint(rawConfiguration, projectProperties))
            .setProgramArguments(rawConfiguration.getProgramArguments().orElse(null))
            .setEnvironment(rawConfiguration.getEnvironment())
            .setExposedPorts(ExposedPortsParser.parse(rawConfiguration.getPorts()))
            .setVolumes(getVolumesSet(rawConfiguration))
            .setLabels(rawConfiguration.getLabels())
            .setUser(rawConfiguration.getUser().orElse(null));
    getWorkingDirectoryChecked(rawConfiguration)
        .ifPresent(jibContainerBuilder::setWorkingDirectory);
    if (rawConfiguration.getUseCurrentTimestamp()) {
      eventDispatcher.dispatch(
          LogEvent.warn(
              "Setting image creation time to current time; your image may not be reproducible."));
      jibContainerBuilder.setCreationTime(Instant.now());
    }

    PluginConfigurationProcessor.configureContainerizer(
        containerizer, rawConfiguration, projectProperties);

    return new PluginConfigurationProcessor(
        jibContainerBuilder,
        containerizer,
        baseImageReference,
        targetImageReference,
        isBaseImageCredentialPresent,
        isTargetImageCredentialPresent);
  }

  /**
   * Parses the list of raw volumes directories to a set of {@link AbsoluteUnixPath}
   *
   * @param rawConfiguration raw configuration data
   * @return the set of parsed volumes.
   * @throws InvalidContainerVolumeException if {@code volumes} are not valid absolute Unix paths
   */
  @VisibleForTesting
  static Set<AbsoluteUnixPath> getVolumesSet(RawConfiguration rawConfiguration)
      throws InvalidContainerVolumeException {
    Set<AbsoluteUnixPath> volumes = new HashSet<>();
    for (String path : rawConfiguration.getVolumes()) {
      try {
        AbsoluteUnixPath absoluteUnixPath = AbsoluteUnixPath.get(path);
        volumes.add(absoluteUnixPath);
      } catch (IllegalArgumentException exception) {
        throw new InvalidContainerVolumeException(path, path, exception);
      }
    }

    return volumes;
  }

  /**
   * Gets the value of the {@code appRoot} parameter. If the parameter is empty, returns {@link
   * JavaLayerConfigurations#DEFAULT_WEB_APP_ROOT} for WAR projects or {@link
   * JavaLayerConfigurations#DEFAULT_APP_ROOT} for other projects.
   *
   * @param rawConfiguration raw configuration data
   * @param projectProperties used for providing additional information
   * @return the app root value
   * @throws InvalidAppRootException if {@code appRoot} value is not an absolute Unix path
   */
  @VisibleForTesting
  static AbsoluteUnixPath getAppRootChecked(
      RawConfiguration rawConfiguration, ProjectProperties projectProperties)
      throws InvalidAppRootException {
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
      throw new InvalidAppRootException(appRoot, appRoot, ex);
    }
  }

  @VisibleForTesting
  static Optional<AbsoluteUnixPath> getWorkingDirectoryChecked(RawConfiguration rawConfiguration)
      throws InvalidWorkingDirectoryException {
    if (!rawConfiguration.getWorkingDirectory().isPresent()) {
      return Optional.empty();
    }

    String path = rawConfiguration.getWorkingDirectory().get();
    try {
      return Optional.of(AbsoluteUnixPath.get(path));
    } catch (IllegalArgumentException ex) {
      throw new InvalidWorkingDirectoryException(path, path, ex);
    }
  }

  // TODO: find a way to reduce the number of arguments.
  private static boolean configureCredentialRetrievers(
      EventDispatcher eventDispatcher,
      RegistryImage registryImage,
      ImageReference imageReference,
      String usernamePropertyName,
      String passwordPropertyName,
      AuthProperty knownAuth,
      String knownAuthDescriptor,
      String knownAuthUsernameDescriptor,
      String knownAuthPasswordDescriptor,
      InferredAuthProvider inferredAuthProvider,
      String inferredAuthDescriptor,
      @Nullable String credHelper)
      throws FileNotFoundException, InferredAuthRetrievalException {
    DefaultCredentialRetrievers defaultCredentialRetrievers =
        DefaultCredentialRetrievers.init(
            CredentialRetrieverFactory.forImage(imageReference, eventDispatcher));
    Optional<Credential> optionalToCredential =
        ConfigurationPropertyValidator.getImageCredential(
            eventDispatcher,
            usernamePropertyName,
            passwordPropertyName,
            knownAuth,
            knownAuthUsernameDescriptor,
            knownAuthPasswordDescriptor);
    boolean credentialPresent = optionalToCredential.isPresent();
    if (optionalToCredential.isPresent()) {
      defaultCredentialRetrievers.setKnownCredential(
          optionalToCredential.get(), knownAuthDescriptor);
    } else {
      Optional<AuthProperty> optionalInferredAuth =
          inferredAuthProvider.getInferredAuth(imageReference.getRegistry());
      credentialPresent = optionalInferredAuth.isPresent();
      if (optionalInferredAuth.isPresent()) {
        AuthProperty auth = optionalInferredAuth.get();
        String username = Verify.verifyNotNull(auth.getUsername());
        String password = Verify.verifyNotNull(auth.getPassword());
        Credential credential = Credential.basic(username, password);
        defaultCredentialRetrievers.setInferredCredential(credential, inferredAuthDescriptor);
      }
    }
    defaultCredentialRetrievers.setCredentialHelper(credHelper);
    defaultCredentialRetrievers.asList().forEach(registryImage::addCredentialRetriever);

    return credentialPresent;
  }

  private static ImageReference getGeneratedTargetDockerTag(
      RawConfiguration rawConfiguration,
      ProjectProperties projectProperties,
      HelpfulSuggestions helpfulSuggestions)
      throws InvalidImageReferenceException {
    return ConfigurationPropertyValidator.getGeneratedTargetDockerTag(
        rawConfiguration.getToImage().orElse(null),
        new DefaultEventDispatcher(projectProperties.getEventHandlers()),
        projectProperties.getName(),
        projectProperties.getVersion().equals("unspecified")
            ? "latest"
            : projectProperties.getVersion(),
        helpfulSuggestions);
  }

  /**
   * Configures a {@link Containerizer} with values pulled from project properties/raw build
   * configuration.
   *
   * @param containerizer the {@link Containerizer} to configure
   * @param rawConfiguration the raw build configuration
   * @param projectProperties the project properties
   */
  private static void configureContainerizer(
      Containerizer containerizer,
      RawConfiguration rawConfiguration,
      ProjectProperties projectProperties) {
    containerizer
        .setToolName(projectProperties.getToolName())
        .setEventHandlers(projectProperties.getEventHandlers())
        .setAllowInsecureRegistries(rawConfiguration.getAllowInsecureRegistries())
        .setBaseImageLayersCache(Containerizer.DEFAULT_BASE_CACHE_DIRECTORY)
        .setApplicationLayersCache(projectProperties.getCacheDirectory());

    rawConfiguration.getToTags().forEach(containerizer::withAdditionalTag);

    if (rawConfiguration.getUseOnlyProjectCache()) {
      containerizer.setBaseImageLayersCache(projectProperties.getCacheDirectory());
    }
  }

  private final JibContainerBuilder jibContainerBuilder;
  private final ImageReference baseImageReference;
  private final ImageReference targetImageReference;
  private final boolean isBaseImageCredentialPresent;
  private final boolean isTargetImageCredentialPresent;
  private final Containerizer containerizer;

  private PluginConfigurationProcessor(
      JibContainerBuilder jibContainerBuilder,
      Containerizer containerizer,
      ImageReference baseImageReference,
      ImageReference targetImageReference,
      boolean isBaseImageCredentialPresent,
      boolean isTargetImageCredentialPresent) {
    this.jibContainerBuilder = jibContainerBuilder;
    this.containerizer = containerizer;
    this.baseImageReference = baseImageReference;
    this.targetImageReference = targetImageReference;
    this.isBaseImageCredentialPresent = isBaseImageCredentialPresent;
    this.isTargetImageCredentialPresent = isTargetImageCredentialPresent;
  }

  public JibContainerBuilder getJibContainerBuilder() {
    return jibContainerBuilder;
  }

  public Containerizer getContainerizer() {
    return containerizer;
  }

  public ImageReference getBaseImageReference() {
    return baseImageReference;
  }

  public ImageReference getTargetImageReference() {
    return targetImageReference;
  }

  public boolean isBaseImageCredentialPresent() {
    return isBaseImageCredentialPresent;
  }

  public boolean isTargetImageCredentialPresent() {
    return isTargetImageCredentialPresent;
  }
}
