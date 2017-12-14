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

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpMethods;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import java.io.IOException;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

/** Tests for {@link Connection}. */
public class ConnectionTest {

  @Mock private HttpRequestFactory mockHttpRequestFactory;
  @Mock private Request mockRequest;
  @Mock private BlobHttpContent mockBlobHttpContent;
  @Mock private HttpRequest mockHttpRequest;
  @Mock private HttpHeaders mockHttpHeaders;
  @Mock private HttpResponse mockHttpResponse;

  private GenericUrl fakeUrl;

  @Before
  public void setUpMocksAndFakes() throws IOException {
    MockitoAnnotations.initMocks(this);

    fakeUrl = new GenericUrl("http://crepecake/fake/url");

    Mockito.when(
            mockHttpRequestFactory.buildRequest(
                Mockito.any(String.class), Mockito.eq(fakeUrl), Mockito.eq(mockBlobHttpContent)))
        .thenReturn(mockHttpRequest);

    Mockito.when(mockRequest.getBody()).thenReturn(mockBlobHttpContent);
    Mockito.when(mockRequest.getHeaders()).thenReturn(mockHttpHeaders);
    Mockito.when(mockHttpRequest.setHeaders(mockHttpHeaders)).thenReturn(mockHttpRequest);
    Mockito.when(mockHttpRequest.execute()).thenReturn(mockHttpResponse);
  }

  @Test
  public void testGet() throws IOException {
    testSend(Connection::get);
    Mockito.verify(mockHttpRequestFactory)
        .buildRequest(HttpMethods.GET, fakeUrl, mockBlobHttpContent);
  }

  @Test
  public void testPost() throws IOException {
    testSend(Connection::post);
    Mockito.verify(mockHttpRequestFactory)
        .buildRequest(HttpMethods.POST, fakeUrl, mockBlobHttpContent);
  }

  @Test
  public void testPut() throws IOException {
    testSend(Connection::put);
    Mockito.verify(mockHttpRequestFactory)
        .buildRequest(HttpMethods.PUT, fakeUrl, mockBlobHttpContent);
  }

  @FunctionalInterface
  private interface SendFunction {

    Response send(Connection connection, Request request) throws IOException;
  }

  private void testSend(SendFunction sendFunction) throws IOException {
    try (Connection connection = new Connection(fakeUrl.toURL(), mockHttpRequestFactory)) {
      sendFunction.send(connection, mockRequest);
    }

    Mockito.verify(mockHttpRequest).setHeaders(mockHttpHeaders);
    Mockito.verify(mockHttpResponse).disconnect();
  }
}
