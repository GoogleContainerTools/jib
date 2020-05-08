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

import com.google.cloud.tools.jib.plugins.extension.JibPluginExtension;
import com.google.cloud.tools.jib.plugins.extension.JibPluginExtensionException;

/** Holds useful and convenient methods for Jib Maven Plugin extensions. */
public class MavenExtensionUtil {

  /**
   * Checks the type of {@code config} corresponding to the extension-specific {@code
   * <configuration>} defined in Maven POM and returns the same {@code config} after type-casting
   * into {@code configClass}. This is useful to ensure the type of the {@code config} object at the
   * beginning of an extension implementation, which throws an exception with detailed information
   * and instructions to help the extension user fix the configuration error in POM.
   *
   * @param <T> expected type ({@code configClass}) for {@code config}
   * @param extensionClass plugin extension running the method
   * @param configClass expected extension-specific configuration class
   * @param config configuration object parsed from {@code <configuration>} whose expected type is
   *     {@code configClass}. Can be null.
   * @return the same {@code config} after type-casting into {@code configClass}. Can be null.
   * @throws JibPluginExtensionException if {@code config} is not of type {@code configClass}
   */
  @SuppressWarnings("unchecked")
  public static <T> T checkConfigObject(
      Class<? extends JibPluginExtension> extensionClass, Class<T> configClass, Object config)
      throws JibPluginExtensionException {
    if (config == null || configClass.isInstance(config)) {
      return (T) config;
    }
    throw new JibPluginExtensionException(
        extensionClass,
        "<configuration> for "
            + extensionClass.getSimpleName()
            + " defined in POM is not of type "
            + configClass.getTypeName()
            + "; make sure to define it with <configuration implementation=\""
            + configClass.getTypeName()
            + "\">");
  }
}
