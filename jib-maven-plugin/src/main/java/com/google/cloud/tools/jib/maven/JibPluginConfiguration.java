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

import com.google.cloud.tools.jib.maven.extension.JibMavenPluginExtension;
import com.google.cloud.tools.jib.plugins.common.AuthProperty;
import com.google.cloud.tools.jib.plugins.common.ConfigurationPropertyValidator;
import com.google.cloud.tools.jib.plugins.common.PropertyNames;
import com.google.cloud.tools.jib.plugins.common.RawConfiguration.ExtensionConfiguration;
import com.google.cloud.tools.jib.plugins.common.RawConfiguration.ExtraDirectoriesConfiguration;
import com.google.cloud.tools.jib.plugins.common.RawConfiguration.PlatformConfiguration;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import java.io.File;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.inject.Inject;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.crypto.SettingsDecrypter;

/** Defines the configuration parameters for Jib. Jib {@link Mojo}s should extend this class. */
public abstract class JibPluginConfiguration extends AbstractMojo {

  /** Base for {@link FromAuthConfiguration} and {@link ToAuthConfiguration}. */
  private static class AuthConfiguration implements AuthProperty {

    @Nullable @Parameter private String username;
    @Nullable @Parameter private String password;
    private final String source;

    private AuthConfiguration(String source) {
      this.source = source;
    }

    @Override
    @Nullable
    public String getUsername() {
      return username;
    }

    @Override
    @Nullable
    public String getPassword() {
      return password;
    }

    @Override
    public String getAuthDescriptor() {
      return "<" + source + "><auth>";
    }

    @Override
    public String getUsernameDescriptor() {
      return getAuthDescriptor() + "<username>";
    }

    @Override
    public String getPasswordDescriptor() {
      return getAuthDescriptor() + "<password>";
    }
  }

  /** Used to configure {@code from.auth} parameters. */
  public static class FromAuthConfiguration extends AuthConfiguration {

    public FromAuthConfiguration() {
      super("from");
    }
  }

  /** Used to configure {@code to.auth} parameters. */
  public static class ToAuthConfiguration extends AuthConfiguration {

    public ToAuthConfiguration() {
      super("to");
    }
  }

  /** Used to configure {@code extraDirectories.permissions} parameter. */
  public static class PermissionConfiguration {

    @Nullable @Parameter private String file;
    @Nullable @Parameter private String mode;

    // Need default constructor for Maven
    public PermissionConfiguration() {}

    @VisibleForTesting
    PermissionConfiguration(String file, String mode) {
      this.file = file;
      this.mode = mode;
    }

    Optional<String> getFile() {
      return Optional.ofNullable(file);
    }

    Optional<String> getMode() {
      return Optional.ofNullable(mode);
    }
  }

  /** Configuration for {@code platform} parameter. */
  public static class PlatformParameters implements PlatformConfiguration {

    @Nullable @Parameter private String os;
    @Nullable @Parameter private String architecture;

    @Override
    public Optional<String> getOsName() {
      return Optional.ofNullable(os);
    }

    @Override
    public Optional<String> getArchitectureName() {
      return Optional.ofNullable(architecture);
    }
  }

  /** Configuration for {@code from} parameter. */
  public static class FromConfiguration {

    @Nullable @Parameter private String image;
    @Nullable @Parameter private String credHelper;
    @Parameter private FromAuthConfiguration auth = new FromAuthConfiguration();
    @Parameter private List<PlatformParameters> platforms;

    /** Constructor for defaults. */
    public FromConfiguration() {
      PlatformParameters platform = new PlatformParameters();
      platform.os = "linux";
      platform.architecture = "amd64";
      platforms = Collections.singletonList(platform);
    }
  }

  /** Configuration for {@code to} parameter, where image is required. */
  public static class ToConfiguration {

    @Nullable @Parameter private String image;
    @Parameter private List<String> tags = Collections.emptyList();
    @Nullable @Parameter private String credHelper;
    @Parameter private ToAuthConfiguration auth = new ToAuthConfiguration();

    public void set(String image) {
      this.image = image;
    }
  }

  /** Configuration for {@code container} parameter. */
  public static class ContainerParameters {

    // Note: `entrypoint` and `args` are @Nullable to handle inheriting values from the base image

    @Nullable @Parameter private List<String> entrypoint;
    @Parameter private List<String> jvmFlags = Collections.emptyList();
    @Parameter private Map<String, String> environment = Collections.emptyMap();
    @Parameter private List<String> extraClasspath = Collections.emptyList();
    private boolean expandClasspathDependencies;
    @Nullable @Parameter private String mainClass;
    @Nullable @Parameter private List<String> args;
    @Parameter private String format = "Docker";
    @Parameter private List<String> ports = Collections.emptyList();
    @Parameter private List<String> volumes = Collections.emptyList();
    @Parameter private Map<String, String> labels = Collections.emptyMap();
    @Parameter private String appRoot = "";
    @Nullable @Parameter private String user;
    @Nullable @Parameter private String workingDirectory;
    @Parameter private String filesModificationTime = "EPOCH_PLUS_SECOND";
    @Parameter private String creationTime = "EPOCH";
  }

  /** Configuration for the {@code extraDirectories} parameter. */
  public static class ExtraDirectoriesParameters {

    @Parameter private List<ExtraDirectoryParameters> paths = Collections.emptyList();
    @Parameter private List<PermissionConfiguration> permissions = Collections.emptyList();

    public List<ExtraDirectoryParameters> getPaths() {
      return paths;
    }
  }

  /** A bean that configures the source and destination of an extra directory. */
  public static class ExtraDirectoryParameters implements ExtraDirectoriesConfiguration {

    @Parameter private File from = new File("");
    @Parameter private String into = "/";
    @Parameter private List<String> includes = Collections.emptyList();
    @Parameter private List<String> excludes = Collections.emptyList();

    // Need default constructor for Maven
    public ExtraDirectoryParameters() {}

    ExtraDirectoryParameters(File from, String into) {
      this.from = from;
      this.into = into;
    }

    // Allows <path>source</path> shorthand instead of forcing
    // <path><from>source</from><into>/</into></path>
    public void set(File path) {
      from = path;
      into = "/";
    }

    @Override
    public Path getFrom() {
      return from.toPath();
    }

    public void setFrom(File from) {
      this.from = from;
    }

    @Override
    public String getInto() {
      return into;
    }

    @Override
    public List<String> getIncludesList() {
      return includes;
    }

    @Override
    public List<String> getExcludesList() {
      return excludes;
    }
  }

  /** Configuration for the {@code dockerClient} parameter. */
  public static class DockerClientParameters {

    @Nullable @Parameter private File executable;
    @Parameter private Map<String, String> environment = Collections.emptyMap();
  }

  public static class OutputPathsParameters {

    @Nullable @Parameter private File tar;
    @Nullable @Parameter private File digest;
    @Nullable @Parameter private File imageId;
    @Nullable @Parameter private File imageJson;
  }

  public static class ExtensionParameters implements ExtensionConfiguration {

    @Parameter private String implementation = "<extension implementation not configured>";
    @Parameter private Map<String, String> properties = Collections.emptyMap();
    @Nullable @Parameter private Object configuration;

    @Override
    public String getExtensionClass() {
      return implementation;
    }

    @Override
    public Map<String, String> getProperties() {
      return properties;
    }

    @Override
    public Optional<Object> getExtraConfiguration() {
      return Optional.ofNullable(configuration);
    }
  }

  @Nullable
  @Parameter(defaultValue = "${session}", readonly = true)
  private MavenSession session;

  @Nullable
  @Parameter(defaultValue = "${project}", readonly = true)
  private MavenProject project;

  @Nullable
  @Parameter(defaultValue = "${plugin}", readonly = true)
  protected PluginDescriptor descriptor;

  @Component protected SettingsDecrypter settingsDecrypter;

  @Parameter private FromConfiguration from = new FromConfiguration();
  @Parameter private ToConfiguration to = new ToConfiguration();
  @Parameter private ContainerParameters container = new ContainerParameters();
  // this parameter is cloned in FilesMojo
  @Parameter private ExtraDirectoriesParameters extraDirectories = new ExtraDirectoriesParameters();
  @Parameter private DockerClientParameters dockerClient = new DockerClientParameters();
  @Parameter private OutputPathsParameters outputPaths = new OutputPathsParameters();

  @Parameter(property = PropertyNames.ALLOW_INSECURE_REGISTRIES)
  private boolean allowInsecureRegistries;

  @Parameter(property = PropertyNames.CONTAINERIZING_MODE)
  private String containerizingMode = "exploded";

  @Parameter(property = PropertyNames.SKIP)
  private boolean skip;

  @Parameter private List<ExtensionParameters> pluginExtensions = Collections.emptyList();
  @Inject private Set<JibMavenPluginExtension<?>> injectedPluginExtensions = Collections.emptySet();

  protected Set<JibMavenPluginExtension<?>> getInjectedPluginExtensions() {
    return injectedPluginExtensions;
  }

  protected MavenSession getSession() {
    return Preconditions.checkNotNull(session);
  }

  protected MavenProject getProject() {
    return Preconditions.checkNotNull(project);
  }

  protected void checkJibVersion() throws MojoExecutionException {
    Preconditions.checkNotNull(descriptor);
    MojoCommon.checkJibVersion(descriptor);
  }

  /**
   * Gets the specified platforms.
   *
   * @return the specified platforms
   */
  List<PlatformParameters> getPlatforms() {
    return from.platforms;
  }

  /**
   * Gets the base image reference.
   *
   * @return the configured base image reference
   */
  @Nullable
  String getBaseImage() {
    String property = getProperty(PropertyNames.FROM_IMAGE);
    if (property != null) {
      return property;
    }
    return from.image;
  }

  /**
   * Gets the base image credential helper.
   *
   * @return the configured base image credential helper name
   */
  @Nullable
  String getBaseImageCredentialHelperName() {
    String property = getProperty(PropertyNames.FROM_CRED_HELPER);
    if (property != null) {
      return property;
    }
    return from.credHelper;
  }

  AuthConfiguration getBaseImageAuth() {
    // System/pom properties for auth are handled in ConfigurationPropertyValidator
    return from.auth;
  }

  /**
   * Gets the target image reference.
   *
   * @return the configured target image reference
   */
  @Nullable
  protected String getTargetImage() {
    String propertyAlternate = getProperty(PropertyNames.TO_IMAGE_ALTERNATE);
    if (propertyAlternate != null) {
      return propertyAlternate;
    }
    String property = getProperty(PropertyNames.TO_IMAGE);
    if (property != null) {
      return property;
    }
    return to.image;
  }

  /**
   * Gets the additional target image tags.
   *
   * @return the configured extra tags.
   */
  Set<String> getTargetImageAdditionalTags() {
    String property = getProperty(PropertyNames.TO_TAGS);
    List<String> tags =
        property != null ? ConfigurationPropertyValidator.parseListProperty(property) : to.tags;
    if (tags.stream().anyMatch(Strings::isNullOrEmpty)) {
      String source = property != null ? PropertyNames.TO_TAGS : "<to><tags>";
      throw new IllegalArgumentException(source + " has empty tag");
    }
    return new HashSet<>(tags);
  }

  /**
   * Gets the target image credential helper.
   *
   * @return the configured target image credential helper name
   */
  @Nullable
  String getTargetImageCredentialHelperName() {
    String property = getProperty(PropertyNames.TO_CRED_HELPER);
    if (property != null) {
      return property;
    }
    return to.credHelper;
  }

  AuthConfiguration getTargetImageAuth() {
    // System/pom properties for auth are handled in ConfigurationPropertyValidator
    return to.auth;
  }

  /**
   * Gets the configured entrypoint.
   *
   * @return the configured entrypoint
   */
  @Nullable
  List<String> getEntrypoint() {
    String property = getProperty(PropertyNames.CONTAINER_ENTRYPOINT);
    if (property != null) {
      return ConfigurationPropertyValidator.parseListProperty(property);
    }
    return container.entrypoint;
  }

  /**
   * Gets the configured jvm flags.
   *
   * @return the configured jvm flags
   */
  List<String> getJvmFlags() {
    String property = getProperty(PropertyNames.CONTAINER_JVM_FLAGS);
    if (property != null) {
      return ConfigurationPropertyValidator.parseListProperty(property);
    }
    return container.jvmFlags;
  }

  /**
   * Gets the configured environment variables.
   *
   * @return the configured environment variables
   */
  Map<String, String> getEnvironment() {
    String property = getProperty(PropertyNames.CONTAINER_ENVIRONMENT);
    if (property != null) {
      return ConfigurationPropertyValidator.parseMapProperty(property);
    }
    return container.environment;
  }

  /**
   * Gets the extra classpath elements.
   *
   * @return the extra classpath elements
   */
  List<String> getExtraClasspath() {
    String property = getProperty(PropertyNames.CONTAINER_EXTRA_CLASSPATH);
    if (property != null) {
      return ConfigurationPropertyValidator.parseListProperty(property);
    }
    return container.extraClasspath;
  }

  /**
   * Returns whether to expand classpath dependencies.
   *
   * @return {@code true} to expand classpath dependencies. {@code false} otherwise.
   */
  public boolean getExpandClasspathDependencies() {
    String property = getProperty(PropertyNames.EXPAND_CLASSPATH_DEPENDENCIES);
    if (property != null) {
      return Boolean.valueOf(property);
    }
    return container.expandClasspathDependencies;
  }

  /**
   * Gets the name of the main class.
   *
   * @return the configured main class name
   */
  @Nullable
  String getMainClass() {
    String property = getProperty(PropertyNames.CONTAINER_MAIN_CLASS);
    if (property != null) {
      return property;
    }
    return container.mainClass;
  }

  /**
   * Gets the username or UID which the process in the container should run as.
   *
   * @return the configured main class name
   */
  @Nullable
  String getUser() {
    String property = getProperty(PropertyNames.CONTAINER_USER);
    if (property != null) {
      return property;
    }
    return container.user;
  }

  /**
   * Gets the working directory in the container.
   *
   * @return the working directory
   */
  @Nullable
  String getWorkingDirectory() {
    String property = getProperty(PropertyNames.CONTAINER_WORKING_DIRECTORY);
    if (property != null) {
      return property;
    }
    return container.workingDirectory;
  }

  /**
   * Gets the configured main arguments.
   *
   * @return the configured main arguments
   */
  @Nullable
  List<String> getArgs() {
    String property = getProperty(PropertyNames.CONTAINER_ARGS);
    if (property != null) {
      return ConfigurationPropertyValidator.parseListProperty(property);
    }
    return container.args;
  }

  /**
   * Gets the configured exposed ports.
   *
   * @return the configured exposed ports
   */
  List<String> getExposedPorts() {
    String property = getProperty(PropertyNames.CONTAINER_PORTS);
    if (property != null) {
      return ConfigurationPropertyValidator.parseListProperty(property);
    }
    return container.ports;
  }

  /**
   * Gets the configured volumes.
   *
   * @return the configured volumes
   */
  List<String> getVolumes() {
    String property = getProperty(PropertyNames.CONTAINER_VOLUMES);
    if (property != null) {
      return ConfigurationPropertyValidator.parseListProperty(property);
    }
    return container.volumes;
  }

  /**
   * Gets the configured labels.
   *
   * @return the configured labels
   */
  Map<String, String> getLabels() {
    String property = getProperty(PropertyNames.CONTAINER_LABELS);
    if (property != null) {
      return ConfigurationPropertyValidator.parseMapProperty(property);
    }
    return container.labels;
  }

  /**
   * Gets the configured app root directory.
   *
   * @return the configured app root directory
   */
  String getAppRoot() {
    String property = getProperty(PropertyNames.CONTAINER_APP_ROOT);
    if (property != null) {
      return property;
    }
    return container.appRoot;
  }

  /**
   * Gets the configured container image format.
   *
   * @return the configured container image format
   */
  String getFormat() {
    String property = getProperty(PropertyNames.CONTAINER_FORMAT);
    if (property != null) {
      return property;
    }
    return container.format;
  }

  /**
   * Gets the configured files modification time value.
   *
   * @return the configured files modification time value
   */
  String getFilesModificationTime() {
    String property = getProperty(PropertyNames.CONTAINER_FILES_MODIFICATION_TIME);
    if (property != null) {
      return property;
    }
    return container.filesModificationTime;
  }

  /**
   * Gets the configured container creation time value.
   *
   * @return the configured container creation time value
   */
  String getCreationTime() {
    String property = getProperty(PropertyNames.CONTAINER_CREATION_TIME);
    if (property != null) {
      return property;
    }
    return container.creationTime;
  }

  /**
   * Gets the list of configured extra directory paths.
   *
   * @return the list of configured extra directory paths
   */
  List<ExtraDirectoryParameters> getExtraDirectories() {
    // TODO: Should inform user about nonexistent directory if using custom directory.
    String property = getProperty(PropertyNames.EXTRA_DIRECTORIES_PATHS);
    if (property != null) {
      List<String> paths = ConfigurationPropertyValidator.parseListProperty(property);
      return paths.stream()
          .map(path -> new ExtraDirectoryParameters(new File(path), "/"))
          .collect(Collectors.toList());
    }
    return extraDirectories.getPaths();
  }

  /**
   * Gets the configured extra layer file permissions.
   *
   * @return the configured extra layer file permissions
   */
  List<PermissionConfiguration> getExtraDirectoryPermissions() {
    String property = getProperty(PropertyNames.EXTRA_DIRECTORIES_PERMISSIONS);
    if (property != null) {
      return ConfigurationPropertyValidator.parseMapProperty(property).entrySet().stream()
          .map(entry -> new PermissionConfiguration(entry.getKey(), entry.getValue()))
          .collect(Collectors.toList());
    }
    return extraDirectories.permissions;
  }

  @Nullable
  Path getDockerClientExecutable() {
    String property = getProperty(PropertyNames.DOCKER_CLIENT_EXECUTABLE);
    if (property != null) {
      return Paths.get(property);
    }
    return dockerClient.executable == null ? null : dockerClient.executable.toPath();
  }

  Map<String, String> getDockerClientEnvironment() {
    String property = getProperty(PropertyNames.DOCKER_CLIENT_ENVIRONMENT);
    if (property != null) {
      return ConfigurationPropertyValidator.parseMapProperty(property);
    }
    return dockerClient.environment;
  }

  Path getTarOutputPath() {
    Path configuredPath =
        outputPaths.tar == null
            ? Paths.get(getProject().getBuild().getDirectory()).resolve("jib-image.tar")
            : outputPaths.tar.toPath();
    return getRelativeToProjectRoot(configuredPath, PropertyNames.OUTPUT_PATHS_TAR);
  }

  Path getDigestOutputPath() {
    Path configuredPath =
        outputPaths.digest == null
            ? Paths.get(getProject().getBuild().getDirectory()).resolve("jib-image.digest")
            : outputPaths.digest.toPath();
    return getRelativeToProjectRoot(configuredPath, PropertyNames.OUTPUT_PATHS_DIGEST);
  }

  Path getImageIdOutputPath() {
    Path configuredPath =
        outputPaths.imageId == null
            ? Paths.get(getProject().getBuild().getDirectory()).resolve("jib-image.id")
            : outputPaths.imageId.toPath();
    return getRelativeToProjectRoot(configuredPath, PropertyNames.OUTPUT_PATHS_IMAGE_ID);
  }

  Path getImageJsonOutputPath() {
    Path configuredPath =
        outputPaths.imageJson == null
            ? Paths.get(getProject().getBuild().getDirectory()).resolve("jib-image.json")
            : outputPaths.imageJson.toPath();
    return getRelativeToProjectRoot(configuredPath, PropertyNames.OUTPUT_PATHS_IMAGE_JSON);
  }

  private Path getRelativeToProjectRoot(Path configuration, String propertyName) {
    String property = getProperty(propertyName);
    Path path = property != null ? Paths.get(property) : configuration;
    return path.isAbsolute() ? path : getProject().getBasedir().toPath().resolve(path);
  }

  boolean getAllowInsecureRegistries() {
    return allowInsecureRegistries;
  }

  public String getContainerizingMode() {
    String property = getProperty(PropertyNames.CONTAINERIZING_MODE);
    return property != null ? property : containerizingMode;
  }

  boolean isSkipped() {
    return skip;
  }

  List<ExtensionParameters> getPluginExtensions() {
    return pluginExtensions;
  }

  /**
   * Return false if the `jib.containerize` property is specified and does not match this
   * module/project. Used by the Skaffold-Jib binding.
   *
   * @return true if this module should be containerized
   */
  boolean isContainerizable() {
    String moduleSpecification = getProperty(PropertyNames.CONTAINERIZE);
    if (project == null || Strings.isNullOrEmpty(moduleSpecification)) {
      return true;
    }
    // modules can be specified in one of three ways:
    // 1) a `groupId:artifactId`
    // 2) an `:artifactId`
    // 3) relative path within the repository
    if (moduleSpecification.equals(project.getGroupId() + ":" + project.getArtifactId())
        || moduleSpecification.equals(":" + project.getArtifactId())) {
      return true;
    }
    // Relative paths never have a colon on *nix nor Windows.  This moduleSpecification could be an
    // :artifactId or groupId:artifactId for a different artifact.
    if (moduleSpecification.contains(":")) {
      return false;
    }
    try {
      Path projectBase = project.getBasedir().toPath();
      return projectBase.endsWith(moduleSpecification);
    } catch (InvalidPathException ex) {
      // ignore since moduleSpecification may not actually be a path
      return false;
    }
  }

  SettingsDecrypter getSettingsDecrypter() {
    return settingsDecrypter;
  }

  @VisibleForTesting
  void setProject(MavenProject project) {
    this.project = project;
  }

  @VisibleForTesting
  void setSession(MavenSession session) {
    this.session = session;
  }

  @Nullable
  String getProperty(String propertyName) {
    return MavenProjectProperties.getProperty(propertyName, project, session);
  }
}
