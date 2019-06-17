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

import com.google.cloud.tools.jib.api.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.Containerizer;
import com.google.cloud.tools.jib.api.Credential;
import com.google.cloud.tools.jib.api.DockerDaemonImage;
import com.google.cloud.tools.jib.api.ImageReference;
import com.google.cloud.tools.jib.api.InvalidImageReferenceException;
import com.google.cloud.tools.jib.api.JavaContainerBuilder;
import com.google.cloud.tools.jib.api.JibContainerBuilder;
import com.google.cloud.tools.jib.api.LogEvent;
import com.google.cloud.tools.jib.api.Ports;
import com.google.cloud.tools.jib.api.RegistryImage;
import com.google.cloud.tools.jib.api.TarImage;
import com.google.cloud.tools.jib.frontend.CredentialRetrieverFactory;
import com.google.cloud.tools.jib.global.JibSystemProperties;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Verify;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Configures and provides {@code JibContainerBuilder} for the image building tasks based on raw
 * plugin configuration values and project properties.
 */
public class PluginConfigurationProcessor {

  public static PluginConfigurationProcessor processCommonConfigurationForDockerDaemonImage(
      RawConfiguration rawConfiguration,
      InferredAuthProvider inferredAuthProvider,
      ProjectProperties projectProperties,
      @Nullable Path dockerExecutable,
      @Nullable Map<String, String> dockerEnvironment,
      HelpfulSuggestions helpfulSuggestions)
      throws InvalidImageReferenceException, MainClassInferenceException, InvalidAppRootException,
          IOException, InvalidWorkingDirectoryException, InvalidContainerVolumeException,
          IncompatibleBaseImageJavaVersionException, NumberFormatException,
          InvalidContainerizingModeException {
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
        rawConfiguration,
        inferredAuthProvider,
        projectProperties,
        containerizer,
        targetImageReference,
        false);
  }

  public static PluginConfigurationProcessor processCommonConfigurationForTarImage(
      RawConfiguration rawConfiguration,
      InferredAuthProvider inferredAuthProvider,
      ProjectProperties projectProperties,
      Path tarImagePath,
      HelpfulSuggestions helpfulSuggestions)
      throws InvalidImageReferenceException, MainClassInferenceException, InvalidAppRootException,
          IOException, InvalidWorkingDirectoryException, InvalidContainerVolumeException,
          IncompatibleBaseImageJavaVersionException, NumberFormatException,
          InvalidContainerizingModeException {
    ImageReference targetImageReference =
        getGeneratedTargetDockerTag(rawConfiguration, projectProperties, helpfulSuggestions);
    TarImage targetImage = TarImage.named(targetImageReference).saveTo(tarImagePath);
    Containerizer containerizer = Containerizer.to(targetImage);

    return processCommonConfiguration(
        rawConfiguration,
        inferredAuthProvider,
        projectProperties,
        containerizer,
        targetImageReference,
        false);
  }

  public static PluginConfigurationProcessor processCommonConfigurationForRegistryImage(
      RawConfiguration rawConfiguration,
      InferredAuthProvider inferredAuthProvider,
      ProjectProperties projectProperties)
      throws InvalidImageReferenceException, MainClassInferenceException, InvalidAppRootException,
          IOException, InvalidWorkingDirectoryException, InvalidContainerVolumeException,
          IncompatibleBaseImageJavaVersionException, NumberFormatException,
          InvalidContainerizingModeException {
    Preconditions.checkArgument(rawConfiguration.getToImage().isPresent());

    ImageReference targetImageReference = ImageReference.parse(rawConfiguration.getToImage().get());
    RegistryImage targetImage = RegistryImage.named(targetImageReference);

    boolean isTargetImageCredentialPresent =
        configureCredentialRetrievers(
            rawConfiguration,
            projectProperties,
            targetImage,
            targetImageReference,
            PropertyNames.TO_AUTH_USERNAME,
            PropertyNames.TO_AUTH_PASSWORD,
            rawConfiguration.getToAuth(),
            inferredAuthProvider,
            rawConfiguration.getToCredHelper().orElse(null));

    PluginConfigurationProcessor processor =
        processCommonConfiguration(
            rawConfiguration,
            inferredAuthProvider,
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
      InferredAuthProvider inferredAuthProvider,
      ProjectProperties projectProperties,
      Containerizer containerizer,
      ImageReference targetImageReference,
      boolean isTargetImageCredentialPresent)
      throws InvalidImageReferenceException, MainClassInferenceException, InvalidAppRootException,
          IOException, InvalidWorkingDirectoryException, InvalidContainerVolumeException,
          IncompatibleBaseImageJavaVersionException, NumberFormatException,
          InvalidContainerizingModeException {
    JibSystemProperties.checkHttpTimeoutProperty();
    JibSystemProperties.checkProxyPortProperty();

    ImageReference baseImageReference =
        ImageReference.parse(getBaseImage(rawConfiguration, projectProperties));

    if (JibSystemProperties.isSendCredentialsOverHttpEnabled()) {
      projectProperties.log(
          LogEvent.warn(
              "Authentication over HTTP is enabled. It is strongly recommended that you do not "
                  + "enable this on a public network!"));
    }

    RegistryImage baseImage = RegistryImage.named(baseImageReference);
    boolean isBaseImageCredentialPresent =
        configureCredentialRetrievers(
            rawConfiguration,
            projectProperties,
            baseImage,
            baseImageReference,
            PropertyNames.FROM_AUTH_USERNAME,
            PropertyNames.FROM_AUTH_PASSWORD,
            rawConfiguration.getFromAuth(),
            inferredAuthProvider,
            rawConfiguration.getFromCredHelper().orElse(null));

    JibContainerBuilder jibContainerBuilder =
        projectProperties
            .createContainerBuilder(
                baseImage,
                getAppRootChecked(rawConfiguration, projectProperties),
                getContainerizingModeChecked(rawConfiguration, projectProperties))
            .setEntrypoint(computeEntrypoint(rawConfiguration, projectProperties))
            .setProgramArguments(rawConfiguration.getProgramArguments().orElse(null))
            .setEnvironment(rawConfiguration.getEnvironment())
            .setExposedPorts(Ports.parse(rawConfiguration.getPorts()))
            .setVolumes(getVolumesSet(rawConfiguration))
            .setLabels(rawConfiguration.getLabels())
            .setUser(rawConfiguration.getUser().orElse(null));
    getWorkingDirectoryChecked(rawConfiguration)
        .ifPresent(jibContainerBuilder::setWorkingDirectory);
    if (rawConfiguration.getUseCurrentTimestamp()) {
      projectProperties.log(
          LogEvent.warn(
              "Setting image creation time to current time; your image may not be reproducible."));
      jibContainerBuilder.setCreationTime(Instant.now());
    }

    // Adds all the extra files.
    for (Path directory : rawConfiguration.getExtraDirectories()) {
      if (Files.exists(directory)) {
        jibContainerBuilder.addLayer(
            JavaContainerBuilderHelper.extraDirectoryLayerConfiguration(
                directory, rawConfiguration.getExtraDirectoryPermissions()));
      }
    }

    configureContainerizer(containerizer, rawConfiguration, projectProperties);

    return new PluginConfigurationProcessor(
        jibContainerBuilder,
        containerizer,
        baseImageReference,
        targetImageReference,
        isBaseImageCredentialPresent,
        isTargetImageCredentialPresent);
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
   * @throws InvalidContainerizingModeException if {@code containerizingMode} value is invalid
   */
  @Nullable
  @VisibleForTesting
  static List<String> computeEntrypoint(
      RawConfiguration rawConfiguration, ProjectProperties projectProperties)
      throws MainClassInferenceException, InvalidAppRootException, IOException,
          InvalidContainerizingModeException {
    AbsoluteUnixPath appRoot = getAppRootChecked(rawConfiguration, projectProperties);

    Optional<List<String>> rawEntrypoint = rawConfiguration.getEntrypoint();
    List<String> rawExtraClasspath = rawConfiguration.getExtraClasspath();
    if (rawEntrypoint.isPresent() && !rawEntrypoint.get().isEmpty()) {
      if (rawConfiguration.getMainClass().isPresent()
          || !rawConfiguration.getJvmFlags().isEmpty()
          || !rawExtraClasspath.isEmpty()) {
        projectProperties.log(
            LogEvent.warn(
                "mainClass, extraClasspath, and jvmFlags are ignored when entrypoint is specified"));
      }

      if (rawEntrypoint.get().size() == 1 && "INHERIT".equals(rawEntrypoint.get().get(0))) {
        return null;
      }
      return rawEntrypoint.get();
    }

    if (projectProperties.isWarProject()) {
      return null;
    }

    List<String> classpath = new ArrayList<>(rawExtraClasspath);
    ContainerizingMode mode = getContainerizingModeChecked(rawConfiguration, projectProperties);
    switch (mode) {
      case EXPLODED:
        classpath.add(appRoot.resolve("resources").toString());
        classpath.add(appRoot.resolve("classes").toString());
        classpath.add(appRoot.resolve("libs/*").toString());
        break;
      case PACKAGED:
        classpath.add(appRoot.resolve("classpath/*").toString());
        classpath.add(appRoot.resolve("libs/*").toString());
        break;
      default:
        throw new IllegalStateException("unknown containerizing mode: " + mode);
    }

    String classpathString = String.join(":", classpath);
    String mainClass =
        MainClassResolver.resolveMainClass(
            rawConfiguration.getMainClass().orElse(null), projectProperties);

    List<String> entrypoint = new ArrayList<>(4 + rawConfiguration.getJvmFlags().size());
    entrypoint.add("java");
    entrypoint.addAll(rawConfiguration.getJvmFlags());
    entrypoint.add("-cp");
    entrypoint.add(classpathString);
    entrypoint.add(mainClass);
    return entrypoint;
  }

  /**
   * Gets the suitable value for the base image. If the raw base image parameter is null, returns
   * {@code "gcr.io/distroless/java/jetty"} for WAR projects or {@code "gcr.io/distroless/java"} for
   * non-WAR.
   *
   * @param rawConfiguration raw configuration data
   * @param projectProperties used for providing additional information
   * @return the base image
   * @throws IncompatibleBaseImageJavaVersionException when the Java version in the base image is
   *     incompatible with the Java version of the application to be containerized
   */
  @VisibleForTesting
  static String getBaseImage(RawConfiguration rawConfiguration, ProjectProperties projectProperties)
      throws IncompatibleBaseImageJavaVersionException {
    int javaVersion = projectProperties.getMajorJavaVersion();

    if (rawConfiguration.getFromImage().isPresent()) {
      String baseImage = rawConfiguration.getFromImage().get();

      if (isKnownDistrolessJava8Image(baseImage) && javaVersion > 8) {
        throw new IncompatibleBaseImageJavaVersionException(8, javaVersion);
      }
      if (isKnownDistrolessJava11Image(baseImage) && javaVersion > 11) {
        throw new IncompatibleBaseImageJavaVersionException(11, javaVersion);
      }
      return baseImage;
    }

    // Base image not configured; auto-pick Distroless.
    if (javaVersion <= 8) {
      return projectProperties.isWarProject()
          ? "gcr.io/distroless/java/jetty:java8"
          : "gcr.io/distroless/java:8";
    }
    if (javaVersion <= 11) {
      return projectProperties.isWarProject()
          ? "gcr.io/distroless/java/jetty:java11"
          : "gcr.io/distroless/java:11";
    }
    throw new IncompatibleBaseImageJavaVersionException(11, javaVersion);
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
   * JavaContainerBuilder#DEFAULT_WEB_APP_ROOT} for WAR projects or {@link
   * JavaContainerBuilder#DEFAULT_APP_ROOT} for other projects.
   *
   * @param rawConfiguration raw configuration data
   * @param projectProperties the project properties
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
              ? JavaContainerBuilder.DEFAULT_WEB_APP_ROOT
              : JavaContainerBuilder.DEFAULT_APP_ROOT;
    }
    try {
      return AbsoluteUnixPath.get(appRoot);
    } catch (IllegalArgumentException ex) {
      throw new InvalidAppRootException(appRoot, appRoot, ex);
    }
  }

  @VisibleForTesting
  static ContainerizingMode getContainerizingModeChecked(
      RawConfiguration rawConfiguration, ProjectProperties projectProperties)
      throws InvalidContainerizingModeException {
    String rawMode = rawConfiguration.getContainerizingMode();
    try {
      if (!rawMode.toLowerCase(Locale.US).equals(rawMode)) {
        throw new InvalidContainerizingModeException(rawMode, rawMode);
      }

      ContainerizingMode mode = ContainerizingMode.valueOf(rawMode.toUpperCase(Locale.US));
      if (mode == ContainerizingMode.PACKAGED && projectProperties.isWarProject()) {
        throw new UnsupportedOperationException(
            "packaged containerizing mode for WAR is not yet supported");
      }
      return mode;
    } catch (IllegalArgumentException ex) {
      throw new InvalidContainerizingModeException(rawMode, rawMode);
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
      RawConfiguration rawConfiguration,
      ProjectProperties projectProperties,
      RegistryImage registryImage,
      ImageReference imageReference,
      String usernamePropertyName,
      String passwordPropertyName,
      AuthProperty knownAuth,
      InferredAuthProvider inferredAuthProvider,
      @Nullable String credHelper)
      throws FileNotFoundException {
    DefaultCredentialRetrievers defaultCredentialRetrievers =
        DefaultCredentialRetrievers.init(
            CredentialRetrieverFactory.forImage(imageReference, projectProperties::log));
    Optional<Credential> optionalCredential =
        ConfigurationPropertyValidator.getImageCredential(
            projectProperties::log,
            usernamePropertyName,
            passwordPropertyName,
            knownAuth,
            rawConfiguration);
    boolean credentialPresent = optionalCredential.isPresent();
    if (optionalCredential.isPresent()) {
      defaultCredentialRetrievers.setKnownCredential(
          optionalCredential.get(), knownAuth.getAuthDescriptor());
    } else {
      try {
        Optional<AuthProperty> optionalInferredAuth =
            inferredAuthProvider.inferAuth(imageReference.getRegistry());
        credentialPresent = optionalInferredAuth.isPresent();
        if (optionalInferredAuth.isPresent()) {
          AuthProperty auth = optionalInferredAuth.get();
          String username = Verify.verifyNotNull(auth.getUsername());
          String password = Verify.verifyNotNull(auth.getPassword());
          Credential credential = Credential.from(username, password);
          defaultCredentialRetrievers.setInferredCredential(credential, auth.getAuthDescriptor());
        }
      } catch (InferredAuthException ex) {
        projectProperties.log(LogEvent.warn("InferredAuthException: " + ex.getMessage()));
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
        rawConfiguration.getToImage().orElse(null), projectProperties, helpfulSuggestions);
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
    projectProperties.configureEventHandlers(containerizer);
    containerizer
        .setOfflineMode(projectProperties.isOffline())
        .setToolName(projectProperties.getToolName())
        .setAllowInsecureRegistries(rawConfiguration.getAllowInsecureRegistries())
        .setBaseImageLayersCache(
            getCheckedCacheDirectory(
                PropertyNames.BASE_IMAGE_CACHE,
                Boolean.getBoolean(PropertyNames.USE_ONLY_PROJECT_CACHE)
                    ? projectProperties.getDefaultCacheDirectory()
                    : Containerizer.DEFAULT_BASE_CACHE_DIRECTORY))
        .setApplicationLayersCache(
            getCheckedCacheDirectory(
                PropertyNames.APPLICATION_CACHE, projectProperties.getDefaultCacheDirectory()));

    rawConfiguration.getToTags().forEach(containerizer::withAdditionalTag);
  }

  /**
   * Returns the value of a cache directory system property if it is set, otherwise returns {@code
   * defaultPath}.
   *
   * @param property the name of the system property to check
   * @param defaultPath the path to return if the system property isn't set
   * @return the value of a cache directory system property if it is set, otherwise returns {@code
   *     defaultPath}
   */
  private static Path getCheckedCacheDirectory(String property, Path defaultPath) {
    if (System.getProperty(property) != null) {
      return Paths.get(System.getProperty(property));
    }
    return defaultPath;
  }

  /**
   * Checks if the given image is a known Java 8 distroless image. Checking against only images
   * known to Java 8, the method may to return {@code false} for Java 8 distroless unknown to it.
   *
   * @param imageReference the image reference
   * @return {@code true} if the image is equal to one of the known Java 8 distroless images, else
   *     {@code false}
   */
  private static boolean isKnownDistrolessJava8Image(String imageReference) {
    // TODO: drop "latest", "debug", and the like once they no longer point to Java 8.
    return imageReference.equals("gcr.io/distroless/java")
        || imageReference.equals("gcr.io/distroless/java:latest")
        || imageReference.equals("gcr.io/distroless/java:debug")
        || imageReference.equals("gcr.io/distroless/java:8")
        || imageReference.equals("gcr.io/distroless/java:8-debug")
        || imageReference.equals("gcr.io/distroless/java/jetty")
        || imageReference.equals("gcr.io/distroless/java/jetty:latest")
        || imageReference.equals("gcr.io/distroless/java/jetty:debug")
        || imageReference.equals("gcr.io/distroless/java/jetty:java8")
        || imageReference.equals("gcr.io/distroless/java/jetty:java8-debug");
  }

  /**
   * Checks if the given image is a known Java 11 distroless image. Checking against only images
   * known to Java 11, the method may to return {@code false} for Java 11 distroless unknown to it.
   *
   * @param imageReference the image reference
   * @return {@code true} if the image is equal to one of the known Java 11 distroless images, else
   *     {@code false}
   */
  private static boolean isKnownDistrolessJava11Image(String imageReference) {
    // TODO: add "latest", "debug", and the like to this list once they point to Java 11.
    return imageReference.equals("gcr.io/distroless/java:11")
        || imageReference.equals("gcr.io/distroless/java:11-debug")
        || imageReference.equals("gcr.io/distroless/java/jetty:java11")
        || imageReference.equals("gcr.io/distroless/java/jetty:java11-debug");
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
