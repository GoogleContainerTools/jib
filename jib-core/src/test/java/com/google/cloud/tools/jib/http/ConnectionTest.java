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
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpMethods;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import com.google.cloud.tools.jib.blob.Blobs;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.atomic.LongAdder;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests for {@link Connection}. */
@RunWith(MockitoJUnitRunner.class)
public class ConnectionTest {

  @FunctionalInterface
  private interface SendFunction {

    Response send(Connection connection, Request request) throws IOException;
  }

  @Mock private HttpRequestFactory mockHttpRequestFactory;
  @Mock private HttpRequest mockHttpRequest;

  private final ArgumentCaptor<HttpHeaders> httpHeadersArgumentCaptor =
      ArgumentCaptor.forClass(HttpHeaders.class);
  private final ArgumentCaptor<BlobHttpContent> blobHttpContentArgumentCaptor =
      ArgumentCaptor.forClass(BlobHttpContent.class);

  private final GenericUrl fakeUrl = new GenericUrl("http://crepecake/fake/url");
  private final LongAdder totalByteCount = new LongAdder();

  private Request fakeRequest;
  private HttpResponse mockHttpResponse;

  @InjectMocks
  private final Connection testConnection =
      Connection.getConnectionFactory().apply(fakeUrl.toURL());

  @Test
  public void testGet() throws IOException {
    setUpMocksAndFakes(null);
    testSend(HttpMethods.GET, Connection::get);
  }

  @Test
  public void testPost() throws IOException {
    setUpMocksAndFakes(null);
    testSend(HttpMethods.POST, Connection::post);
  }

  @Test
  public void testPut() throws IOException {
    setUpMocksAndFakes(null);
    testSend(HttpMethods.PUT, Connection::put);
  }

  @Test
  public void testHttpTimeout_doNotSetByDefault() throws IOException {
    setUpMocksAndFakes(null);
    try (Connection connection = testConnection) {
      connection.send(HttpMethods.GET, fakeRequest);
    }

    Mockito.verify(mockHttpRequest, Mockito.never()).setConnectTimeout(Mockito.anyInt());
    Mockito.verify(mockHttpRequest, Mockito.never()).setReadTimeout(Mockito.anyInt());
  }

  @Test
  public void testHttpTimeout() throws IOException {
    setUpMocksAndFakes(5982);
    try (Connection connection = testConnection) {
      connection.send(HttpMethods.GET, fakeRequest);
    }

    Mockito.verify(mockHttpRequest).setConnectTimeout(5982);
    Mockito.verify(mockHttpRequest).setReadTimeout(5982);
  }

  private void setUpMocksAndFakes(Integer httpTimeout) throws IOException {
    fakeRequest =
        Request.builder()
            .setAccept(Arrays.asList("fake.accept", "another.fake.accept"))
            .setUserAgent("fake user agent")
            .setBody(
                new BlobHttpContent(
                    Blobs.from("crepecake"), "fake.content.type", totalByteCount::add))
            .setAuthorization(Authorization.fromBasicCredentials("fake-username", "fake-secret"))
            .setHttpTimeout(httpTimeout)
            .build();

    Mockito.when(
            mockHttpRequestFactory.buildRequest(
                Mockito.any(String.class), Mockito.eq(fakeUrl), Mockito.any(BlobHttpContent.class)))
        .thenReturn(mockHttpRequest);

    Mockito.when(mockHttpRequest.setHeaders(Mockito.any(HttpHeaders.class)))
        .thenReturn(mockHttpRequest);
    if (httpTimeout != null) {
      Mockito.when(mockHttpRequest.setConnectTimeout(Mockito.anyInt())).thenReturn(mockHttpRequest);
      Mockito.when(mockHttpRequest.setReadTimeout(Mockito.anyInt())).thenReturn(mockHttpRequest);
    }
    mockHttpResponse = Mockito.mock(HttpResponse.class);
    Mockito.when(mockHttpRequest.execute()).thenReturn(mockHttpResponse);
  }

  private void testSend(String httpMethod, SendFunction sendFunction) throws IOException {
    try (Connection connection = testConnection) {
      sendFunction.send(connection, fakeRequest);
    }

    Mockito.verify(mockHttpRequest).setHeaders(httpHeadersArgumentCaptor.capture());
    Mockito.verify(mockHttpResponse).disconnect();

    Assert.assertEquals(
        "fake.accept,another.fake.accept", httpHeadersArgumentCaptor.getValue().getAccept());
    Assert.assertEquals("fake user agent", httpHeadersArgumentCaptor.getValue().getUserAgent());
    // Base64 representation of "fake-username:fake-secret"
    Assert.assertEquals(
        "Basic ZmFrZS11c2VybmFtZTpmYWtlLXNlY3JldA==",
        httpHeadersArgumentCaptor.getValue().getAuthorization());

    Mockito.verify(mockHttpRequestFactory)
        .buildRequest(
            Mockito.eq(httpMethod), Mockito.eq(fakeUrl), blobHttpContentArgumentCaptor.capture());
    Assert.assertEquals("fake.content.type", blobHttpContentArgumentCaptor.getValue().getType());

    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    blobHttpContentArgumentCaptor.getValue().writeTo(byteArrayOutputStream);

    Assert.assertEquals(
        "crepecake", new String(byteArrayOutputStream.toByteArray(), StandardCharsets.UTF_8));
    Assert.assertEquals("crepecake".length(), totalByteCount.longValue());
  }
}
