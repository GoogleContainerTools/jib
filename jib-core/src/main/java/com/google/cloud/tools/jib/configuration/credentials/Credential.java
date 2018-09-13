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

package com.google.cloud.tools.jib.configuration.credentials;

import java.util.Objects;

// TODO: Move to lower-level package - probably at same level as Authorization.
/** Holds credentials (username and password). */
public class Credential {

  /**
   * Gets a {@link Credential} configured with a username and password.
   *
   * @param username the username
   * @param password the password
   * @return a new {@link Credential}
   */
  public static Credential basic(String username, String password) {
    return new Credential(username, password);
  }

  private final String username;
  private final String password;

  private Credential(String username, String password) {
    this.username = username;
    this.password = password;
  }

  /**
   * Gets the username.
   *
   * @return the username
   */
  public String getUsername() {
    return username;
  }

  /**
   * Gets the password.
   *
   * @return the password
   */
  public String getPassword() {
    return password;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof Credential)) {
      return false;
    }
    Credential otherCredential = (Credential) other;
    return username.equals(otherCredential.username) && password.equals(otherCredential.password);
  }

  @Override
  public int hashCode() {
    return Objects.hash(username, password);
  }
}
