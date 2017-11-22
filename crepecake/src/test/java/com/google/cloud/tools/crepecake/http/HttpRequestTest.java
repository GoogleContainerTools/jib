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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

/** Tests for {@link HttpRequest}. */
public class HttpRequestTest {

  @Mock private ConnectionFactory connectionFactoryMock;
  @Mock private HttpURLConnection httpUrlConnectionMock;

  private URL fakeUrl;

  @Before
  public void setUpMocksAndFakes() throws IOException {
    fakeUrl = new URL("http://crepecake/fake/url");

    MockitoAnnotations.initMocks(this);

    Mockito.when(connectionFactoryMock.newConnection(fakeUrl)).thenReturn(httpUrlConnectionMock);
  }

  @Test
  public void testSendGet_withoutBody() throws IOException {
    HttpRequest httpRequest = new HttpRequest(fakeUrl, connectionFactoryMock);
    httpRequest.send();

    Mockito.verifyZeroInteractions(httpUrlConnectionMock);
  }

  @Test
  public void testSendGet_setContentType() throws IOException {
    HttpRequest httpRequest = new HttpRequest(fakeUrl, connectionFactoryMock);
    httpRequest.setContentType("some content type");
    httpRequest.send();

    Mockito.verify(httpUrlConnectionMock).setRequestProperty("Content-Type", "some content type");
    Mockito.verifyNoMoreInteractions(httpUrlConnectionMock);
  }

  @Test
  public void testSendPut() throws IOException {
    String requestBody = "crepecake";
    ByteArrayOutputStream responseStream = new ByteArrayOutputStream();
    Mockito.when(httpUrlConnectionMock.getOutputStream()).thenReturn(responseStream);

    HttpRequest httpRequest = new HttpRequest(fakeUrl, connectionFactoryMock);
    httpRequest.setMethodPut();
    httpRequest.setContentType("some content type");
    httpRequest.send(new BlobStream(requestBody));

    Mockito.verify(httpUrlConnectionMock).setRequestMethod("PUT");
    Mockito.verify(httpUrlConnectionMock).setRequestProperty("Content-Type", "some content type");
    Mockito.verify(httpUrlConnectionMock).setDoOutput(true);
    Mockito.verify(httpUrlConnectionMock).getOutputStream();
    Mockito.verifyNoMoreInteractions(httpUrlConnectionMock);

    Assert.assertEquals(requestBody, responseStream.toString());
  }

  @Test
  public void testSendPost() throws IOException {
    String requestBody = "crepecake";
    ByteArrayOutputStream responseStream = new ByteArrayOutputStream();
    Mockito.when(httpUrlConnectionMock.getOutputStream()).thenReturn(responseStream);

    HttpRequest httpRequest = new HttpRequest(fakeUrl, connectionFactoryMock);
    httpRequest.setMethodPost();
    httpRequest.setContentType("some content type");
    httpRequest.send(new BlobStream(requestBody));

    Mockito.verify(httpUrlConnectionMock).setRequestMethod("POST");
    Mockito.verify(httpUrlConnectionMock).setRequestProperty("Content-Type", "some content type");
    Mockito.verify(httpUrlConnectionMock).setDoOutput(true);
    Mockito.verify(httpUrlConnectionMock).getOutputStream();
    Mockito.verifyNoMoreInteractions(httpUrlConnectionMock);

    Assert.assertEquals(requestBody, responseStream.toString());
  }
}
