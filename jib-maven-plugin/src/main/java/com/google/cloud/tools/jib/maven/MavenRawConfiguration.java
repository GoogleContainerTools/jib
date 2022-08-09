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

import com.google.cloud.tools.jib.api.buildplan.FilePermissions;
import com.google.cloud.tools.jib.api.buildplan.ImageFormat;
import com.google.cloud.tools.jib.plugins.common.AuthProperty;
import com.google.cloud.tools.jib.plugins.common.RawConfiguration;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Maven-specific adapter for providing raw configuration parameter values. */
public class MavenRawConfiguration implements RawConfiguration {

  private final JibPluginConfiguration jibPluginConfiguration;

  /**
   * Creates a raw configuration instances.
   *
   * @param jibPluginConfiguration the Jib plugin configuration
   */
  public MavenRawConfiguration(JibPluginConfiguration jibPluginConfiguration) {
    this.jibPluginConfiguration = jibPluginConfiguration;
  }

  @Override
  public Optional<String> getFromImage() {
    return Optional.ofNullable(jibPluginConfiguration.getBaseImage());
  }

  @Override
  public AuthProperty getFromAuth() {
    return jibPluginConfiguration.getBaseImageAuth();
  }

  @Override
  public CredHelperConfiguration getFromCredHelper() {
    return jibPluginConfiguration.getBaseImageCredHelperConfig();
  }

  @Override
  public Optional<String> getToImage() {
    return Optional.ofNullable(jibPluginConfiguration.getTargetImage());
  }

  @Override
  public AuthProperty getToAuth() {
    return jibPluginConfiguration.getTargetImageAuth();
  }

  @Override
  public CredHelperConfiguration getToCredHelper() {
    return jibPluginConfiguration.getTargetImageCredentialHelperConfig();
  }

  @Override
  public Set<String> getToTags() {
    return jibPluginConfiguration.getTargetImageAdditionalTags();
  }

  @Override
  public Optional<List<String>> getEntrypoint() {
    return Optional.ofNullable(jibPluginConfiguration.getEntrypoint());
  }

  @Override
  public Optional<List<String>> getProgramArguments() {
    return Optional.ofNullable(jibPluginConfiguration.getArgs());
  }

  @Override
  public List<String> getExtraClasspath() {
    return jibPluginConfiguration.getExtraClasspath();
  }

  @Override
  public boolean getExpandClasspathDependencies() {
    return jibPluginConfiguration.getExpandClasspathDependencies();
  }

  @Override
  public Optional<String> getMainClass() {
    return Optional.ofNullable(jibPluginConfiguration.getMainClass());
  }

  @Override
  public List<String> getJvmFlags() {
    return jibPluginConfiguration.getJvmFlags();
  }

  @Override
  public String getAppRoot() {
    return jibPluginConfiguration.getAppRoot();
  }

  @Override
  public Map<String, String> getEnvironment() {
    return jibPluginConfiguration.getEnvironment();
  }

  @Override
  public Map<String, String> getLabels() {
    return jibPluginConfiguration.getLabels();
  }

  @Override
  public List<String> getVolumes() {
    return jibPluginConfiguration.getVolumes();
  }

  @Override
  public List<String> getPorts() {
    return jibPluginConfiguration.getExposedPorts();
  }

  @Override
  public Optional<String> getUser() {
    return Optional.ofNullable(jibPluginConfiguration.getUser());
  }

  @Override
  public Optional<String> getWorkingDirectory() {
    return Optional.ofNullable(jibPluginConfiguration.getWorkingDirectory());
  }

  @Override
  public boolean getAllowInsecureRegistries() {
    return jibPluginConfiguration.getAllowInsecureRegistries();
  }

  @Override
  public ImageFormat getImageFormat() {
    return ImageFormat.valueOf(jibPluginConfiguration.getFormat());
  }

  @Override
  public Optional<String> getProperty(String propertyName) {
    return Optional.ofNullable(jibPluginConfiguration.getProperty(propertyName));
  }

  @Override
  public String getFilesModificationTime() {
    return jibPluginConfiguration.getFilesModificationTime();
  }

  @Override
  public String getCreationTime() {
    return jibPluginConfiguration.getCreationTime();
  }

  @Override
  public List<? extends ExtraDirectoriesConfiguration> getExtraDirectories() {
    return MojoCommon.getExtraDirectories(jibPluginConfiguration);
  }

  @Override
  public Map<String, FilePermissions> getExtraDirectoryPermissions() {
    return MojoCommon.convertPermissionsList(jibPluginConfiguration.getExtraDirectoryPermissions());
  }

  @Override
  public Optional<Path> getDockerExecutable() {
    return Optional.ofNullable(jibPluginConfiguration.getDockerClientExecutable());
  }

  @Override
  public Map<String, String> getDockerEnvironment() {
    return jibPluginConfiguration.getDockerClientEnvironment();
  }

  @Override
  public String getContainerizingMode() {
    return jibPluginConfiguration.getContainerizingMode();
  }

  @Override
  public Path getTarOutputPath() {
    return jibPluginConfiguration.getTarOutputPath();
  }

  @Override
  public Path getDigestOutputPath() {
    return jibPluginConfiguration.getDigestOutputPath();
  }

  @Override
  public Path getImageIdOutputPath() {
    return jibPluginConfiguration.getImageIdOutputPath();
  }

  @Override
  public Path getImageJsonOutputPath() {
    return jibPluginConfiguration.getImageJsonOutputPath();
  }

  @Override
  public List<? extends ExtensionConfiguration> getPluginExtensions() {
    return jibPluginConfiguration.getPluginExtensions();
  }

  @Override
  public List<? extends PlatformConfiguration> getPlatforms() {
    return jibPluginConfiguration.getPlatforms();
  }
}
