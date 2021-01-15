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

import com.google.cloud.tools.jib.plugins.common.AuthProperty;
import javax.annotation.Nullable;
import javax.inject.Inject;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;

/**
 * A bean that configures authorization credentials to be used for a registry. This is configurable
 * with Groovy closures and can be validated when used as a task input.
 */
public class AuthParameters implements AuthProperty {

  private Property<String> username;
  private Property<String> password;
  private final String source;

  @Inject
  public AuthParameters(ObjectFactory objectFactory, String source) {
    username = objectFactory.property(String.class);
    password = objectFactory.property(String.class);
    this.source = source;
  }

  @Input
  @Optional
  @Override
  @Nullable
  public String getUsername() {
    return username.getOrNull();
  }

  public void setUsername(String username) {
    this.username.set(username);
  }

  public void setUsername(Provider<String> username) {
    this.username.set(username);
  }

  @Input
  @Optional
  @Override
  @Nullable
  public String getPassword() {
    return password.getOrNull();
  }

  public void setPassword(String password) {
    this.password.set(password);
  }

  public void setPassword(Provider<String> password) {
    this.password.set(password);
  }

  @Internal
  @Override
  public String getAuthDescriptor() {
    return source;
  }

  @Internal
  @Override
  public String getUsernameDescriptor() {
    return getAuthDescriptor() + ".username";
  }

  @Internal
  @Override
  public String getPasswordDescriptor() {
    return getAuthDescriptor() + ".password";
  }
}
