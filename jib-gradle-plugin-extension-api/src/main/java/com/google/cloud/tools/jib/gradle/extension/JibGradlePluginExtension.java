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

package com.google.cloud.tools.jib.gradle.extension;

import com.google.cloud.tools.jib.api.buildplan.ContainerBuildPlan;
import com.google.cloud.tools.jib.plugins.extension.ExtensionLogger;
import com.google.cloud.tools.jib.plugins.extension.JibPluginExtension;
import com.google.cloud.tools.jib.plugins.extension.JibPluginExtensionException;
import java.util.Map;
import java.util.Optional;

/**
 * Jib Gradle plugin extension API.
 *
 * <p>If a class implementing the interface is visible on the classpath of the Jib Gradle plugin and
 * the plugin is configured to load the extension class, the Jib plugin extension framework calls
 * the interface method of the class.
 */
public interface JibGradlePluginExtension<T> extends JibPluginExtension {

  /**
   * The type of an custom configuration defined by this extension. The configuration object is
   * mapped from {@code <pluginExtensions><pluginExtension><configuration>}. If the extension does
   * not wish to define a custom configuration, this method may return {@code Optional#empty()}.
   *
   * @return type of an extension-specific custom configuration
   */
  default Optional<Class<T>> getExtraConfigType() {
    return Optional.empty();
  }

  /**
   * Extends the build plan prepared by the Jib Gradle plugin.
   *
   * @param buildPlan original build plan prepared by the Jib Gradle plugin
   * @param properties custom properties configured for the plugin extension
   * @param extraConfig extension-specific custom configuration mapped from {@code
   *     jib.pluginExtensions.pluginExtension.configuration} of type <T>. {@link Optional#empty()}
   *     when {@link #getExtraConfigType()} returns {@link Optional#empty()} or {@code
   *     pluginExtension.configuration} is not specified by the extension user.
   * @param gradleData {@link GradleData} providing Gradle-specific data and properties
   * @param logger logger for writing log messages
   * @return updated build plan
   * @throws JibPluginExtensionException if an error occurs while running the plugin extension
   */
  ContainerBuildPlan extendContainerBuildPlan(
      ContainerBuildPlan buildPlan,
      Map<String, String> properties,
      Optional<T> extraConfig,
      GradleData gradleData,
      ExtensionLogger logger)
      throws JibPluginExtensionException;
}
