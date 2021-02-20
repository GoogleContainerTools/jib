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

package com.google.cloud.tools.jib.plugins.common;

import com.google.cloud.tools.jib.plugins.common.RawConfiguration.ExtensionConfiguration;
import com.google.cloud.tools.jib.plugins.extension.JibPluginExtension;
import java.util.Optional;

/** To be implemented if dependency injection of extensions is supported by the build system. */
public interface ExtensionConfigurationWithInjectedPlugin extends ExtensionConfiguration {
  /**
   * The matching extension, if it has been injected.
   *
   * @return the extension
   */
  Optional<? extends JibPluginExtension> getInjectedExtension();
}
