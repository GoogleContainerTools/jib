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
import com.google.common.collect.Range;

/** Names of system properties defined/used by Jib. */
public class JibSystemProperties {

  public static final String UPSTREAM_CLIENT = "_JIB_UPSTREAM_CLIENT";
  private static final String DISABLE_USER_AGENT = "_JIB_DISABLE_USER_AGENT";

  @VisibleForTesting public static final String HTTP_TIMEOUT = "jib.httpTimeout";

  @VisibleForTesting static final String CROSS_REPOSITORY_BLOB_MOUNTS = "jib.blobMounts";

  public static final String SEND_CREDENTIALS_OVER_HTTP = "sendCredentialsOverHttp";
  public static final String SERIALIZE = "jib.serialize";

  @VisibleForTesting public static final String SKIP_EXISTING_IMAGES = "jib.skipExistingImages";

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
   * Gets whether or not to use <em>cross-repository blob mounts</em> when uploading image layers
   * ({@code mount/from}). This is defined by the {@code jib.blobMounts} system property.
   *
   * @return {@code true} if {@code mount/from} should be used, {@code false} if not, defaulting to
   *     {@code true}
   */
  public static boolean useCrossRepositoryBlobMounts() {
    return System.getProperty(CROSS_REPOSITORY_BLOB_MOUNTS) == null
        || Boolean.getBoolean(CROSS_REPOSITORY_BLOB_MOUNTS);
  }

  /**
   * Gets whether or not to serialize Jib's execution. This is defined by the {@code jib.serialize}
   * system property.
   *
   * @return {@code true} if Jib's execution should be serialized, {@code false} if not
   */
  public static boolean serializeExecution() {
    return Boolean.getBoolean(SERIALIZE);
  }

  /**
   * Gets whether or not to allow sending authentication information over insecure HTTP connections.
   * This is defined by the {@code sendCredentialsOverHttp} system property.
   *
   * @return {@code true} if authentication information is allowed to be sent over insecure
   *     connections, {@code false} if not
   */
  public static boolean sendCredentialsOverHttp() {
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
    checkNumericSystemProperty(HTTP_TIMEOUT, Range.atLeast(0));
  }

  /**
   * Checks if {@code http.proxyPort} and {@code https.proxyPort} system properties are in the
   * [0..65535] range when set.
   *
   * @throws NumberFormatException if invalid values
   */
  public static void checkProxyPortProperty() throws NumberFormatException {
    checkNumericSystemProperty("http.proxyPort", Range.closed(0, 65535));
    checkNumericSystemProperty("https.proxyPort", Range.closed(0, 65535));
  }

  /**
   * Gets whether or not to skip pushing tags to existing images. This is defined by the {@code
   * jib.skipExistingImages} system property.
   *
   * @return {@code true} if Jib should skip pushing tags to existing images, {@code false} if not
   */
  public static boolean skipExistingImages() {
    return Boolean.getBoolean(SKIP_EXISTING_IMAGES);
  }

  private static void checkNumericSystemProperty(String property, Range<Integer> validRange) {
    String value = System.getProperty(property);
    if (value == null) {
      return;
    }

    int parsed;
    try {
      parsed = Integer.parseInt(value);
    } catch (NumberFormatException ex) {
      throw new NumberFormatException(property + " must be an integer: " + value);
    }
    if (validRange.hasLowerBound() && validRange.lowerEndpoint() > parsed) {
      throw new NumberFormatException(
          property + " cannot be less than " + validRange.lowerEndpoint() + ": " + value);
    } else if (validRange.hasUpperBound() && validRange.upperEndpoint() < parsed) {
      throw new NumberFormatException(
          property + " cannot be greater than " + validRange.upperEndpoint() + ": " + value);
    }
  }

  private JibSystemProperties() {}
}
