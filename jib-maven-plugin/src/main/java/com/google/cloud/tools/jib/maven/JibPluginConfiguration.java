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
import com.google.common.collect.ImmutableSet;
import java.io.File;
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

  /** Used to configure {@code extraDirectory.permissions} parameter. */
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

    @Parameter private boolean useCurrentTimestamp = false;

    @Nullable @Parameter private List<String> entrypoint;

    @Parameter private List<String> jvmFlags = Collections.emptyList();

    @Parameter private Map<String, String> environment = Collections.emptyMap();

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

  /** Configuration for the {@code extraDirectory} parameter. */
  public static class ExtraDirectoryParameters {

    @Nullable @Parameter private File path;

    @Parameter private List<PermissionConfiguration> permissions = Collections.emptyList();

    /**
     * Allows users to configure {@code path} using just {@code <extraDirectory>} instead of {@code
     * <extraDirectory><path>}.
     *
     * @param path the value to set {@code path} to
     */
    public void set(File path) {
      this.path = path;
    }

    @Nullable
    public File getPath() {
      return path;
    }
  }

  @Nullable
  @Parameter(defaultValue = "${session}", readonly = true)
  MavenSession session;

  @Nullable
  @Parameter(defaultValue = "${project}", readonly = true)
  private MavenProject project;

  @Parameter private FromConfiguration from = new FromConfiguration();

  @Parameter private ToConfiguration to = new ToConfiguration();

  @Parameter private ContainerParameters container = new ContainerParameters();

  // this parameter is cloned in FilesMojo
  @Parameter private ExtraDirectoryParameters extraDirectory = new ExtraDirectoryParameters();

  @Parameter(
      defaultValue = "false",
      required = true,
      property = PropertyNames.ALLOW_INSECURE_REGISTRIES)
  private boolean allowInsecureRegistries;

  @Parameter(defaultValue = "false", property = PropertyNames.SKIP)
  private boolean skip;

  @Nullable @Component protected SettingsDecrypter settingsDecrypter;

  @Nullable
  @Parameter(property = PropertyNames.PACKAGING_OVERRIDE)
  private String packagingOverride;

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
    if (System.getProperty(PropertyNames.FROM_IMAGE) != null) {
      return System.getProperty(PropertyNames.FROM_IMAGE);
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
    if (System.getProperty(PropertyNames.FROM_CRED_HELPER) != null) {
      return System.getProperty(PropertyNames.FROM_CRED_HELPER);
    }
    return Preconditions.checkNotNull(from).credHelper;
  }

  AuthConfiguration getBaseImageAuth() {
    // System properties are handled in ConfigurationPropertyValidator
    return from.auth;
  }

  /**
   * Gets the target image reference.
   *
   * @return the configured target image reference
   */
  @Nullable
  String getTargetImage() {
    if (System.getProperty(PropertyNames.TO_IMAGE_ALTERNATE) != null) {
      return System.getProperty(PropertyNames.TO_IMAGE_ALTERNATE);
    }
    if (System.getProperty(PropertyNames.TO_IMAGE) != null) {
      return System.getProperty(PropertyNames.TO_IMAGE);
    }
    return to.image;
  }

  /**
   * Gets the additional target image tags.
   *
   * @return the configured extra tags.
   */
  Set<String> getTargetImageAdditionalTags() {
    if (System.getProperty(PropertyNames.TO_TAGS) != null) {
      return ImmutableSet.copyOf(
          ConfigurationPropertyValidator.parseListProperty(
              System.getProperty(PropertyNames.TO_TAGS)));
    }
    return new HashSet<>(to.tags);
  }

  /**
   * Gets the target image credential helper.
   *
   * @return the configured target image credential helper name
   */
  @Nullable
  String getTargetImageCredentialHelperName() {
    if (System.getProperty(PropertyNames.TO_CRED_HELPER) != null) {
      return System.getProperty(PropertyNames.TO_CRED_HELPER);
    }
    return Preconditions.checkNotNull(to).credHelper;
  }

  AuthConfiguration getTargetImageAuth() {
    // System properties are handled in ConfigurationPropertyValidator
    return to.auth;
  }

  /**
   * Gets whether or not to use the current timestamp for the container build.
   *
   * @return {@code true} if the build should use the current timestamp, {@code false} if not
   */
  boolean getUseCurrentTimestamp() {
    if (System.getProperty(PropertyNames.CONTAINER_USE_CURRENT_TIMESTAMP) != null) {
      return Boolean.getBoolean(PropertyNames.CONTAINER_USE_CURRENT_TIMESTAMP);
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
    if (System.getProperty(PropertyNames.CONTAINER_ENTRYPOINT) != null) {
      return ConfigurationPropertyValidator.parseListProperty(
          System.getProperty(PropertyNames.CONTAINER_ENTRYPOINT));
    }
    return container.entrypoint;
  }

  /**
   * Gets the configured jvm flags.
   *
   * @return the configured jvm flags
   */
  List<String> getJvmFlags() {
    if (System.getProperty(PropertyNames.CONTAINER_JVM_FLAGS) != null) {
      return ConfigurationPropertyValidator.parseListProperty(
          System.getProperty(PropertyNames.CONTAINER_JVM_FLAGS));
    }
    return container.jvmFlags;
  }

  /**
   * Gets the configured environment variables.
   *
   * @return the configured environment variables
   */
  Map<String, String> getEnvironment() {
    if (System.getProperty(PropertyNames.CONTAINER_ENVIRONMENT) != null) {
      return ConfigurationPropertyValidator.parseMapProperty(
          System.getProperty(PropertyNames.CONTAINER_ENVIRONMENT));
    }
    return container.environment;
  }

  /**
   * Gets the name of the main class.
   *
   * @return the configured main class name
   */
  @Nullable
  String getMainClass() {
    if (System.getProperty(PropertyNames.CONTAINER_MAIN_CLASS) != null) {
      return System.getProperty(PropertyNames.CONTAINER_MAIN_CLASS);
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
    if (System.getProperty(PropertyNames.CONTAINER_USER) != null) {
      return System.getProperty(PropertyNames.CONTAINER_USER);
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
    if (System.getProperty(PropertyNames.CONTAINER_WORKING_DIRECTORY) != null) {
      return System.getProperty(PropertyNames.CONTAINER_WORKING_DIRECTORY);
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
    if (System.getProperty(PropertyNames.CONTAINER_ARGS) != null) {
      return ConfigurationPropertyValidator.parseListProperty(
          System.getProperty(PropertyNames.CONTAINER_ARGS));
    }
    return container.args;
  }

  /**
   * Gets the configured exposed ports.
   *
   * @return the configured exposed ports
   */
  List<String> getExposedPorts() {
    if (System.getProperty(PropertyNames.CONTAINER_PORTS) != null) {
      return ConfigurationPropertyValidator.parseListProperty(
          System.getProperty(PropertyNames.CONTAINER_PORTS));
    }
    return container.ports;
  }

  /**
   * Gets the configured volumes.
   *
   * @return the configured volumes
   */
  List<String> getVolumes() {
    if (System.getProperty(PropertyNames.CONTAINER_VOLUMES) != null) {
      return ConfigurationPropertyValidator.parseListProperty(
          System.getProperty(PropertyNames.CONTAINER_VOLUMES));
    }
    return container.volumes;
  }

  /**
   * Gets the configured labels.
   *
   * @return the configured labels
   */
  Map<String, String> getLabels() {
    if (System.getProperty(PropertyNames.CONTAINER_LABELS) != null) {
      return ConfigurationPropertyValidator.parseMapProperty(
          System.getProperty(PropertyNames.CONTAINER_LABELS));
    }
    return container.labels;
  }

  /**
   * Gets the configured app root directory.
   *
   * @return the configured app root directory
   */
  String getAppRoot() {
    if (System.getProperty(PropertyNames.CONTAINER_APP_ROOT) != null) {
      return System.getProperty(PropertyNames.CONTAINER_APP_ROOT);
    }
    return container.appRoot;
  }

  /**
   * Gets the configured container image format.
   *
   * @return the configured container image format
   */
  String getFormat() {
    if (System.getProperty(PropertyNames.CONTAINER_FORMAT) != null) {
      return System.getProperty(PropertyNames.CONTAINER_FORMAT);
    }
    return Preconditions.checkNotNull(container.format);
  }

  /**
   * Gets the configured extra directory path.
   *
   * @return the configured extra directory path
   */
  Optional<Path> getExtraDirectoryPath() {
    // TODO: Should inform user about nonexistent directory if using custom directory.
    if (System.getProperty(PropertyNames.EXTRA_DIRECTORY_PATH) != null) {
      return Optional.of(Paths.get(System.getProperty(PropertyNames.EXTRA_DIRECTORY_PATH)));
    }
    return extraDirectory.path == null
        ? Optional.empty()
        : Optional.of(extraDirectory.path.toPath());
  }

  /**
   * Gets the configured extra layer file permissions.
   *
   * @return the configured extra layer file permissions
   */
  List<PermissionConfiguration> getExtraDirectoryPermissions() {
    if (System.getProperty(PropertyNames.EXTRA_DIRECTORY_PERMISSIONS) != null) {
      return ConfigurationPropertyValidator.parseMapProperty(
              System.getProperty(PropertyNames.EXTRA_DIRECTORY_PERMISSIONS))
          .entrySet()
          .stream()
          .map(entry -> new PermissionConfiguration(entry.getKey(), entry.getValue()))
          .collect(Collectors.toList());
    }
    return extraDirectory.permissions;
  }

  boolean getAllowInsecureRegistries() {
    return allowInsecureRegistries;
  }

  boolean isSkipped() {
    return skip;
  }

  @Nullable
  String getPackagingOverride() {
    return packagingOverride;
  }

  SettingsDecrypter getSettingsDecrypter() {
    return Preconditions.checkNotNull(settingsDecrypter);
  }

  @VisibleForTesting
  void setProject(MavenProject project) {
    this.project = project;
  }
}
