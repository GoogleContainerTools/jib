/*
 * Copyright 2020 Google LLC.
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

package com.google.cloud.tools.jib.maven.extension;

import com.google.cloud.tools.jib.api.buildplan.ContainerBuildPlan;
import com.google.cloud.tools.jib.plugins.extension.ExtensionLogger;
import com.google.cloud.tools.jib.plugins.extension.JibPluginExtension;
import com.google.cloud.tools.jib.plugins.extension.JibPluginExtensionException;
import java.util.Map;

/**
 * Jib Maven plugin extension API.
 *
 * <p>If a class implementing the interface is visible on the classpath of the Jib Maven plugin and
 * the plugin is configured to load the extension class, the Jib plugin extension framework calls
 * the interface method of the class.
 */
public interface JibMavenPluginExtension extends JibPluginExtension {

  /**
   * Extends the build plan prepared by the Jib Maven plugin.
   *
   * @param buildPlan original build plan prepared by the Jib Maven plugin
   * @param properties custom properties configured for the plugin extension
   * @param mavenData {@link MavenData} providing Maven-specific data and properties
   * @param logger logger for writing log messages
   * @return updated build plan
   * @throws JibPluginExtensionException if an error occurs while running the plugin extension
   */
  ContainerBuildPlan extendContainerBuildPlan(
      ContainerBuildPlan buildPlan,
      Map<String, String> properties,
      MavenData mavenData,
      ExtensionLogger logger)
      throws JibPluginExtensionException;
}
