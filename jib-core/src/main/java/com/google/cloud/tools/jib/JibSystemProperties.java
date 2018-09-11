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

/** Names of system properties defined/used by Jib. */
public class JibSystemProperties {

  /** (Integer) HTTP connection/read timeouts for registry interactions, in milliseconds. */
  public static String HTTP_TIMEOUT = "jib.httpTimeout";

  /** (Boolean) Enables more detailed logging and serializes Jib's execution. */
  public static String SERIALIZE = "jibSerialize";

  /**
   * (Boolean) Whether or not to allow sending authentication information over insecure HTTP
   * connections.
   */
  public static String SEND_CREDENTIALS_OVER_HTTP = "sendCredentialsOverHttp";

  /** (Boolean) Whether or not to disable the User-Agent header. */
  public static String DISABLE_USER_AGENT = "_JIB_DISABLE_USER_AGENT";

  private JibSystemProperties() {}
}
