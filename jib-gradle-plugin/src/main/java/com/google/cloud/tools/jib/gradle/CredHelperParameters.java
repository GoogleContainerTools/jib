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

package com.google.cloud.tools.jib.gradle;

import com.google.cloud.tools.jib.plugins.common.RawConfiguration.CredHelperConfiguration;
import java.util.Map;
import javax.annotation.Nullable;
import javax.inject.Inject;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;

/** Configuration for a credential helper. */
public class CredHelperParameters implements CredHelperConfiguration {
  private final String propertyName;
  private final MapProperty<String, String> environment;
  @Nullable private String helper;

  @Inject
  public CredHelperParameters(ObjectFactory objectFactory, String propertyName) {
    this.propertyName = propertyName;
    this.environment = objectFactory.mapProperty(String.class, String.class).empty();
  }

  @Input
  @Nullable
  @Optional
  public String getHelper() {
    if (System.getProperty(propertyName) != null) {
      return System.getProperty(propertyName);
    }
    return helper;
  }

  @Internal
  @Override
  public java.util.Optional<String> getHelperName() {
    return java.util.Optional.ofNullable(getHelper());
  }

  public void setHelper(String helper) {
    this.helper = helper;
  }

  @Input
  @Optional
  public Map<String, String> getEnvironment() {
    return environment.get();
  }

  public void setEnvironment(Map<String, String> environment) {
    this.environment.set(environment);
  }

  public void setEnvironment(Provider<Map<String, String>> environment) {
    this.environment.set(environment);
  }
}
