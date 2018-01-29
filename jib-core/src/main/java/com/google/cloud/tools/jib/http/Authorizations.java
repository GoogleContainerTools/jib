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

package com.google.cloud.tools.jib.http;

import com.google.api.client.util.Base64;
import java.nio.charset.StandardCharsets;

/** Static initializers for {@link Authorization}. */
public class Authorizations {

  public static Authorization withBearerToken(String token) {
    return new Authorization("Bearer", token);
  }

  public static Authorization withBasicToken(String username, String secret) {
    String credentials = username + ":" + secret;
    String token = new String(Base64.encodeBase64(credentials.getBytes(StandardCharsets.US_ASCII)));
    return new Authorization("Basic", token);
  }

  private Authorizations() {}
}
