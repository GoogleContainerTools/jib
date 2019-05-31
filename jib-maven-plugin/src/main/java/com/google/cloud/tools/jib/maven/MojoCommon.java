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

import com.google.cloud.tools.jib.api.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.FilePermissions;
import com.google.cloud.tools.jib.maven.JibPluginConfiguration.PermissionConfiguration;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.maven.project.MavenProject;

/** Collection of common methods to share between Maven goals. */
class MojoCommon {

  /**
   * Gets the list of extra directory paths from a {@link JibPluginConfiguration}. Returns {@code
   * (project dir)/src/main/jib} by default if not configured.
   *
   * @param jibPluginConfiguration the build configuration
   * @return the list of resolved extra directories
   */
  static List<Path> getExtraDirectories(JibPluginConfiguration jibPluginConfiguration) {
    List<Path> paths = jibPluginConfiguration.getExtraDirectories();
    if (!paths.isEmpty()) {
      return paths;
    }

    MavenProject project = Preconditions.checkNotNull(jibPluginConfiguration.getProject());
    return Collections.singletonList(
        project.getBasedir().toPath().resolve("src").resolve("main").resolve("jib"));
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
