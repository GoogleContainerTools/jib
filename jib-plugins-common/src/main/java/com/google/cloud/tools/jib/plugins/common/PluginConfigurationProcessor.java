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
import com.google.cloud.tools.jib.api.Credential;
import com.google.cloud.tools.jib.api.DockerDaemonImage;
import com.google.cloud.tools.jib.api.ImageReference;
import com.google.cloud.tools.jib.api.InvalidImageReferenceException;
import com.google.cloud.tools.jib.api.JavaContainerBuilder;
import com.google.cloud.tools.jib.api.JavaContainerBuilder.LayerType;
import com.google.cloud.tools.jib.api.Jib;
import com.google.cloud.tools.jib.api.JibContainerBuilder;
import com.google.cloud.tools.jib.api.LogEvent;
import com.google.cloud.tools.jib.api.Ports;
import com.google.cloud.tools.jib.api.RegistryImage;
import com.google.cloud.tools.jib.api.TarImage;
import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.buildplan.FileEntriesLayer;
import com.google.cloud.tools.jib.api.buildplan.ImageFormat;
import com.google.cloud.tools.jib.api.buildplan.LayerObject;
import com.google.cloud.tools.jib.api.buildplan.ModificationTimeProvider;
import com.google.cloud.tools.jib.api.buildplan.Platform;
import com.google.cloud.tools.jib.frontend.CredentialRetrieverFactory;
import com.google.cloud.tools.jib.global.JibSystemProperties;
import com.google.cloud.tools.jib.plugins.common.RawConfiguration.ExtraDirectoriesConfiguration;
import com.google.cloud.tools.jib.plugins.common.RawConfiguration.PlatformConfiguration;
import com.google.cloud.tools.jib.plugins.common.globalconfig.GlobalConfig;
import com.google.cloud.tools.jib.plugins.extension.JibPluginExtensionException;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Verify;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimaps;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;

/**
 * Configures and provides {@code JibBuildRunner} for the image building tasks based on raw plugin
 * configuration values and project properties.
 */
public class PluginConfigurationProcessor {

  // Known "generated" dependencies -- these require that the underlying system run a build step
  // before they are available for sync'ing
  private static final ImmutableList<String> GENERATED_LAYERS =
      ImmutableList.of(
          LayerType.PROJECT_DEPENDENCIES.getName(),
          LayerType.RESOURCES.getName(),
          LayerType.CLASSES.getName());

  // Known "constant" layers -- changes to these layers require a change to the build definition,
  // which we consider non-syncable. These should not be included in the sync-map.
  private static final ImmutableList<String> CONST_LAYERS =
      ImmutableList.of(LayerType.DEPENDENCIES.getName(), LayerType.JVM_ARG_FILES.getName());

  private static final String DEFAULT_JETTY_APP_ROOT = "/var/lib/jetty/webapps/ROOT";

  private static final String JIB_CLASSPATH_FILE = "jib-classpath-file";
  private static final String JIB_MAIN_CLASS_FILE = "jib-main-class-file";

  /**
   * Generate a runner for image builds to docker daemon.
   *
   * @param rawConfiguration the raw configuration from the plugin
   * @param inferredAuthProvider the plugin specific auth provider
   * @param projectProperties an plugin specific implementation of {@link ProjectProperties}
   * @param globalConfig the Jib global config
   * @param helpfulSuggestions a plugin specific instance of {@link HelpfulSuggestions}
   * @return new {@link JibBuildRunner} to execute a build
   * @throws InvalidImageReferenceException if the image reference is invalid
   * @throws MainClassInferenceException if a main class could not be found
   * @throws InvalidAppRootException if the specific path for application root is invalid
   * @throws IOException if an error occurs creating the container builder
   * @throws InvalidWorkingDirectoryException if the working directory specified for the build is
   *     invalid
   * @throws InvalidPlatformException if there exists a {@link PlatformConfiguration} in the
   *     specified platforms list that is missing required fields or has invalid values
   * @throws InvalidContainerVolumeException if a specific container volume is invalid
   * @throws IncompatibleBaseImageJavaVersionException if the base image java version cannot support
   *     this build
   * @throws NumberFormatException if a string to number conversion operation fails
   * @throws InvalidContainerizingModeException if an invalid {@link ContainerizingMode} was
   *     specified
   * @throws InvalidFilesModificationTimeException if configured modification time could not be
   *     parsed
   * @throws InvalidCreationTimeException if configured creation time could not be parsed
   * @throws JibPluginExtensionException if an error occurred while running plugin extensions
   */
  public static JibBuildRunner createJibBuildRunnerForDockerDaemonImage(
      RawConfiguration rawConfiguration,
      InferredAuthProvider inferredAuthProvider,
      ProjectProperties projectProperties,
      GlobalConfig globalConfig,
      HelpfulSuggestions helpfulSuggestions)
      throws InvalidImageReferenceException, MainClassInferenceException, InvalidAppRootException,
          IOException, InvalidWorkingDirectoryException, InvalidPlatformException,
          InvalidContainerVolumeException, IncompatibleBaseImageJavaVersionException,
          NumberFormatException, InvalidContainerizingModeException,
          InvalidFilesModificationTimeException, InvalidCreationTimeException,
          JibPluginExtensionException {
    ImageReference targetImageReference =
        getGeneratedTargetDockerTag(rawConfiguration, projectProperties, helpfulSuggestions);
    DockerDaemonImage targetImage = DockerDaemonImage.named(targetImageReference);
    if (rawConfiguration.getDockerExecutable().isPresent()) {
      targetImage.setDockerExecutable(rawConfiguration.getDockerExecutable().get());
    }
    targetImage.setDockerEnvironment(rawConfiguration.getDockerEnvironment());

    Containerizer containerizer = Containerizer.to(targetImage);
    Multimaps.asMap(globalConfig.getRegistryMirrors()).forEach(containerizer::addRegistryMirrors);

    JibContainerBuilder jibContainerBuilder =
        processCommonConfiguration(
            rawConfiguration, inferredAuthProvider, projectProperties, containerizer);
    JibContainerBuilder updatedContainerBuilder =
        projectProperties
            .runPluginExtensions(rawConfiguration.getPluginExtensions(), jibContainerBuilder)
            .setFormat(ImageFormat.Docker);

    return JibBuildRunner.forBuildToDockerDaemon(
            updatedContainerBuilder,
            containerizer,
            projectProperties::log,
            helpfulSuggestions,
            targetImageReference,
            rawConfiguration.getToTags())
        .writeImageDigest(rawConfiguration.getDigestOutputPath())
        .writeImageId(rawConfiguration.getImageIdOutputPath())
        .writeImageJson(rawConfiguration.getImageJsonOutputPath());
  }

  /**
   * Generate a runner for image builds to tar file.
   *
   * @param rawConfiguration the raw configuration from the plugin
   * @param inferredAuthProvider the plugin specific auth provider
   * @param projectProperties an plugin specific implementation of {@link ProjectProperties}
   * @param globalConfig the Jib global config
   * @param helpfulSuggestions a plugin specific instance of {@link HelpfulSuggestions}
   * @return new {@link JibBuildRunner} to execute a build
   * @throws InvalidImageReferenceException if the image reference is invalid
   * @throws MainClassInferenceException if a main class could not be found
   * @throws InvalidAppRootException if the specific path for application root is invalid
   * @throws IOException if an error occurs creating the container builder
   * @throws InvalidWorkingDirectoryException if the working directory specified for the build is
   *     invalid
   * @throws InvalidPlatformException if there exists a {@link PlatformConfiguration} in the
   *     specified platforms list that is missing required fields or has invalid values
   * @throws InvalidContainerVolumeException if a specific container volume is invalid
   * @throws IncompatibleBaseImageJavaVersionException if the base image java version cannot support
   *     this build
   * @throws NumberFormatException if a string to number conversion operation fails
   * @throws InvalidContainerizingModeException if an invalid {@link ContainerizingMode} was
   *     specified
   * @throws InvalidFilesModificationTimeException if configured modification time could not be
   *     parsed
   * @throws InvalidCreationTimeException if configured creation time could not be parsed
   * @throws JibPluginExtensionException if an error occurred while running plugin extensions
   */
  public static JibBuildRunner createJibBuildRunnerForTarImage(
      RawConfiguration rawConfiguration,
      InferredAuthProvider inferredAuthProvider,
      ProjectProperties projectProperties,
      GlobalConfig globalConfig,
      HelpfulSuggestions helpfulSuggestions)
      throws InvalidImageReferenceException, MainClassInferenceException, InvalidAppRootException,
          IOException, InvalidWorkingDirectoryException, InvalidPlatformException,
          InvalidContainerVolumeException, IncompatibleBaseImageJavaVersionException,
          NumberFormatException, InvalidContainerizingModeException,
          InvalidFilesModificationTimeException, InvalidCreationTimeException,
          JibPluginExtensionException {
    ImageReference targetImageReference =
        getGeneratedTargetDockerTag(rawConfiguration, projectProperties, helpfulSuggestions);
    TarImage targetImage =
        TarImage.at(rawConfiguration.getTarOutputPath()).named(targetImageReference);

    Containerizer containerizer = Containerizer.to(targetImage);
    Multimaps.asMap(globalConfig.getRegistryMirrors()).forEach(containerizer::addRegistryMirrors);

    JibContainerBuilder jibContainerBuilder =
        processCommonConfiguration(
            rawConfiguration, inferredAuthProvider, projectProperties, containerizer);
    JibContainerBuilder updatedContainerBuilder =
        projectProperties.runPluginExtensions(
            rawConfiguration.getPluginExtensions(), jibContainerBuilder);

    return JibBuildRunner.forBuildTar(
            updatedContainerBuilder,
            containerizer,
            projectProperties::log,
            helpfulSuggestions,
            rawConfiguration.getTarOutputPath())
        .writeImageDigest(rawConfiguration.getDigestOutputPath())
        .writeImageId(rawConfiguration.getImageIdOutputPath())
        .writeImageJson(rawConfiguration.getImageJsonOutputPath());
  }

  /**
   * Generate a runner for image builds to registries.
   *
   * @param rawConfiguration the raw configuration from the plugin
   * @param inferredAuthProvider the plugin specific auth provider
   * @param projectProperties an plugin specific implementation of {@link ProjectProperties}
   * @param globalConfig the Jib global config
   * @param helpfulSuggestions a plugin specific instance of {@link HelpfulSuggestions}
   * @return new {@link JibBuildRunner} to execute a build
   * @throws InvalidImageReferenceException if the image reference is invalid
   * @throws MainClassInferenceException if a main class could not be found
   * @throws InvalidAppRootException if the specific path for application root is invalid
   * @throws IOException if an error occurs creating the container builder
   * @throws InvalidWorkingDirectoryException if the working directory specified for the build is
   *     invalid
   * @throws InvalidPlatformException if there exists a {@link PlatformConfiguration} in the
   *     specified platforms list that is missing required fields or has invalid values
   * @throws InvalidContainerVolumeException if a specific container volume is invalid
   * @throws IncompatibleBaseImageJavaVersionException if the base image java version cannot support
   *     this build
   * @throws NumberFormatException if a string to number conversion operation fails
   * @throws InvalidContainerizingModeException if an invalid {@link ContainerizingMode} was
   *     specified
   * @throws InvalidFilesModificationTimeException if configured modification time could not be
   *     parsed
   * @throws InvalidCreationTimeException if configured creation time could not be parsed
   * @throws JibPluginExtensionException if an error occurred while running plugin extensions
   */
  public static JibBuildRunner createJibBuildRunnerForRegistryImage(
      RawConfiguration rawConfiguration,
      InferredAuthProvider inferredAuthProvider,
      ProjectProperties projectProperties,
      GlobalConfig globalConfig,
      HelpfulSuggestions helpfulSuggestions)
      throws InvalidImageReferenceException, MainClassInferenceException, InvalidAppRootException,
          IOException, InvalidWorkingDirectoryException, InvalidPlatformException,
          InvalidContainerVolumeException, IncompatibleBaseImageJavaVersionException,
          NumberFormatException, InvalidContainerizingModeException,
          InvalidFilesModificationTimeException, InvalidCreationTimeException,
          JibPluginExtensionException {
    Preconditions.checkArgument(rawConfiguration.getToImage().isPresent());

    ImageReference targetImageReference = ImageReference.parse(rawConfiguration.getToImage().get());
    RegistryImage targetImage = RegistryImage.named(targetImageReference);

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

    boolean alwaysCacheBaseImage =
        Boolean.parseBoolean(
            rawConfiguration.getProperty(PropertyNames.ALWAYS_CACHE_BASE_IMAGE).orElse("false"));
    Containerizer containerizer =
        Containerizer.to(targetImage).setAlwaysCacheBaseImage(alwaysCacheBaseImage);
    Multimaps.asMap(globalConfig.getRegistryMirrors()).forEach(containerizer::addRegistryMirrors);

    JibContainerBuilder jibContainerBuilder =
        processCommonConfiguration(
            rawConfiguration, inferredAuthProvider, projectProperties, containerizer);
    JibContainerBuilder updatedContainerBuilder =
        projectProperties.runPluginExtensions(
            rawConfiguration.getPluginExtensions(), jibContainerBuilder);

    return JibBuildRunner.forBuildImage(
            updatedContainerBuilder,
            containerizer,
            projectProperties::log,
            helpfulSuggestions,
            targetImageReference,
            rawConfiguration.getToTags())
        .writeImageDigest(rawConfiguration.getDigestOutputPath())
        .writeImageId(rawConfiguration.getImageIdOutputPath())
        .writeImageJson(rawConfiguration.getImageJsonOutputPath());
  }

  /**
   * Generate a skaffold syncmap JSON string for an image build configuration.
   *
   * @param rawConfiguration the raw configuration from the plugin
   * @param projectProperties an plugin specific implementation of {@link ProjectProperties}
   * @param excludes a set of paths to exclude, directories include in this list will be expanded
   * @return new json string representation of the Sync Map
   * @throws InvalidImageReferenceException if the image reference is invalid
   * @throws MainClassInferenceException if a main class could not be found
   * @throws InvalidAppRootException if the specific path for application root is invalid
   * @throws IOException if an error occurs creating the container builder
   * @throws InvalidWorkingDirectoryException if the working directory specified for the build is
   *     invalid
   * @throws InvalidPlatformException if there exists a {@link PlatformConfiguration} in the
   *     specified platforms list that is missing required fields or has invalid values
   * @throws InvalidContainerVolumeException if a specific container volume is invalid
   * @throws IncompatibleBaseImageJavaVersionException if the base image java version cannot support
   *     this build
   * @throws NumberFormatException if a string to number conversion operation fails
   * @throws InvalidContainerizingModeException if an invalid {@link ContainerizingMode} was
   *     specified
   * @throws InvalidFilesModificationTimeException if configured modification time could not be
   *     parsed
   * @throws InvalidCreationTimeException if configured creation time could not be parsed
   */
  public static String getSkaffoldSyncMap(
      RawConfiguration rawConfiguration, ProjectProperties projectProperties, Set<Path> excludes)
      throws IOException, InvalidCreationTimeException, InvalidImageReferenceException,
          IncompatibleBaseImageJavaVersionException, InvalidPlatformException,
          InvalidContainerVolumeException, MainClassInferenceException, InvalidAppRootException,
          InvalidWorkingDirectoryException, InvalidFilesModificationTimeException,
          InvalidContainerizingModeException {
    JibContainerBuilder jibContainerBuilder =
        processCommonConfiguration(
            rawConfiguration, ignored -> Optional.empty(), projectProperties);
    SkaffoldSyncMapTemplate syncMap = new SkaffoldSyncMapTemplate();
    // since jib has already expanded out directories after processing everything, we just
    // ignore directories and provide only files to watch
    Set<Path> excludesExpanded = getAllFiles(excludes);
    for (LayerObject layerObject : jibContainerBuilder.toContainerBuildPlan().getLayers()) {
      Verify.verify(
          layerObject instanceof FileEntriesLayer,
          "layer types other than FileEntriesLayer not yet supported in build plan layers");
      FileEntriesLayer layer = (FileEntriesLayer) layerObject;
      if (CONST_LAYERS.contains(layer.getName())) {
        continue;
      }
      if (GENERATED_LAYERS.contains(layer.getName())) {
        layer
            .getEntries()
            .stream()
            .filter(layerEntry -> Files.isRegularFile(layerEntry.getSourceFile()))
            .filter(
                layerEntry ->
                    !excludesExpanded.contains(layerEntry.getSourceFile().toAbsolutePath()))
            .forEach(syncMap::addGenerated);
      } else { // this is a direct layer
        layer
            .getEntries()
            .stream()
            .filter(layerEntry -> Files.isRegularFile(layerEntry.getSourceFile()))
            .filter(
                layerEntry ->
                    !excludesExpanded.contains(layerEntry.getSourceFile().toAbsolutePath()))
            .forEach(syncMap::addDirect);
      }
    }
    return syncMap.getJsonString();
  }

  /** Expand directories to files (excludes directory paths). */
  static Set<Path> getAllFiles(Set<Path> paths) throws IOException {
    Set<Path> expanded = new HashSet<>();
    for (Path path : paths) {
      if (Files.isRegularFile(path)) {
        expanded.add(path);
      } else if (Files.isDirectory(path)) {
        try (Stream<Path> dirWalk = Files.walk(path)) {
          dirWalk.filter(Files::isRegularFile).forEach(expanded::add);
        }
      }
    }
    return expanded;
  }

  @VisibleForTesting
  static JibContainerBuilder processCommonConfiguration(
      RawConfiguration rawConfiguration,
      InferredAuthProvider inferredAuthProvider,
      ProjectProperties projectProperties)
      throws InvalidFilesModificationTimeException, InvalidAppRootException,
          IncompatibleBaseImageJavaVersionException, IOException, InvalidImageReferenceException,
          InvalidContainerizingModeException, MainClassInferenceException, InvalidPlatformException,
          InvalidContainerVolumeException, InvalidWorkingDirectoryException,
          InvalidCreationTimeException {

    // Create and configure JibContainerBuilder
    ModificationTimeProvider modificationTimeProvider =
        createModificationTimeProvider(rawConfiguration.getFilesModificationTime());
    JavaContainerBuilder javaContainerBuilder =
        getJavaContainerBuilderWithBaseImage(
                rawConfiguration, projectProperties, inferredAuthProvider)
            .setAppRoot(getAppRootChecked(rawConfiguration, projectProperties))
            .setModificationTimeProvider(modificationTimeProvider);
    JibContainerBuilder jibContainerBuilder =
        projectProperties.createJibContainerBuilder(
            javaContainerBuilder,
            getContainerizingModeChecked(rawConfiguration, projectProperties));
    jibContainerBuilder
        .setFormat(rawConfiguration.getImageFormat())
        .setPlatforms(getPlatformsSet(rawConfiguration))
        .setEntrypoint(computeEntrypoint(rawConfiguration, projectProperties, jibContainerBuilder))
        .setProgramArguments(rawConfiguration.getProgramArguments().orElse(null))
        .setEnvironment(rawConfiguration.getEnvironment())
        .setExposedPorts(Ports.parse(rawConfiguration.getPorts()))
        .setVolumes(getVolumesSet(rawConfiguration))
        .setLabels(rawConfiguration.getLabels())
        .setUser(rawConfiguration.getUser().orElse(null))
        .setCreationTime(getCreationTime(rawConfiguration.getCreationTime(), projectProperties));
    getWorkingDirectoryChecked(rawConfiguration)
        .ifPresent(jibContainerBuilder::setWorkingDirectory);

    // Adds all the extra files.
    for (ExtraDirectoriesConfiguration extraDirectory : rawConfiguration.getExtraDirectories()) {
      Path from = extraDirectory.getFrom();
      if (Files.exists(from)) {
        jibContainerBuilder.addFileEntriesLayer(
            JavaContainerBuilderHelper.extraDirectoryLayerConfiguration(
                from,
                AbsoluteUnixPath.get(extraDirectory.getInto()),
                extraDirectory.getIncludesList(),
                extraDirectory.getExcludesList(),
                rawConfiguration.getExtraDirectoryPermissions(),
                modificationTimeProvider));
      }
    }
    return jibContainerBuilder;
  }

  @VisibleForTesting
  static JibContainerBuilder processCommonConfiguration(
      RawConfiguration rawConfiguration,
      InferredAuthProvider inferredAuthProvider,
      ProjectProperties projectProperties,
      Containerizer containerizer)
      throws InvalidImageReferenceException, MainClassInferenceException, InvalidAppRootException,
          IOException, InvalidWorkingDirectoryException, InvalidPlatformException,
          InvalidContainerVolumeException, IncompatibleBaseImageJavaVersionException,
          NumberFormatException, InvalidContainerizingModeException,
          InvalidFilesModificationTimeException, InvalidCreationTimeException {
    JibSystemProperties.checkHttpTimeoutProperty();
    JibSystemProperties.checkProxyPortProperty();

    if (JibSystemProperties.sendCredentialsOverHttp()) {
      projectProperties.log(
          LogEvent.warn(
              "Authentication over HTTP is enabled. It is strongly recommended that you do not "
                  + "enable this on a public network!"));
    }

    configureContainerizer(containerizer, rawConfiguration, projectProperties);

    return processCommonConfiguration(rawConfiguration, inferredAuthProvider, projectProperties);
  }

  /**
   * Returns a {@link JavaContainerBuilder} with the correctly parsed base image configuration.
   *
   * @param rawConfiguration contains the base image configuration
   * @param projectProperties used for providing additional information
   * @param inferredAuthProvider provides inferred auths for registry images
   * @return a new {@link JavaContainerBuilder} with the configured base image
   * @throws IncompatibleBaseImageJavaVersionException when the Java version in the base image is
   *     incompatible with the Java version of the application to be containerized
   * @throws InvalidImageReferenceException if the base image configuration can't be parsed
   * @throws FileNotFoundException if a credential helper can't be found
   */
  @VisibleForTesting
  static JavaContainerBuilder getJavaContainerBuilderWithBaseImage(
      RawConfiguration rawConfiguration,
      ProjectProperties projectProperties,
      InferredAuthProvider inferredAuthProvider)
      throws IncompatibleBaseImageJavaVersionException, InvalidImageReferenceException,
          FileNotFoundException {
    // Use image configuration as-is if it's a local base image
    String baseImageConfig =
        rawConfiguration.getFromImage().isPresent()
            ? rawConfiguration.getFromImage().get()
            : getDefaultBaseImage(projectProperties);
    if (baseImageConfig.startsWith(Jib.TAR_IMAGE_PREFIX)) {
      return JavaContainerBuilder.from(baseImageConfig);
    }

    // Verify Java version is compatible
    String prefixRemoved = baseImageConfig.replaceFirst(".*://", "");
    int javaVersion = projectProperties.getMajorJavaVersion();
    if (isKnownJava8Image(prefixRemoved) && javaVersion > 8) {
      throw new IncompatibleBaseImageJavaVersionException(8, javaVersion);
    }
    if (isKnownJava11Image(prefixRemoved) && javaVersion > 11) {
      throw new IncompatibleBaseImageJavaVersionException(11, javaVersion);
    }

    ImageReference baseImageReference = ImageReference.parse(prefixRemoved);
    if (baseImageConfig.startsWith(Jib.DOCKER_DAEMON_IMAGE_PREFIX)) {
      DockerDaemonImage dockerDaemonImage =
          DockerDaemonImage.named(baseImageReference)
              .setDockerEnvironment(rawConfiguration.getDockerEnvironment());
      if (rawConfiguration.getDockerExecutable().isPresent()) {
        dockerDaemonImage.setDockerExecutable(rawConfiguration.getDockerExecutable().get());
      }
      return JavaContainerBuilder.from(dockerDaemonImage);
    }

    RegistryImage baseImage = RegistryImage.named(baseImageReference);
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
    return JavaContainerBuilder.from(baseImage);
  }

  /**
   * Computes the container entrypoint.
   *
   * <p>Computation occurs in this order:
   *
   * <ol>
   *   <li>null (inheriting from the base image), if the user specified value is {@code INHERIT}
   *   <li>the user specified one, if set
   *   <li>for a WAR project, null (inheriting) if a custom base image is specified, and {@code
   *       ["java", "-jar", "/usr/local/jetty/start.jar"]} otherwise (default Jetty base image)
   *   <li>for a non-WAR project, by resolving the main class
   * </ol>
   *
   * @param rawConfiguration raw configuration data
   * @param projectProperties used for providing additional information
   * @param jibContainerBuilder container builder
   * @return the entrypoint
   * @throws MainClassInferenceException if no valid main class is configured or discovered
   * @throws InvalidAppRootException if {@code appRoot} value is not an absolute Unix path
   * @throws InvalidContainerizingModeException if {@code containerizingMode} value is invalid
   */
  @Nullable
  @VisibleForTesting
  static List<String> computeEntrypoint(
      RawConfiguration rawConfiguration,
      ProjectProperties projectProperties,
      JibContainerBuilder jibContainerBuilder)
      throws MainClassInferenceException, InvalidAppRootException, IOException,
          InvalidContainerizingModeException {
    Optional<List<String>> rawEntrypoint = rawConfiguration.getEntrypoint();
    List<String> rawExtraClasspath = rawConfiguration.getExtraClasspath();

    if (projectProperties.isWarProject()) {
      if (rawEntrypoint.isPresent() && !rawEntrypoint.get().isEmpty()) {
        if (rawConfiguration.getMainClass().isPresent()
            || !rawConfiguration.getJvmFlags().isEmpty()
            || !rawExtraClasspath.isEmpty()
            || rawConfiguration.getExpandClasspathDependencies()) {
          projectProperties.log(
              LogEvent.warn(
                  "mainClass, extraClasspath, jvmFlags, and expandClasspathDependencies are ignored "
                      + "when entrypoint is specified"));
        }

        if (rawEntrypoint.get().size() == 1 && "INHERIT".equals(rawEntrypoint.get().get(0))) {
          return null;
        }
        return rawEntrypoint.get();
      }

      if (rawConfiguration.getMainClass().isPresent()
          || !rawConfiguration.getJvmFlags().isEmpty()
          || !rawExtraClasspath.isEmpty()
          || rawConfiguration.getExpandClasspathDependencies()) {
        projectProperties.log(
            LogEvent.warn(
                "mainClass, extraClasspath, jvmFlags, and expandClasspathDependencies are ignored "
                    + "for WAR projects"));
      }
      return rawConfiguration.getFromImage().isPresent()
          ? null // Inherit if a custom base image.
          : Arrays.asList("java", "-jar", "/usr/local/jetty/start.jar");
    }

    List<String> classpath = new ArrayList<>(rawExtraClasspath);
    AbsoluteUnixPath appRoot = getAppRootChecked(rawConfiguration, projectProperties);
    ContainerizingMode mode = getContainerizingModeChecked(rawConfiguration, projectProperties);
    switch (mode) {
      case EXPLODED:
        classpath.add(appRoot.resolve("resources").toString());
        classpath.add(appRoot.resolve("classes").toString());
        break;
      case PACKAGED:
        classpath.add(appRoot.resolve("classpath/*").toString());
        break;
      default:
        throw new IllegalStateException("unknown containerizing mode: " + mode);
    }

    if (projectProperties.getMajorJavaVersion() >= 9
        || rawConfiguration.getExpandClasspathDependencies()) {
      List<String> dependencies =
          projectProperties
              .getDependencies()
              .stream()
              .map(path -> appRoot.resolve("libs").resolve(path.getFileName()).toString())
              .collect(Collectors.toList());
      classpath.addAll(dependencies);
    } else {
      classpath.add(appRoot.resolve("libs/*").toString());
    }

    String classpathString = String.join(":", classpath);
    String mainClass =
        MainClassResolver.resolveMainClass(
            rawConfiguration.getMainClass().orElse(null), projectProperties);
    addJvmArgFilesLayer(
        rawConfiguration, projectProperties, jibContainerBuilder, classpathString, mainClass);

    if (projectProperties.getMajorJavaVersion() >= 9) {
      classpathString = "@" + appRoot.resolve(JIB_CLASSPATH_FILE);
    }

    if (rawEntrypoint.isPresent() && !rawEntrypoint.get().isEmpty()) {
      if (rawConfiguration.getMainClass().isPresent()
          || !rawConfiguration.getJvmFlags().isEmpty()
          || !rawExtraClasspath.isEmpty()
          || rawConfiguration.getExpandClasspathDependencies()) {
        projectProperties.log(
            LogEvent.warn(
                "mainClass, extraClasspath, jvmFlags, and expandClasspathDependencies are ignored "
                    + "when entrypoint is specified"));
      }

      if (rawEntrypoint.get().size() == 1 && "INHERIT".equals(rawEntrypoint.get().get(0))) {
        return null;
      }
      return rawEntrypoint.get();
    }

    List<String> entrypoint = new ArrayList<>(4 + rawConfiguration.getJvmFlags().size());
    entrypoint.add("java");
    entrypoint.addAll(rawConfiguration.getJvmFlags());
    entrypoint.add("-cp");
    entrypoint.add(classpathString);
    entrypoint.add(mainClass);
    return entrypoint;
  }

  // It's perfectly fine to always generate a new temp file or rewrite an existing file. However,
  // fixing the source file path and preserving the file timestamp prevents polluting the Jib layer
  // cache space by not creating new cache selectors every time. (Note, however, creating new
  // selectors does not affect correctness at all.)
  @VisibleForTesting
  static void addJvmArgFilesLayer(
      RawConfiguration rawConfiguration,
      ProjectProperties projectProperties,
      JibContainerBuilder jibContainerBuilder,
      String classpath,
      String mainClass)
      throws IOException, InvalidAppRootException {
    Path projectCache = projectProperties.getDefaultCacheDirectory();
    Path classpathFile = projectCache.resolve(JIB_CLASSPATH_FILE);
    Path mainClassFile = projectCache.resolve(JIB_MAIN_CLASS_FILE);

    writeFileConservatively(classpathFile, classpath);
    writeFileConservatively(mainClassFile, mainClass);

    AbsoluteUnixPath appRoot = getAppRootChecked(rawConfiguration, projectProperties);
    jibContainerBuilder.addFileEntriesLayer(
        FileEntriesLayer.builder()
            .setName(LayerType.JVM_ARG_FILES.getName())
            .addEntry(classpathFile, appRoot.resolve(JIB_CLASSPATH_FILE))
            .addEntry(mainClassFile, appRoot.resolve(JIB_MAIN_CLASS_FILE))
            .build());
  }

  /**
   * Writes a file only when needed (when the file does not exist or the existing file has a
   * different content). It reads the entire bytes into a {@code String} for content comparison, so
   * care should be taken when using this method for a huge file.
   *
   * @param file target file to write
   * @param content file content to write
   * @throws IOException if file I/O error
   */
  @VisibleForTesting
  static void writeFileConservatively(Path file, String content) throws IOException {
    if (Files.exists(file)) {
      String oldContent = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
      if (oldContent.equals(content)) {
        return;
      }
    }
    Files.createDirectories(file.getParent());
    Files.write(file, content.getBytes(StandardCharsets.UTF_8));
  }

  /**
   * Gets the suitable value for the base image. If the raw base image parameter is null, returns
   * {@code "jetty"} for WAR projects or {@code "adoptopenjdk:8-jre"} or {@code
   * "adoptopenjdk:11-jre"} for non-WAR.
   *
   * @param projectProperties used for providing additional information
   * @return the base image
   * @throws IncompatibleBaseImageJavaVersionException when the Java version in the base image is
   *     incompatible with the Java version of the application to be containerized
   */
  @VisibleForTesting
  static String getDefaultBaseImage(ProjectProperties projectProperties)
      throws IncompatibleBaseImageJavaVersionException {
    if (projectProperties.isWarProject()) {
      return "jetty";
    }
    int javaVersion = projectProperties.getMajorJavaVersion();
    if (javaVersion <= 8) {
      return "adoptopenjdk:8-jre";
    } else if (javaVersion <= 11) {
      return "adoptopenjdk:11-jre";
    }
    throw new IncompatibleBaseImageJavaVersionException(11, javaVersion);
  }

  /**
   * Parses the list of platforms to a set of {@link Platform}.
   *
   * @param rawConfiguration raw configuration data
   * @return the set of parsed platforms
   * @throws InvalidPlatformException if there exists a {@link PlatformConfiguration} in the
   *     specified platforms list that is missing required fields or has invalid values
   */
  @VisibleForTesting
  static Set<Platform> getPlatformsSet(RawConfiguration rawConfiguration)
      throws InvalidPlatformException {
    Set<Platform> platforms = new LinkedHashSet<>();
    for (PlatformConfiguration platformConfiguration : rawConfiguration.getPlatforms()) {

      String platformToString =
          "architecture="
              + platformConfiguration.getArchitectureName().orElse("<missing>")
              + ", os="
              + platformConfiguration.getOsName().orElse("<missing>");

      if (!platformConfiguration.getArchitectureName().isPresent()) {
        throw new InvalidPlatformException(
            "platform configuration is missing an architecture value", platformToString);
      }
      if (!platformConfiguration.getOsName().isPresent()) {
        throw new InvalidPlatformException(
            "platform configuration is missing an OS value", platformToString);
      }

      platforms.add(
          new Platform(
              platformConfiguration.getArchitectureName().get(),
              platformConfiguration.getOsName().get()));
    }
    return platforms;
  }

  /**
   * Parses the list of raw volumes directories to a set of {@link AbsoluteUnixPath}.
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
   * Gets the value of the {@code appRoot} parameter. If the parameter is empty, returns {@code
   * /var/lib/jetty/webapps/ROOT} for WAR projects or {@link JavaContainerBuilder#DEFAULT_APP_ROOT}
   * for other projects.
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
              ? DEFAULT_JETTY_APP_ROOT
              : JavaContainerBuilder.DEFAULT_APP_ROOT;
    }
    try {
      return AbsoluteUnixPath.get(appRoot);
    } catch (IllegalArgumentException ex) {
      throw new InvalidAppRootException(appRoot, appRoot, ex);
    }
  }

  static ContainerizingMode getContainerizingModeChecked(
      RawConfiguration rawConfiguration, ProjectProperties projectProperties)
      throws InvalidContainerizingModeException {
    ContainerizingMode mode = ContainerizingMode.from(rawConfiguration.getContainerizingMode());
    if (mode == ContainerizingMode.PACKAGED && projectProperties.isWarProject()) {
      throw new UnsupportedOperationException(
          "packaged containerizing mode for WAR is not yet supported");
    }
    return mode;
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

  /**
   * Creates a modification time provider based on the config value. The value can be:
   *
   * <ol>
   *   <li>{@code EPOCH_PLUS_SECOND} to create a provider which trims file modification time to
   *       EPOCH + 1 second
   *   <li>date in ISO 8601 format
   * </ol>
   *
   * @param modificationTime modification time config value
   * @return corresponding modification time provider
   * @throws InvalidFilesModificationTimeException if the config value is not in ISO 8601 format
   */
  @VisibleForTesting
  static ModificationTimeProvider createModificationTimeProvider(String modificationTime)
      throws InvalidFilesModificationTimeException {
    try {
      switch (modificationTime) {
        case "EPOCH_PLUS_SECOND":
          Instant epochPlusSecond = Instant.ofEpochSecond(1);
          return (ignored1, ignored2) -> epochPlusSecond;

        default:
          Instant timestamp =
              DateTimeFormatter.ISO_DATE_TIME.parse(modificationTime, Instant::from);
          return (ignored1, ignored2) -> timestamp;
      }

    } catch (DateTimeParseException ex) {
      throw new InvalidFilesModificationTimeException(modificationTime, modificationTime, ex);
    }
  }

  /**
   * Creates an {@link Instant} based on the config value. The value can be:
   *
   * <ol>
   *   <li>{@code EPOCH} to return epoch
   *   <li>{@code USE_CURRENT_TIMESTAMP} to return the current time
   *   <li>date in ISO 8601 format
   * </ol>
   *
   * @param configuredCreationTime the config value
   * @param projectProperties used for logging warnings
   * @return corresponding {@link Instant}
   * @throws InvalidCreationTimeException if the config value is invalid
   */
  @VisibleForTesting
  static Instant getCreationTime(String configuredCreationTime, ProjectProperties projectProperties)
      throws DateTimeParseException, InvalidCreationTimeException {
    try {
      switch (configuredCreationTime) {
        case "EPOCH":
          return Instant.EPOCH;

        case "USE_CURRENT_TIMESTAMP":
          projectProperties.log(
              LogEvent.warn(
                  "Setting image creation time to current time; your image may not be reproducible."));
          return Instant.now();

        default:
          DateTimeFormatter formatter =
              new DateTimeFormatterBuilder()
                  .append(DateTimeFormatter.ISO_DATE_TIME) // parses isoStrict
                  // add ability to parse with no ":" in tz
                  .optionalStart()
                  .appendOffset("+HHmm", "+0000")
                  .optionalEnd()
                  .toFormatter();
          return formatter.parse(configuredCreationTime, Instant::from);
      }
    } catch (DateTimeParseException ex) {
      throw new InvalidCreationTimeException(configuredCreationTime, configuredCreationTime, ex);
    }
  }

  // TODO: find a way to reduce the number of arguments.
  private static void configureCredentialRetrievers(
      RawConfiguration rawConfiguration,
      ProjectProperties projectProperties,
      RegistryImage registryImage,
      ImageReference imageReference,
      String usernamePropertyName,
      String passwordPropertyName,
      AuthProperty rawAuthConfiguration,
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
            rawAuthConfiguration,
            rawConfiguration);
    if (optionalCredential.isPresent()) {
      defaultCredentialRetrievers.setKnownCredential(
          optionalCredential.get(), rawAuthConfiguration.getAuthDescriptor());
    } else {
      try {
        Optional<AuthProperty> optionalInferredAuth =
            inferredAuthProvider.inferAuth(imageReference.getRegistry());
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
        .setToolVersion(projectProperties.getToolVersion())
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
   * Checks if the given image is a known Java 8 image. May return false negative.
   *
   * @param imageReference the image reference
   * @return {@code true} if the image is a known Java 8 image
   */
  private static boolean isKnownJava8Image(String imageReference) {
    return imageReference.startsWith("adoptopenjdk:8");
  }

  /**
   * Checks if the given image is a known Java 11 image. May return false negative.
   *
   * @param imageReference the image reference
   * @return {@code true} if the image is a known Java 11 image
   */
  private static boolean isKnownJava11Image(String imageReference) {
    return imageReference.startsWith("adoptopenjdk:11");
  }
}
