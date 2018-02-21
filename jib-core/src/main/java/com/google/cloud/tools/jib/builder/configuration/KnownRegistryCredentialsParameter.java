/*
 * Copyright 2018 Google Inc.
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

package com.google.cloud.tools.jib.builder.configuration;

import com.google.cloud.tools.jib.registry.credentials.RegistryCredentials;

/** Known registry credentials to fallback on. */
class KnownRegistryCredentialsParameter implements ConfigurationParameter<RegistryCredentials> {

  private RegistryCredentials knownRegistryCredentials = RegistryCredentials.none();

  @Override
  public String getDescription() {
    return "known registry credentials";
  }

  @Override
  public ConfigurationParameter<RegistryCredentials> set(RegistryCredentials registryCredentials) {
    knownRegistryCredentials = registryCredentials;
    return this;
  }

  @Override
  public RegistryCredentials get() {
    return knownRegistryCredentials;
  }

  @Override
  public ValidationResult validate() {
    return ValidationResult.valid();
  }
}
