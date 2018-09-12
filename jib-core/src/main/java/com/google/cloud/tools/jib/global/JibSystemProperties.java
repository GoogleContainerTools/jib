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

package com.google.cloud.tools.jib.global;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;

/** Names of system properties defined/used by Jib. */
public class JibSystemProperties {

  @VisibleForTesting public static final String HTTP_TIMEOUT = "jib.httpTimeout";

  @VisibleForTesting
  public static final String SEND_CREDENTIALS_OVER_HTTP = "sendCredentialsOverHttp";

  private static final String SERIALIZE = "jibSerialize";

  private static final String DISABLE_USER_AGENT = "_JIB_DISABLE_USER_AGENT";

  /**
   * Gets the HTTP connection/read timeouts for registry interactions in milliseconds. This is
   * defined by the {@code jib.httpTimeout} system property. The default value is 20000 if the
   * system property is not set, and 0 indicates an infinite timeout.
   *
   * @return the HTTP connection/read timeouts for registry interactions in milliseconds
   */
  public static int getHttpTimeout() {
    if (Integer.getInteger(HTTP_TIMEOUT) == null) {
      return 20000;
    }
    return Integer.getInteger(HTTP_TIMEOUT);
  }

  /**
   * Gets whether or not to serialize Jib's execution. This is defined by the {@code jibSerialize}
   * system property.
   *
   * @return {@code true} if Jib's execution should be serialized, {@code false} if not
   */
  public static boolean isSerializedExecutionEnabled() {
    return Boolean.getBoolean(SERIALIZE);
  }

  /**
   * Gets whether or not to allow sending authentication information over insecure HTTP connections.
   * This is defined by the {@code sendCredentialsOverHttp} system property.
   *
   * @return {@code true} if authentication information is allowed to be sent over insecure
   *     connections, {@code false} if not
   */
  public static boolean isSendCredentialsOverHttpEnabled() {
    return Boolean.getBoolean(SEND_CREDENTIALS_OVER_HTTP);
  }

  /**
   * Gets whether or not to enable the User-Agent header. This is defined by the {@code
   * _JIB_DISABLE_USER_AGENT} system property.
   *
   * @return {@code true} if the User-Agent header is enabled, {@code false} if not
   */
  public static boolean isUserAgentEnabled() {
    return Strings.isNullOrEmpty(System.getProperty(DISABLE_USER_AGENT));
  }

  /**
   * Checks the {@code jib.httpTimeout} system property for invalid (non-integer or negative)
   * values.
   *
   * @throws NumberFormatException if invalid values
   */
  public static void checkHttpTimeoutProperty() throws NumberFormatException {
    String value = System.getProperty(HTTP_TIMEOUT);
    if (value == null) {
      return;
    }
    int parsed;
    try {
      parsed = Integer.parseInt(value);
    } catch (NumberFormatException ex) {
      throw new NumberFormatException(HTTP_TIMEOUT + " must be an integer: " + value);
    }
    if (parsed < 0) {
      throw new NumberFormatException(HTTP_TIMEOUT + " cannot be negative: " + value);
    }
  }

  private JibSystemProperties() {}
}
