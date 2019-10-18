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
import com.google.cloud.tools.jib.api.CacheDirectoryCreationException;
import com.google.cloud.tools.jib.api.FilePermissions;
import com.google.cloud.tools.jib.api.InvalidImageReferenceException;
import com.google.cloud.tools.jib.maven.JibPluginConfiguration.PermissionConfiguration;
import com.google.cloud.tools.jib.plugins.common.HelpfulSuggestions;
import com.google.cloud.tools.jib.plugins.common.IncompatibleBaseImageJavaVersionException;
import com.google.cloud.tools.jib.plugins.common.InvalidAppRootException;
import com.google.cloud.tools.jib.plugins.common.InvalidContainerVolumeException;
import com.google.cloud.tools.jib.plugins.common.InvalidContainerizingModeException;
import com.google.cloud.tools.jib.plugins.common.InvalidCreationTimeException;
import com.google.cloud.tools.jib.plugins.common.InvalidFilesModificationTimeException;
import com.google.cloud.tools.jib.plugins.common.InvalidWorkingDirectoryException;
import com.google.cloud.tools.jib.plugins.common.MainClassInferenceException;
import com.google.cloud.tools.jib.plugins.common.VersionChecker;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.project.MavenProject;

/** Collection of common methods to share between Maven goals. */
public class MojoCommon {
  /** Describes a minimum required version or version range for Jib. */
  @VisibleForTesting
  public static final String REQUIRED_VERSION_PROPERTY_NAME = "jib.requiredVersion";

  @Deprecated
  static void checkUseCurrentTimestampDeprecation(JibPluginConfiguration jibPluginConfiguration) {
    if (jibPluginConfiguration.getUseCurrentTimestamp()) {
      if (!jibPluginConfiguration.getCreationTime().equals("EPOCH")) {
        throw new IllegalArgumentException(
            "You cannot configure both <container><useCurrentTimestamp> and "
                + "<container><creationTime>");
      }
      jibPluginConfiguration
          .getLog()
          .warn(
              "<container><useCurrentTimestamp> is deprecated; use <container><creationTime> with "
                  + "the value USE_CURRENT_TIMESTAMP instead");
    }
  }

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

  /**
   * Check that the actual version satisfies required Jib version range when specified. No check is
   * performed if the provided Jib version is {@code null}, which should only occur during debug.
   *
   * @param descriptor the plugin version
   * @throws MojoExecutionException if the version is not acceptable
   */
  public static void checkJibVersion(PluginDescriptor descriptor) throws MojoExecutionException {
    String acceptableVersionSpec = System.getProperty(MojoCommon.REQUIRED_VERSION_PROPERTY_NAME);
    if (acceptableVersionSpec == null) {
      return;
    }
    String actualVersion = descriptor.getVersion();
    if (actualVersion == null) {
      throw new MojoExecutionException("Could not determine Jib plugin version");
    }
    VersionChecker<DefaultArtifactVersion> checker =
        new VersionChecker<>(DefaultArtifactVersion::new);
    if (!checker.compatibleVersion(acceptableVersionSpec, actualVersion)) {
      String failure =
          String.format(
              "Jib plugin version is %s but is required to be %s",
              actualVersion, acceptableVersionSpec);
      throw new MojoExecutionException(failure);
    }
  }

  /**
   * Re-throws a Jib exception as a {@link MojoExecutionException} with an appropriate message.
   *
   * @param ex the exception to re-throw
   * @throws MojoExecutionException with the correct message
   */
  static void rethrowJibException(Exception ex) throws MojoExecutionException {
    if (ex instanceof InvalidAppRootException) {
      throw new MojoExecutionException(
          "<container><appRoot> is not an absolute Unix-style path: "
              + ((InvalidAppRootException) ex).getInvalidPathValue(),
          ex);
    }
    if (ex instanceof InvalidContainerizingModeException) {
      throw new MojoExecutionException(
          "invalid value for <containerizingMode>: "
              + ((InvalidContainerizingModeException) ex).getInvalidContainerizingMode(),
          ex);
    }
    if (ex instanceof InvalidWorkingDirectoryException) {
      throw new MojoExecutionException(
          "<container><workingDirectory> is not an absolute Unix-style path: "
              + ((InvalidWorkingDirectoryException) ex).getInvalidPathValue(),
          ex);
    }
    if (ex instanceof InvalidContainerVolumeException) {
      throw new MojoExecutionException(
          "<container><volumes> is not an absolute Unix-style path: "
              + ((InvalidContainerVolumeException) ex).getInvalidVolume(),
          ex);
    }
    if (ex instanceof InvalidFilesModificationTimeException) {
      throw new MojoExecutionException(
          "<container><filesModificationTime> should be an ISO 8601 date-time (see "
              + "DateTimeFormatter.ISO_DATE_TIME) or special keyword \"EPOCH_PLUS_SECOND\": "
              + ((InvalidFilesModificationTimeException) ex).getInvalidFilesModificationTime(),
          ex);
    }
    if (ex instanceof InvalidCreationTimeException) {
      throw new MojoExecutionException(
          "<container><creationTime> should be an ISO 8601 date-time (see "
              + "DateTimeFormatter.ISO_DATE_TIME) or a special keyword (\"EPOCH\", "
              + "\"USE_CURRENT_TIMESTAMP\"): "
              + ((InvalidCreationTimeException) ex).getInvalidCreationTime(),
          ex);
    }
    if (ex instanceof IncompatibleBaseImageJavaVersionException) {
      IncompatibleBaseImageJavaVersionException castedEx =
          (IncompatibleBaseImageJavaVersionException) ex;
      throw new MojoExecutionException(
          HelpfulSuggestions.forIncompatibleBaseImageJavaVersionForMaven(
              castedEx.getBaseImageMajorJavaVersion(), castedEx.getProjectMajorJavaVersion()),
          ex);
    }
    if (ex instanceof InvalidImageReferenceException) {
      throw new MojoExecutionException(
          HelpfulSuggestions.forInvalidImageReference(
              ((InvalidImageReferenceException) ex).getInvalidReference()),
          ex);
    }
    if (ex instanceof IOException
        || ex instanceof CacheDirectoryCreationException
        || ex instanceof MainClassInferenceException) {
      throw new MojoExecutionException(ex.getMessage(), ex);
    }
    throw new MojoExecutionException(ex.getMessage(), ex.getCause());
  }

  private MojoCommon() {}
}
