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

import com.google.api.client.http.HttpContent;
import com.google.api.client.http.HttpHeaders;
import java.util.List;
import javax.annotation.Nullable;

/** Holds an HTTP request. */
public class Request {

  /** The HTTP request headers. */
  private final HttpHeaders headers;

  /** The HTTP request body. */
  @Nullable private final HttpContent body;

  /** HTTP connection and read timeout. */
  @Nullable private final Integer httpTimeout;

  public static class Builder {

    private final HttpHeaders headers = new HttpHeaders().setAccept("*/*");
    @Nullable private HttpContent body;
    @Nullable private Integer httpTimeout;

    public Request build() {
      return new Request(this);
    }

    /**
     * Sets the {@code Authorization} header.
     *
     * @param authorization the authorization
     * @return this
     */
    public Builder setAuthorization(@Nullable Authorization authorization) {
      headers.setAuthorization(authorization == null ? null : authorization.toString());
      return this;
    }

    /**
     * Sets the {@code Accept} header.
     *
     * @param mimeTypes the items to pass into the accept header
     * @return this
     */
    public Builder setAccept(List<String> mimeTypes) {
      headers.setAccept(String.join(",", mimeTypes));
      return this;
    }

    /**
     * Sets the {@code User-Agent} header.
     *
     * @param userAgent the user agent
     * @return this
     */
    public Builder setUserAgent(@Nullable String userAgent) {
      headers.setUserAgent(userAgent);
      return this;
    }

    /**
     * Sets the HTTP connection and read timeout in milliseconds. {@code null} uses the default
     * timeout and {@code 0} an infinite timeout.
     *
     * @param httpTimeout timeout in milliseconds
     * @return this
     */
    public Builder setHttpTimeout(@Nullable Integer httpTimeout) {
      this.httpTimeout = httpTimeout;
      return this;
    }

    /**
     * Sets the body and its corresponding {@code Content-Type} header.
     *
     * @param httpContent the body content
     * @return this
     */
    public Builder setBody(@Nullable HttpContent httpContent) {
      this.body = httpContent;
      return this;
    }
  }

  public static Builder builder() {
    return new Builder();
  }

  private Request(Builder builder) {
    this.headers = builder.headers;
    this.body = builder.body;
    this.httpTimeout = builder.httpTimeout;
  }

  HttpHeaders getHeaders() {
    return headers;
  }

  @Nullable
  HttpContent getHttpContent() {
    return body;
  }

  @Nullable
  Integer getHttpTimeout() {
    return httpTimeout;
  }
}
