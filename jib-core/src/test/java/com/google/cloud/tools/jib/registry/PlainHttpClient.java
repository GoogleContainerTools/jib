/*
 * Copyright 2019 Google LLC.
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

import com.google.api.client.http.GenericUrl;
import com.google.cloud.tools.jib.http.FailoverHttpClient;
import com.google.cloud.tools.jib.http.Request;
import com.google.cloud.tools.jib.http.Response;
import java.io.IOException;
import java.net.URL;

/** Forces sending all requests in plain-HTTP protocol. For testing only. */
class PlainHttpClient extends FailoverHttpClient {

  PlainHttpClient() {
    super(true, true, ignored -> {});
  }

  @Override
  public Response call(String httpMethod, URL url, Request request) throws IOException {
    GenericUrl httpUrl = new GenericUrl(url);
    httpUrl.setScheme("http");
    return super.call(httpMethod, httpUrl.toURL(), request);
  }
}
