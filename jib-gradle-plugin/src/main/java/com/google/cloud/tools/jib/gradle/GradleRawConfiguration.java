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

import com.google.cloud.tools.jib.api.buildplan.FilePermissions;
import com.google.cloud.tools.jib.api.buildplan.ImageFormat;
import com.google.cloud.tools.jib.plugins.common.AuthProperty;
import com.google.cloud.tools.jib.plugins.common.RawConfiguration;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Gradle-specific adapter for providing raw configuration parameter values. */
public class GradleRawConfiguration implements RawConfiguration {

  private final JibExtension jibExtension;

  public GradleRawConfiguration(JibExtension jibExtension) {
    this.jibExtension = jibExtension;
  }

  @Override
  public Optional<String> getFromImage() {
    return Optional.ofNullable(jibExtension.getFrom().getImage());
  }

  @Override
  public AuthProperty getFromAuth() {
    return jibExtension.getFrom().getAuth();
  }

  @Override
  public CredHelperConfiguration getFromCredHelper() {
    return jibExtension.getFrom().getCredHelper();
  }

  @Override
  public Optional<String> getToImage() {
    return Optional.ofNullable(jibExtension.getTo().getImage());
  }

  @Override
  public AuthProperty getToAuth() {
    return jibExtension.getTo().getAuth();
  }

  @Override
  public CredHelperConfiguration getToCredHelper() {
    return jibExtension.getTo().getCredHelper();
  }

  @Override
  public Set<String> getToTags() {
    return jibExtension.getTo().getTags();
  }

  @Override
  public Optional<List<String>> getEntrypoint() {
    return Optional.ofNullable(jibExtension.getContainer().getEntrypoint());
  }

  @Override
  public Optional<List<String>> getProgramArguments() {
    return Optional.ofNullable(jibExtension.getContainer().getArgs());
  }

  @Override
  public List<String> getExtraClasspath() {
    return jibExtension.getContainer().getExtraClasspath();
  }

  @Override
  public boolean getExpandClasspathDependencies() {
    return jibExtension.getContainer().getExpandClasspathDependencies();
  }

  @Override
  public Optional<String> getMainClass() {
    return Optional.ofNullable(jibExtension.getContainer().getMainClass());
  }

  @Override
  public List<String> getJvmFlags() {
    return jibExtension.getContainer().getJvmFlags();
  }

  @Override
  public String getAppRoot() {
    return jibExtension.getContainer().getAppRoot();
  }

  @Override
  public Map<String, String> getEnvironment() {
    return jibExtension.getContainer().getEnvironment();
  }

  @Override
  public Map<String, String> getLabels() {
    return jibExtension.getContainer().getLabels().get();
  }

  @Override
  public List<String> getVolumes() {
    return jibExtension.getContainer().getVolumes();
  }

  @Override
  public List<String> getPorts() {
    return jibExtension.getContainer().getPorts();
  }

  @Override
  public Optional<String> getUser() {
    return Optional.ofNullable(jibExtension.getContainer().getUser());
  }

  @Override
  public Optional<String> getWorkingDirectory() {
    return Optional.ofNullable(jibExtension.getContainer().getWorkingDirectory());
  }

  @Override
  public boolean getAllowInsecureRegistries() {
    return jibExtension.getAllowInsecureRegistries();
  }

  @Override
  public ImageFormat getImageFormat() {
    return jibExtension.getContainer().getFormat();
  }

  @Override
  public Optional<String> getProperty(String propertyName) {
    return Optional.ofNullable(System.getProperty(propertyName));
  }

  @Override
  public String getFilesModificationTime() {
    return jibExtension.getContainer().getFilesModificationTime().get();
  }

  @Override
  public String getCreationTime() {
    return jibExtension.getContainer().getCreationTime().get();
  }

  @Override
  public List<? extends ExtraDirectoriesConfiguration> getExtraDirectories() {
    for (ExtraDirectoryParameters path : jibExtension.getExtraDirectories().getPaths()) {
      if (path.getFrom().equals(Paths.get(""))) {
        throw new IllegalArgumentException(
            "Incomplete extraDirectories.paths configuration; source directory must be set");
      }
    }
    return jibExtension.getExtraDirectories().getPaths();
  }

  @Override
  public Map<String, FilePermissions> getExtraDirectoryPermissions() {
    return TaskCommon.convertPermissionsMap(
        jibExtension.getExtraDirectories().getPermissions().get());
  }

  @Override
  public Optional<Path> getDockerExecutable() {
    return Optional.ofNullable(jibExtension.getDockerClient().getExecutablePath());
  }

  @Override
  public Map<String, String> getDockerEnvironment() {
    return jibExtension.getDockerClient().getEnvironment();
  }

  @Override
  public String getContainerizingMode() {
    return jibExtension.getContainerizingMode();
  }

  @Override
  public Path getTarOutputPath() {
    return jibExtension.getOutputPaths().getTarPath();
  }

  @Override
  public Path getDigestOutputPath() {
    return jibExtension.getOutputPaths().getDigestPath();
  }

  @Override
  public Path getImageIdOutputPath() {
    return jibExtension.getOutputPaths().getImageIdPath();
  }

  @Override
  public Path getImageJsonOutputPath() {
    return jibExtension.getOutputPaths().getImageJsonPath();
  }

  @Override
  public List<? extends ExtensionConfiguration> getPluginExtensions() {
    return jibExtension.getPluginExtensions().get();
  }

  @Override
  public List<? extends PlatformConfiguration> getPlatforms() {
    return jibExtension.getFrom().getPlatforms().get();
  }
}
