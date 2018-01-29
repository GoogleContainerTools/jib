/*
 * Copyright 2017 Google Inc.
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

package com.google.cloud.tools.jib.registry;

/** Thrown because registry authentication failed. */
public class RegistryAuthenticationFailedException extends Exception {

  private static final String REASON_PREFIX = "Failed to authenticate with the registry because: ";

  RegistryAuthenticationFailedException(Throwable cause) {
    super(REASON_PREFIX + cause.getMessage(), cause);
  }

  RegistryAuthenticationFailedException(String reason) {
    super(REASON_PREFIX + reason);
  }
}
