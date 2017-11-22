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

package com.google.cloud.tools.crepecake.http;

import com.google.cloud.tools.crepecake.blob.BlobStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;

/** Captures an HTTP response. */
public class HttpResponse {

  private final HttpURLConnection connection;

  HttpResponse(HttpURLConnection connection) {
    this.connection = connection;
  }

  public int getResponseCode() throws IOException {
    return connection.getResponseCode();
  }

  public String getHeader(String headerName) {
    return connection.getHeaderField(headerName);
  }

  public BlobStream getContent() throws IOException {
    InputStream responseStream;
    if (getResponseCode() < HttpURLConnection.HTTP_BAD_REQUEST) {
      responseStream = connection.getInputStream();
    } else {
      responseStream = connection.getErrorStream();
    }

    return new BlobStream(responseStream);
  }
}
