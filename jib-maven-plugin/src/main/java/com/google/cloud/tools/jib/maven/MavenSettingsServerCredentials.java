/*
 * Copyright 2018 Google LLC. All rights reserved.
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

import com.google.cloud.tools.jib.http.Authorizations;
import com.google.cloud.tools.jib.registry.credentials.RegistryCredentials;
import com.google.common.annotations.VisibleForTesting;
import javax.annotation.Nullable;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;

/**
 * Retrieves credentials for servers defined in <a
 * href="https://maven.apache.org/settings.html">Maven settings</a>.
 */
class MavenSettingsServerCredentials {

  @VisibleForTesting static final String CREDENTIAL_SOURCE = "Maven settings";

  private final Settings settings;

  MavenSettingsServerCredentials(Settings settings) {
    this.settings = settings;
  }

  /**
   * Attempts to retrieve credentials for {@code registry} from Maven settings.
   *
   * @param registry the registry
   * @return the credentials for the registry
   */
  @Nullable
  RegistryCredentials retrieve(@Nullable String registry) {
    if (registry == null) {
      return null;
    }

    Server registryServerSettings = settings.getServer(registry);
    if (registryServerSettings == null) {
      return null;
    }

    return new RegistryCredentials(
        CREDENTIAL_SOURCE,
        Authorizations.withBasicCredentials(
            registryServerSettings.getUsername(), registryServerSettings.getPassword()));
  }
}
