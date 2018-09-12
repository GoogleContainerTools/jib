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

package com.google.cloud.tools.jib;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import java.util.function.Function;

/** Names of system properties defined/used by Jib. */
public class JibSystemProperties {

  @VisibleForTesting public static final String HTTP_TIMEOUT = "jib.httpTimeout";

  @VisibleForTesting
  public static final String SEND_CREDENTIALS_OVER_HTTP = "sendCredentialsOverHttp";

  private static final String SERIALIZE = "jibSerialize";

  private static final String DISABLE_USER_AGENT = "_JIB_DISABLE_USER_AGENT";

  /**
   * Gets the HTTP connection/read timeouts for registry interactions in milliseconds.
   *
   * @return the value of the {@code jib.httpTimeout} system property
   */
  public static Integer getHttpTimeout() {
    return Integer.getInteger(HTTP_TIMEOUT);
  }

  /**
   * Gets whether or not to serialize Jib's execution.
   *
   * @return the value of the {@code jibSerialize} system property
   */
  public static boolean isSerializedExecutionEnabled() {
    return Boolean.getBoolean(SERIALIZE);
  }

  /**
   * Gets whether or not to allow sending authentication information over insecure HTTP connections.
   *
   * @return the value of the {@code sendCredentialsOverHttp} system property
   */
  public static boolean isSendCredentialsOverHttpEnabled() {
    return Boolean.getBoolean(SEND_CREDENTIALS_OVER_HTTP);
  }

  /**
   * Gets whether or not to enable the User-Agent header.
   *
   * @return {@code true} if the {@code _JIB_DISABLE_USER_AGENT} system property is set to a null or
   *     empty string, otherwise {@code false}
   */
  public static boolean isUserAgentEnabled() {
    return Strings.isNullOrEmpty(System.getProperty(DISABLE_USER_AGENT));
  }

  /**
   * Checks the {@code jib.httpTimeout} system property for invalid (non-integer or negative)
   * values.
   *
   * @param exceptionFactory factory to create an exception with the given description
   * @param <T> the exception type to throw if invalid values
   * @throws T if invalid values
   */
  public static <T extends Throwable> void checkHttpTimeoutProperty(
      Function<String, T> exceptionFactory) throws T {
    String value = System.getProperty(HTTP_TIMEOUT);
    if (value == null) {
      return;
    }
    try {
      if (Integer.parseInt(value) < 0) {
        throw exceptionFactory.apply(HTTP_TIMEOUT + " cannot be negative: " + value);
      }
    } catch (NumberFormatException ex) {
      throw exceptionFactory.apply(HTTP_TIMEOUT + " must be an integer: " + value);
    }
  }

  private JibSystemProperties() {}
}
