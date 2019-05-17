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

import com.google.cloud.tools.jib.plugins.common.AuthProperty;
import com.google.cloud.tools.jib.plugins.common.ConfigurationPropertyValidator;
import com.google.cloud.tools.jib.plugins.common.PropertyNames;
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
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
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

  /** Configuration for {@code from} parameter, */
  public static class FromConfiguration {

    @Nullable
    @Parameter(required = true)
    private String image;

    @Nullable @Parameter private String credHelper;

    @Parameter private FromAuthConfiguration auth = new FromAuthConfiguration();
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

    @Parameter private boolean useCurrentTimestamp = false;

    @Nullable @Parameter private List<String> entrypoint;

    @Parameter private List<String> jvmFlags = Collections.emptyList();

    @Parameter private Map<String, String> environment = Collections.emptyMap();

    @Parameter private List<String> extraClasspath = Collections.emptyList();

    @Nullable @Parameter private String mainClass;

    @Nullable @Parameter private List<String> args;

    @Nullable
    @Parameter(required = true)
    private String format = "Docker";

    @Parameter private List<String> ports = Collections.emptyList();

    @Parameter private List<String> volumes = Collections.emptyList();

    @Parameter private Map<String, String> labels = Collections.emptyMap();

    @Parameter private String appRoot = "";

    @Nullable @Parameter private String user;

    @Nullable @Parameter private String workingDirectory;
  }

  /** Configuration for the {@code extraDirectories} parameter. */
  public static class ExtraDirectoriesParameters {

    @Parameter private List<File> paths = Collections.emptyList();

    @Parameter private List<PermissionConfiguration> permissions = Collections.emptyList();

    public List<File> getPaths() {
      return paths;
    }
  }

  /** Configuration for the {@code extraDirectory} parameter. */
  @Deprecated
  public static class ExtraDirectoryParameters {

    // retained for backward-compatibility for <extraDirectory><path>...<path></extraDirectory>
    @Deprecated @Nullable @Parameter private File path;

    @Deprecated @Parameter
    private List<PermissionConfiguration> permissions = Collections.emptyList();

    // Allows users to configure a single path using just <extraDirectory> instead of
    // <extraDirectory><path>.
    @Deprecated
    public void set(File path) {
      this.path = path;
    }

    @Deprecated
    public List<File> getPaths() {
      return path == null ? Collections.emptyList() : Collections.singletonList(path);
    }
  }

  @Nullable
  @Parameter(defaultValue = "${session}", readonly = true)
  private MavenSession session;

  @Nullable
  @Parameter(defaultValue = "${project}", readonly = true)
  private MavenProject project;

  @Parameter private FromConfiguration from = new FromConfiguration();

  @Parameter private ToConfiguration to = new ToConfiguration();

  @Parameter private ContainerParameters container = new ContainerParameters();

  // this parameter is cloned in FilesMojo
  @Deprecated @Parameter
  private ExtraDirectoryParameters extraDirectory = new ExtraDirectoryParameters();

  // this parameter is cloned in FilesMojo
  @Parameter private ExtraDirectoriesParameters extraDirectories = new ExtraDirectoriesParameters();

  @Parameter(
      defaultValue = "false",
      required = true,
      property = PropertyNames.ALLOW_INSECURE_REGISTRIES)
  private boolean allowInsecureRegistries;

  @Parameter(defaultValue = "false", property = PropertyNames.SKIP)
  private boolean skip;

  @Component protected SettingsDecrypter settingsDecrypter;

  MavenSession getSession() {
    return Preconditions.checkNotNull(session);
  }

  MavenProject getProject() {
    return Preconditions.checkNotNull(project);
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
    return Preconditions.checkNotNull(from).image;
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
    return Preconditions.checkNotNull(from).credHelper;
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
  String getTargetImage() {
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
    return Preconditions.checkNotNull(to).credHelper;
  }

  AuthConfiguration getTargetImageAuth() {
    // System/pom properties for auth are handled in ConfigurationPropertyValidator
    return to.auth;
  }

  /**
   * Gets whether or not to use the current timestamp for the container build.
   *
   * @return {@code true} if the build should use the current timestamp, {@code false} if not
   */
  boolean getUseCurrentTimestamp() {
    String property = getProperty(PropertyNames.CONTAINER_USE_CURRENT_TIMESTAMP);
    if (property != null) {
      return Boolean.parseBoolean(property);
    }
    return container.useCurrentTimestamp;
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
    return Preconditions.checkNotNull(container.format);
  }

  /**
   * Gets the list of configured extra directory paths.
   *
   * @return the list of configured extra directory paths
   */
  List<Path> getExtraDirectories() {
    // TODO: Should inform user about nonexistent directory if using custom directory.
    String deprecatedProperty = getProperty(PropertyNames.EXTRA_DIRECTORY_PATH);
    String newProperty = getProperty(PropertyNames.EXTRA_DIRECTORIES_PATHS);

    List<File> deprecatedPaths = extraDirectory.getPaths();
    List<File> newPaths = extraDirectories.getPaths();

    if (deprecatedProperty != null) {
      getLog()
          .warn(
              "The property 'jib.extraDirectory.path' is deprecated; "
                  + "use 'jib.extraDirectories.paths' instead");
    }
    if (!deprecatedPaths.isEmpty()) {
      getLog().warn("<extraDirectory> is deprecated; use <extraDirectories> with <paths><path>");
    }
    if (deprecatedProperty != null && newProperty != null) {
      throw new IllegalArgumentException(
          "You cannot configure both 'jib.extraDirectory.path' and 'jib.extraDirectories.paths'");
    }
    if (!deprecatedPaths.isEmpty() && !newPaths.isEmpty()) {
      throw new IllegalArgumentException(
          "You cannot configure both <extraDirectory> and <extraDirectories>");
    }

    String property = newProperty != null ? newProperty : deprecatedProperty;
    if (property != null) {
      List<String> paths = ConfigurationPropertyValidator.parseListProperty(property);
      return paths.stream().map(Paths::get).collect(Collectors.toList());
    }

    List<File> paths = !newPaths.isEmpty() ? newPaths : deprecatedPaths;
    return paths.stream().map(File::toPath).collect(Collectors.toList());
  }

  /**
   * Gets the configured extra layer file permissions.
   *
   * @return the configured extra layer file permissions
   */
  List<PermissionConfiguration> getExtraDirectoryPermissions() {
    String deprecatedProperty = getProperty(PropertyNames.EXTRA_DIRECTORY_PERMISSIONS);
    String newProperty = getProperty(PropertyNames.EXTRA_DIRECTORIES_PERMISSIONS);

    List<PermissionConfiguration> deprecatedPermissions = extraDirectory.permissions;
    List<PermissionConfiguration> newPermissions = extraDirectories.permissions;

    if (deprecatedProperty != null) {
      getLog()
          .warn(
              "The property 'jib.extraDirectory.permissions' is deprecated; "
                  + "use 'jib.extraDirectories.permissions' instead");
    }
    if (deprecatedProperty != null && newProperty != null) {
      throw new IllegalArgumentException(
          "You cannot configure both 'jib.extraDirectory.permissions' and "
              + "'jib.extraDirectories.permissions'");
    }
    if (!deprecatedPermissions.isEmpty() && !newPermissions.isEmpty()) {
      throw new IllegalArgumentException(
          "You cannot configure both <extraDirectory> and <extraDirectories>");
    }

    String property = newProperty != null ? newProperty : deprecatedProperty;
    if (property != null) {
      return ConfigurationPropertyValidator.parseMapProperty(property)
          .entrySet()
          .stream()
          .map(entry -> new PermissionConfiguration(entry.getKey(), entry.getValue()))
          .collect(Collectors.toList());
    }

    return !extraDirectories.getPaths().isEmpty()
        ? extraDirectories.permissions
        : extraDirectory.permissions;
  }

  boolean getAllowInsecureRegistries() {
    return allowInsecureRegistries;
  }

  boolean isSkipped() {
    return skip;
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
