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

import com.google.cloud.tools.jib.ProjectInfo;
import com.google.cloud.tools.jib.api.LogEvent;
import com.google.cloud.tools.jib.api.buildplan.FilePermissions;
import com.google.cloud.tools.jib.maven.JibPluginConfiguration.ExtraDirectoryParameters;
import com.google.cloud.tools.jib.maven.JibPluginConfiguration.PermissionConfiguration;
import com.google.cloud.tools.jib.plugins.common.ProjectProperties;
import com.google.cloud.tools.jib.plugins.common.PropertyNames;
import com.google.cloud.tools.jib.plugins.common.UpdateChecker;
import com.google.cloud.tools.jib.plugins.common.VersionChecker;
import com.google.cloud.tools.jib.plugins.common.globalconfig.GlobalConfig;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.Futures;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;

/** Collection of common methods to share between Maven goals. */
public class MojoCommon {
  /** Describes a minimum required version or version range for Jib. */
  @VisibleForTesting
  public static final String REQUIRED_VERSION_PROPERTY_NAME = "jib.requiredVersion";

  public static final String VERSION_URL = "https://storage.googleapis.com/jib-versions/jib-maven";

  static Future<Optional<String>> newUpdateChecker(
          ProjectProperties projectProperties, GlobalConfig globalConfig, Log logger) {
    if (projectProperties.isOffline()
            || !logger.isInfoEnabled()
            || globalConfig.isDisableUpdateCheck()) {
      return Futures.immediateFuture(Optional.empty());
    }
    ExecutorService executorService = Executors.newSingleThreadExecutor();
    try {
      return UpdateChecker.checkForUpdate(
              executorService,
              VERSION_URL,
              projectProperties.getToolName(),
              projectProperties.getToolVersion(),
              projectProperties::log);
    } finally {
      executorService.shutdown();
    }
  }

  static void finishUpdateChecker(
          ProjectProperties projectProperties, Future<Optional<String>> updateCheckFuture) {
    UpdateChecker.finishUpdateCheck(updateCheckFuture)
            .ifPresent(
                    updateMessage -> {
                      projectProperties.log(LogEvent.lifecycle(""));
                      projectProperties.log(LogEvent.lifecycle("\u001B[33m" + updateMessage + "\u001B[0m"));
                      projectProperties.log(
                              LogEvent.lifecycle(
                                      "\u001B[33m"
                                              + ProjectInfo.GITHUB_URL
                                              + "/blob/master/jib-maven-plugin/CHANGELOG.md\u001B[0m"));
                      projectProperties.log(
                              LogEvent.lifecycle(
                                      "Please see https://github.com/GoogleContainerTools/jib/blob/master/docs/privacy.md for info on disabling this update check."));
                      projectProperties.log(LogEvent.lifecycle(""));
                    });
  }

  /**
   * Gets the list of extra directory paths from a {@link JibPluginConfiguration}. Returns {@code
   * (project dir)/src/main/jib} by default if not configured.
   *
   * @param jibPluginConfiguration the build configuration
   * @return the list of resolved extra directories
   */
  static List<ExtraDirectoryParameters> getExtraDirectories(
          JibPluginConfiguration jibPluginConfiguration) {
    List<ExtraDirectoryParameters> extraDirectories = jibPluginConfiguration.getExtraDirectories();
    if (!extraDirectories.isEmpty()) {
      for (ExtraDirectoryParameters directory : extraDirectories) {
        if (directory.getFrom().equals(Paths.get(""))) {
          throw new IllegalArgumentException(
                  "Incomplete <extraDirectories><paths> configuration; source directory must be set");
        }
      }
      return extraDirectories;
    }

    MavenProject project = Preconditions.checkNotNull(jibPluginConfiguration.getProject());
    return Collections.singletonList(
            new ExtraDirectoryParameters(
                    project.getBasedir().toPath().resolve("src").resolve("main").resolve("jib").toFile(),
                    "/"));
  }

  /**
   * Converts a list of {@link PermissionConfiguration} to an equivalent {@code
   * String->FilePermission} map.
   *
   * @param permissionList the list to convert
   * @return the resulting map
   */
  static Map<String, FilePermissions> convertPermissionsList(
          List<PermissionConfiguration> permissionList) {
    // Order is important, so use a LinkedHashMap
    Map<String, FilePermissions> permissionsMap = new LinkedHashMap<>();
    for (PermissionConfiguration permission : permissionList) {
      if (!permission.getFile().isPresent() || !permission.getMode().isPresent()) {
        throw new IllegalArgumentException(
                "Incomplete <permission> configuration; requires <file> and <mode> fields to be set");
      }
      permissionsMap.put(
              permission.getFile().get(), FilePermissions.fromOctalString(permission.getMode().get()));
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
   * Determines if Jib goal execution on this project/module should be skipped due to configuration.
   *
   * @param jibPluginConfiguration usually {@code this}, the Mojo this check is applied in.
   * @return {@code true} if Jib should be skipped (should not execute goal), or {@code false} if it
   *     should continue with execution.
   */
  public static boolean shouldSkipJibExecution(JibPluginConfiguration jibPluginConfiguration) {
    Log log = jibPluginConfiguration.getLog();
    if (jibPluginConfiguration.isSkipped()) {
      log.info("Skipping containerization because jib-maven-plugin: skip = true");
      return true;
    }
    if (!jibPluginConfiguration.isContainerizable()) {
      log.info(
              "Skipping containerization of this module (not specified in "
                      + PropertyNames.CONTAINERIZE
                      + ")");
      return true;
    }
    if ("pom".equals(jibPluginConfiguration.getProject().getPackaging())) {
      log.info("Skipping containerization because packaging is 'pom'...");
      return true;
    }
    return false;
  }

  private MojoCommon() {}
}