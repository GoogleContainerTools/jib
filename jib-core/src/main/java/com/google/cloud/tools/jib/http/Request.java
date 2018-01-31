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

import com.google.api.client.http.HttpHeaders;
import java.util.List;
import javax.annotation.Nullable;

/** Holds an HTTP request. */
public class Request {

  /** The HTTP request headers. */
  private final HttpHeaders headers;

  /** The HTTP request body. */
  @Nullable private BlobHttpContent body;

  public static class Builder {

    private final HttpHeaders headers = new HttpHeaders().setAccept("*/*");
    @Nullable private BlobHttpContent body;

    public Request build() {
      return new Request(this);
    }

    /** Sets the {@code Authorization} header. */
    public Builder setAuthorization(@Nullable Authorization authorization) {
      if (authorization != null) {
        headers.setAuthorization(authorization.toString());
      }
      return this;
    }

    /** Sets the {@code Accept} header. */
    public Builder setAccept(List<String> mimeTypes) {
      headers.setAccept(String.join(",", mimeTypes));
      return this;
    }

    /** Sets the {@code User-Agent} header. */
    public Builder setUserAgent(String userAgent) {
      headers.setUserAgent(userAgent);
      return this;
    }

    /** Sets the body and its corresponding {@code Content-Type} header. */
    public Builder setBody(@Nullable BlobHttpContent blobHttpContent) {
      this.body = blobHttpContent;
      return this;
    }
  }

  public static Builder builder() {
    return new Builder();
  }

  private Request(Builder builder) {
    this.headers = builder.headers;
    this.body = builder.body;
  }

  HttpHeaders getHeaders() {
    return headers;
  }

  @Nullable
  BlobHttpContent getHttpContent() {
    return body;
  }
}
