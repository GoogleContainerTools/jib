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
import com.google.cloud.tools.crepecake.blob.Blob;
import com.google.cloud.tools.crepecake.blob.Blobs;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.Assert;
import org.junit.Before;
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

  @Mock private HttpRequestFactory mockHttpRequestFactory;
  @Mock private HttpRequest mockHttpRequest;
  @Mock private HttpResponse mockHttpResponse;

  private final ArgumentCaptor<HttpHeaders> httpHeadersArgumentCaptor =
      ArgumentCaptor.forClass(HttpHeaders.class);
  private final ArgumentCaptor<BlobHttpContent> blobHttpContentArgumentCaptor =
      ArgumentCaptor.forClass(BlobHttpContent.class);

  private final GenericUrl fakeUrl = new GenericUrl("http://crepecake/fake/url");
  private final Request fakeRequest = new Request();

  @InjectMocks private final Connection testConnection = new Connection(fakeUrl.toURL());

  @Before
  public void setUpMocksAndFakes() throws IOException {
    Blob fakeBlob = Blobs.from("crepecake", false);
    fakeRequest.setBody(fakeBlob);
    fakeRequest.setContentType("fake.content.type");
    fakeRequest.setAuthorization(Authorizations.withBasicToken("fake-token"));

    Mockito.when(
            mockHttpRequestFactory.buildRequest(
                Mockito.any(String.class), Mockito.eq(fakeUrl), Mockito.any(BlobHttpContent.class)))
        .thenReturn(mockHttpRequest);

    Mockito.when(mockHttpRequest.setHeaders(Mockito.any(HttpHeaders.class)))
        .thenReturn(mockHttpRequest);
    Mockito.when(mockHttpRequest.execute()).thenReturn(mockHttpResponse);
  }

  @Test
  public void testGet() throws IOException {
    testSend(HttpMethods.GET, Connection::get);
  }

  @Test
  public void testPost() throws IOException {
    testSend(HttpMethods.POST, Connection::post);
  }

  @Test
  public void testPut() throws IOException {
    testSend(HttpMethods.PUT, Connection::put);
  }

  @FunctionalInterface
  private interface SendFunction {

    Response send(Connection connection, Request request) throws IOException;
  }

  private void testSend(String httpMethod, SendFunction sendFunction) throws IOException {
    try (Connection connection = testConnection) {
      sendFunction.send(connection, fakeRequest);
    }

    Mockito.verify(mockHttpRequest).setHeaders(httpHeadersArgumentCaptor.capture());
    Mockito.verify(mockHttpResponse).disconnect();

    Assert.assertEquals("fake.content.type", httpHeadersArgumentCaptor.getValue().getContentType());
    Assert.assertEquals(
        "Basic fake-token", httpHeadersArgumentCaptor.getValue().getAuthorization());

    Mockito.verify(mockHttpRequestFactory)
        .buildRequest(
            Mockito.eq(httpMethod), Mockito.eq(fakeUrl), blobHttpContentArgumentCaptor.capture());

    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    blobHttpContentArgumentCaptor.getValue().writeTo(byteArrayOutputStream);

    Assert.assertEquals(
        "crepecake", new String(byteArrayOutputStream.toByteArray(), StandardCharsets.UTF_8));
  }
}
