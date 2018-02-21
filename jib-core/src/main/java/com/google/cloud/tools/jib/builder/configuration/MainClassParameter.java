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

/** The main class to use when running the application. */
class MainClassParameter implements ConfigurationParameter<String> {

  private String mainClass;

  @Override
  public String getDescription() {
    return "main class";
  }

  @Override
  public ConfigurationParameter<String> set(String mainClass) {
    this.mainClass = mainClass;
    return this;
  }

  @Override
  public String get() {
    return mainClass;
  }

  @Override
  public ValidationResult validate() {
    if (mainClass != null) {
      return ValidationResult.valid();
    }

    return ValidationResult.invalid("main class is required but not set");
  }
}
