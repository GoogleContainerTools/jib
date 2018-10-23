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

import com.google.cloud.tools.jib.filesystem.AbsoluteUnixPath;
import com.google.cloud.tools.jib.frontend.JavaLayerConfigurations;
import org.apache.maven.plugin.MojoExecutionException;

/** Collection of common methods to share between Gradle tasks. */
public class MojoCommon {

  /**
   * Gets the value of the {@code <container><appRoot>} parameter. If the parameter is empty,
   * returns {@link JavaLayerConfigurations#DEFAULT_WEB_APP_ROOT} for project with WAR packaging or
   * {@link JavaLayerConfigurations#DEFAULT_APP_ROOT} for other packaging.
   *
   * @param jibPluginConfiguration the Jib plugin configuration
   * @return the app root value
   * @throws MojoExecutionException if the app root is not an absolute path in Unix-style
   */
  // TODO: find a way to use PluginConfigurationProcessor.getAppRootChecked() instead
  static AbsoluteUnixPath getAppRootChecked(JibPluginConfiguration jibPluginConfiguration)
      throws MojoExecutionException {
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
      throw new MojoExecutionException(
          "<container><appRoot> is not an absolute Unix-style path: " + appRoot);
    }
  }

  /** Disables annoying Apache HTTP client logging. */
  // TODO: Instead of disabling logging, have authentication credentials be provided
  static void disableHttpLogging() {
    System.setProperty(
        "org.apache.commons.logging.Log", "org.apache.commons.logging.impl.SimpleLog");
    System.setProperty("org.apache.commons.logging.simplelog.defaultlog", "error");
  }
}
