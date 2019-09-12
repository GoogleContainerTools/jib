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

package com.google.cloud.tools.jib.http;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.apache.ApacheHttpTransport;
import java.io.IOException;
import java.util.function.BiFunction;

/**
 * Mock {@link Connection} used for testing. Normally, you would use {@link
 * org.mockito.Mockito#mock}; this class is intended to examine the {@link Request) object by
 * calling its non-public package-protected methods.
 */
public class MockConnection extends Connection {

  private final BiFunction<String, Request, Response> responseSupplier;
  private Integer httpTimeout;

  public MockConnection(BiFunction<String, Request, Response> responseSupplier) {
    super(
        new GenericUrl("ftp://non-exisiting.example.url.ever").toURL(), new ApacheHttpTransport());
    this.responseSupplier = responseSupplier;
  }

  @Override
  public Response send(String httpMethod, Request request) throws IOException {
    httpTimeout = request.getHttpTimeout();
    return responseSupplier.apply(httpMethod, request);
  }

  public Integer getRequestedHttpTimeout() {
    return httpTimeout;
  }
}
