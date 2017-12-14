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
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import com.google.cloud.tools.crepecake.blob.Blob;
import com.google.cloud.tools.crepecake.blob.Blobs;
import com.google.common.base.Charsets;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.io.IOException;
import java.net.URL;
import java.util.function.Consumer;
import java.util.function.Function;

/** Tests for {@link Connection}. */
public class ConnectionTest {

  @Mock private HttpRequestFactory mockHttpRequestFactory;
  @Mock private Request mockRequest;
  @Mock private HttpRequest mockHttpRequest;
  @Mock private HttpHeaders mockHttpHeaders;
  @Mock private HttpResponse mockHttpResponse;

  private ArgumentCaptor<GenericUrl> urlArgumentCaptor = ArgumentCaptor.forClass(GenericUrl.class);

  private URL fakeUrl;

  @Before
  public void setUpMocksAndFakes() throws IOException {
    fakeUrl = new URL("http://crepecake/fake/url");

    Mockito.when(mockHttpRequestFactory.buildGetRequest(Mockito.any(GenericUrl.class)))
        .thenReturn(mockHttpRequest);

    Mockito.when(mockHttpRequest.getHeaders()).thenReturn(mockHttpHeaders);
    Mockito.when(mockHttpRequest.setHeaders(mockHttpHeaders)).thenReturn(mockHttpRequest);
    Mockito.when(mockHttpRequest.execute()).thenReturn(mockHttpResponse);
  }

  @Test
  public void testGet() throws IOException {
    testSend(Connection::get);
    try (Connection connection = new Connection(fakeUrl, mockHttpRequestFactory)) {
      connection.get(mockRequest);

      Mockito.verify(mockHttpRequestFactory).buildGetRequest(urlArgumentCaptor.capture());
      Mockito.verify(mockHttpRequest).setHeaders(mockHttpHeaders);

      Assert.assertEquals(new GenericUrl(fakeUrl), urlArgumentCaptor.getValue());
    }

    Mockito.verify(mockHttpResponse).disconnect();
  }

  @Test
  public void testPost() throws IOException {
    try (Connection connection = new Connection(fakeUrl, mockHttpRequestFactory)) {
      connection.post(mockRequest);

      Mockito.verify(mockHttpRequestFactory).buildGetRequest(urlArgumentCaptor.capture());
      Mockito.verify(mockHttpRequest).setHeaders(mockHttpHeaders);

      Assert.assertEquals(new GenericUrl(fakeUrl), urlArgumentCaptor.getValue());
    }

    Mockito.verify(mockHttpResponse).disconnect();
  }

  private void testSend(Function<Connection, Response> sendFunction) {
    sendFunction
  }
}
