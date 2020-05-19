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
import java.util.Optional;

/**
 * Jib Maven plugin extension API.
 *
 * <p>If a class implementing the interface is visible on the classpath of the Jib Maven plugin and
 * the plugin is configured to load the extension class, the Jib plugin extension framework calls
 * the interface method of the class.
 */
public interface JibMavenPluginExtension<T> extends JibPluginExtension {

  /**
   * The type of an custom configuration defined by this extension. The configuration object is
   * mapped from {@code <pluginExtensions><pluginExtension><configuration>}. Often, it is sufficient
   * to leverage {@code <pluginExtensions><pluginExtension><properties>} and the extension may not
   * wish to define a custom configuration; in that case, use {@link Void} for &lt;T&gt; and have
   * this method return {@code Optional#empty()}. (Don't return {@code Optional.of(Void.class)}.)
   *
   * @return type of an extension-specific custom configuration; {@code Optional.empty()} if no need
   *     to define custom configuration
   */
  Optional<Class<T>> getExtraConfigType();

  /**
   * Extends the build plan prepared by the Jib Maven plugin.
   *
   * @param buildPlan original build plan prepared by the Jib Maven plugin
   * @param properties custom properties configured for the plugin extension
   * @param extraConfig extension-specific custom configuration mapped from {@code
   *     <pluginExtensions><pluginExtension><configuration>} of type &lt;T&gt;. {@link
   *     Optional#empty()} when {@link #getExtraConfigType()} returns {@link Optional#empty()} or
   *     {@code <configuration>} is not specified by the extension user.
   * @param mavenData {@link MavenData} providing Maven-specific data and properties
   * @param logger logger for writing log messages
   * @return updated build plan
   * @throws JibPluginExtensionException if an error occurs while running the plugin extension
   */
  ContainerBuildPlan extendContainerBuildPlan(
      ContainerBuildPlan buildPlan,
      Map<String, String> properties,
      Optional<T> extraConfig,
      MavenData mavenData,
      ExtensionLogger logger)
      throws JibPluginExtensionException;
}
