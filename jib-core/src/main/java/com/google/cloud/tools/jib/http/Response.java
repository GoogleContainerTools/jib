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

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpResponse;
import com.google.common.net.HttpHeaders;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/** Holds an HTTP response. */
public class Response {

  private final HttpResponse httpResponse;

  Response(HttpResponse httpResponse) {
    this.httpResponse = httpResponse;
  }

  /** @return the HTTP status code of the response */
  public int getStatusCode() {
    return httpResponse.getStatusCode();
  }

  /**
   * @param headerName the header name
   * @return a list of headers in the response
   */
  public List<String> getHeader(String headerName) {
    return httpResponse.getHeaders().getHeaderStringValues(headerName);
  }

  /**
   * @return the first {@code Content-Length} header, or {@code -1} if not found
   * @throws NumberFormatException if parsing the content length header fails
   */
  public long getContentLength() throws NumberFormatException {
    String contentLengthHeader =
        httpResponse.getHeaders().getFirstHeaderStringValue(HttpHeaders.CONTENT_LENGTH);
    if (contentLengthHeader == null) {
      return -1;
    }
    try {
      return Long.parseLong(contentLengthHeader);

    } catch (NumberFormatException ex) {
      return -1;
    }
  }

  /**
   * @return the HTTP response body as an {@link InputStream}.
   * @throws IOException if getting the HTTP response content fails.
   */
  public InputStream getBody() throws IOException {
    return httpResponse.getContent();
  }

  /** @return the original request URL */
  public GenericUrl getRequestUrl() {
    return httpResponse.getRequest().getUrl();
  }
}
