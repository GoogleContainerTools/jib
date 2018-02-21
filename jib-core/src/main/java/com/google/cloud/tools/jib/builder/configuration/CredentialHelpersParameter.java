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

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

/** Names of credential helpers to possibly use. */
class CredentialHelpersParameter implements ConfigurationParameter<List<String>> {

  private List<String> credentialHelperNames = Collections.emptyList();

  @Override
  public ConfigurationParameter<List<String>> set(@Nullable List<String> credentialHelperNames) {
    if (credentialHelperNames != null) {
      this.credentialHelperNames = credentialHelperNames;
    }
    return this;
  }

  @Override
  public List<String> get() {
    return credentialHelperNames;
  }

  @Override
  public ValidationResult validate() {
    return ValidationResult.valid();
  }
}
