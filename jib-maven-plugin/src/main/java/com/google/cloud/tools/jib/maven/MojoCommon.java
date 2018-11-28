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

import com.google.cloud.tools.jib.configuration.FilePermissions;
import com.google.cloud.tools.jib.filesystem.AbsoluteUnixPath;
import com.google.cloud.tools.jib.frontend.JavaLayerConfigurations;
import com.google.cloud.tools.jib.maven.JibPluginConfiguration.PermissionConfiguration;
import com.google.cloud.tools.jib.plugins.common.InvalidAppRootException;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Collection of common methods to share between Gradle tasks. */
class MojoCommon {

  /**
   * Gets the value of the {@code <container><appRoot>} parameter. If the parameter is empty,
   * returns {@link JavaLayerConfigurations#DEFAULT_WEB_APP_ROOT} for project with WAR packaging or
   * {@link JavaLayerConfigurations#DEFAULT_APP_ROOT} for other packaging.
   *
   * @param jibPluginConfiguration the Jib plugin configuration
   * @return the app root value
   * @throws InvalidAppRootException if the app root is not an absolute path in Unix-style
   */
  // TODO: find a way to use PluginConfigurationProcessor.getAppRootChecked() instead
  static AbsoluteUnixPath getAppRootChecked(JibPluginConfiguration jibPluginConfiguration)
      throws InvalidAppRootException {
    String appRoot = jibPluginConfiguration.getAppRoot();
    if (appRoot.isEmpty()) {
      boolean isWarProject = "war".equals(jibPluginConfiguration.getProject().getPackaging());
      appRoot =
          isWarProject
              ? JavaLayerConfigurations.DEFAULT_WEB_APP_ROOT
              : JavaLayerConfigurations.DEFAULT_APP_ROOT;
    }
    try {
      return AbsoluteUnixPath.get(appRoot);
    } catch (IllegalArgumentException ex) {
      throw new InvalidAppRootException(appRoot, appRoot, ex);
    }
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
        .orElse(
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
   * @param permissionList the list to convert
   * @return the resulting map
   */
  @VisibleForTesting
  static Map<AbsoluteUnixPath, FilePermissions> convertPermissionsList(
      List<PermissionConfiguration> permissionList) {
    Map<AbsoluteUnixPath, FilePermissions> permissionsMap = new HashMap<>();
    for (PermissionConfiguration permission : permissionList) {
      if (!permission.getFile().isPresent() || !permission.getMode().isPresent()) {
        throw new IllegalArgumentException(
            "Incomplete <permission> configuration; requires <file> and <mode> fields to be set");
      }
      AbsoluteUnixPath key = AbsoluteUnixPath.get(permission.getFile().get());
      FilePermissions value = FilePermissions.fromOctalString(permission.getMode().get());
      permissionsMap.put(key, value);
    }
    return permissionsMap;
  }

  private MojoCommon() {}
}
