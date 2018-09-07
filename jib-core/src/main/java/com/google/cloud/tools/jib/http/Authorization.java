/*
 * Copyright 2017 Google LLC.
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

package com.google.cloud.tools.jib.http;

import java.util.Objects;

/**
 * Holds the credentials for an HTTP {@code Authorization} header.
 *
 * <p>The HTTP {@code Authorization} header is in the format:
 *
 * <pre>{@code Authorization: <scheme> <token>}</pre>
 */
public class Authorization {

  private final String scheme;
  private final String token;

  Authorization(String scheme, String token) {
    this.scheme = scheme;
    this.token = token;
  }

  public String getScheme() {
    return scheme;
  }

  public String getToken() {
    return token;
  }

  /** Return the HTTP {@link Authorization} header value. */
  @Override
  public String toString() {
    return scheme + " " + token;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof Authorization)) {
      return false;
    }
    Authorization otherAuthorization = (Authorization) other;
    return scheme.equals(otherAuthorization.scheme) && token.equals(otherAuthorization.token);
  }

  @Override
  public int hashCode() {
    return Objects.hash(scheme, token);
  }
}
