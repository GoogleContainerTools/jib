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

package com.google.cloud.tools.jib.gradle;

import javax.annotation.Nullable;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;

/**
 * A bean that configures authorization credentials to be used for a registry. This is configurable
 * with Groovy closures and can be validated when used as a task input.
 */
public class AuthConfiguration {

  @Nullable private String username;
  @Nullable private String password;

  @Input
  @Nullable
  @Optional
  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  @Input
  @Nullable
  @Optional
  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }
}
