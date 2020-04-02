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

package com.google.cloud.tools.jib.plugins.extension;

import java.util.concurrent.ExecutionException;

/** Exception while running Jib plugin extensions. */
public class JibPluginExtensionException extends ExecutionException {

  private final Class<? extends JibPluginExtension> extensionClass;

  /**
   * Constructs a new exception.
   *
   * @param extensionClass plugin extension creating the exception
   * @param message the detail message
   */
  public JibPluginExtensionException(
      Class<? extends JibPluginExtension> extensionClass, String message) {
    super(message);
    this.extensionClass = extensionClass;
  }

  /**
   * Constructs a new exception.
   *
   * @param extensionClass plugin extension creating the exception
   * @param message the detail message
   * @param cause the cause
   */
  public JibPluginExtensionException(
      Class<? extends JibPluginExtension> extensionClass, String message, Throwable cause) {
    super(message, cause);
    this.extensionClass = extensionClass;
  }

  /**
   * Returns the originating extension class.
   *
   * @return originating extension class
   */
  public Class<? extends JibPluginExtension> getExtensionClass() {
    return extensionClass;
  }
}
