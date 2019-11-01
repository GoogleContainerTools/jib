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

package com.google.cloud.tools.jib.http;

import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpResponseException;
import java.io.IOException;

/** Holds an HTTP response exception. */
public class ResponseException extends IOException {

  private final HttpResponseException httpResponseException;
  private final boolean requestAuthorizationCleared;

  ResponseException(
      HttpResponseException httpResponseException, boolean requestAuthorizationCleared) {
    super(httpResponseException.getMessage(), httpResponseException);
    this.httpResponseException = httpResponseException;
    this.requestAuthorizationCleared = requestAuthorizationCleared;
  }

  public int getStatusCode() {
    return httpResponseException.getStatusCode();
  }

  public String getContent() {
    return httpResponseException.getContent();
  }

  public HttpHeaders getHeaders() {
    return httpResponseException.getHeaders();
  }

  /**
   * Returns whether the {@code Authorization} HTTP header was cleared (and thus not sent).
   *
   * @return whether the {@code Authorization} HTTP header was cleared
   */
  public boolean requestAuthorizationCleared() {
    return requestAuthorizationCleared;
  }
}
