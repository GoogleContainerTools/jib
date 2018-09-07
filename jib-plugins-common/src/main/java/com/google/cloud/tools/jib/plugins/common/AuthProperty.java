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

import javax.annotation.Nullable;

/** Holds a username and password property. */
public interface AuthProperty {

  @Nullable
  String getUsername();

  @Nullable
  String getPassword();

  /**
   * Returns the full descriptor used to configure the {@link AuthProperty}'s username.
   *
   * @return the descriptor used to configure the username property (e.g. 'jib.to.auth.username')
   */
  String getUsernamePropertyDescriptor();

  /**
   * Returns the full descriptor used to configure the {@link AuthProperty}'s password.
   *
   * @return the descriptor used to configure the password property (e.g. 'jib.to.auth.password')
   */
  String getPasswordPropertyDescriptor();
}
