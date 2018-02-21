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

import java.util.Collections;
import java.util.Map;

/** Environment variables to set when running the application. */
class EnvironmentParameter implements ConfigurationParameter<Map<String, String>> {

  private Map<String, String> environmentMap = Collections.emptyMap();

  @Override
  public String getDescription() {
    return "environment";
  }

  @Override
  public ConfigurationParameter<Map<String, String>> set(Map<String, String> environmentMap) {
    this.environmentMap = environmentMap;
    return this;
  }

  @Override
  public Map<String, String> get() {
    return Collections.unmodifiableMap(environmentMap);
  }

  @Override
  public ValidationResult validate() {
    return ValidationResult.valid();
  }
}
