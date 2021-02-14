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

import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.buildplan.FilePermissions;
import com.google.cloud.tools.jib.api.buildplan.ImageFormat;
import com.google.cloud.tools.jib.plugins.extension.JibPluginExtension;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Holds raw plugin configuration parameter values. Acts as a common adapter for heterogeneous
 * plugin configuration models.
 */
public interface RawConfiguration {

  static interface ExtensionConfiguration {

    String getExtensionClass();

    Map<String, String> getProperties();

    Optional<Object> getExtraConfiguration();
    
    /**
     * To be implemented if dependency injection of extensions is supported by the build system.
     * 
     * @return the matching extension, if it has been injected.
     */
    default Optional<? extends JibPluginExtension> getInjectedExtension() {
      return Optional.empty();
    }
  }

  static interface PlatformConfiguration {

    Optional<String> getOsName();

    Optional<String> getArchitectureName();
  }

  Optional<String> getFromImage();

  Optional<String> getToImage();

  AuthProperty getFromAuth();

  AuthProperty getToAuth();

  Optional<String> getFromCredHelper();

  Optional<String> getToCredHelper();

  List<? extends PlatformConfiguration> getPlatforms();

  Set<String> getToTags();

  Optional<List<String>> getEntrypoint();

  List<String> getExtraClasspath();

  boolean getExpandClasspathDependencies();

  Optional<List<String>> getProgramArguments();

  Optional<String> getMainClass();

  List<String> getJvmFlags();

  String getAppRoot();

  Map<String, String> getEnvironment();

  Map<String, String> getLabels();

  List<String> getVolumes();

  List<String> getPorts();

  Optional<String> getUser();

  Optional<String> getWorkingDirectory();

  boolean getAllowInsecureRegistries();

  ImageFormat getImageFormat();

  Optional<String> getProperty(String propertyName);

  String getFilesModificationTime();

  String getCreationTime();

  Map<Path, AbsoluteUnixPath> getExtraDirectories();

  Map<String, FilePermissions> getExtraDirectoryPermissions();

  Optional<Path> getDockerExecutable();

  Map<String, String> getDockerEnvironment();

  String getContainerizingMode();

  Path getTarOutputPath();

  Path getDigestOutputPath();

  Path getImageIdOutputPath();

  Path getImageJsonOutputPath();

  List<? extends ExtensionConfiguration> getPluginExtensions();
}
