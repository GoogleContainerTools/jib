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
import com.google.api.client.http.HttpContent;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.cloud.tools.crepecake.blob.Blob;
import com.google.cloud.tools.crepecake.blob.Blobs;
import com.google.common.base.Charsets;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URL;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

/** Tests for {@link Request}. */
public class RequestTest {

  @Mock private HttpRequestFactory httpRequestFactoryMock;
  @Mock private HttpRequest httpRequestMock;
  @Mock private HttpHeaders httpHeadersMock;

  private ArgumentCaptor<HttpContent> httpContentArgumentCaptor =
      ArgumentCaptor.forClass(HttpContent.class);
  private ArgumentCaptor<GenericUrl> urlArgumentCaptor = ArgumentCaptor.forClass(GenericUrl.class);

  private URL fakeUrl;
  private final String testBodyString = "crepecake";

  @Before
  public void setUpMocksAndFakes() throws IOException {
    fakeUrl = new URL("http://crepecake/fake/url");

    MockitoAnnotations.initMocks(this);

    Mockito.when(httpRequestFactoryMock.buildGetRequest(Mockito.any(GenericUrl.class)))
        .thenReturn(httpRequestMock);
    Mockito.when(
            httpRequestFactoryMock.buildPostRequest(
                Mockito.any(GenericUrl.class), Mockito.any(HttpContent.class)))
        .thenReturn(httpRequestMock);
    Mockito.when(
            httpRequestFactoryMock.buildPutRequest(
                Mockito.any(GenericUrl.class), Mockito.any(HttpContent.class)))
        .thenReturn(httpRequestMock);
  }

  @Test
  public void testGet() throws IOException {
    testSend((request, body) -> request.get());

    Mockito.verify(httpRequestFactoryMock).buildGetRequest(urlArgumentCaptor.capture());

    verifyRequestBuilding(false);
  }

  @Test
  public void testPut() throws IOException {
    testSend(Request::put);

    Mockito.verify(httpRequestFactoryMock)
        .buildPutRequest(urlArgumentCaptor.capture(), httpContentArgumentCaptor.capture());

    verifyRequestBuilding(true);
  }

  @Test
  public void testPost() throws IOException {
    testSend(Request::post);

    Mockito.verify(httpRequestFactoryMock)
        .buildPostRequest(urlArgumentCaptor.capture(), httpContentArgumentCaptor.capture());

    verifyRequestBuilding(true);
  }

  @FunctionalInterface
  private interface SendMethod {
    Response send(Request request, Blob body) throws IOException;
  }

  /**
   * Constructs a request and checks that the content type was set correctly.
   *
   * @param sendMethod wraps the {@link Request} method to test
   * @throws IOException
   */
  private void testSend(SendMethod sendMethod) throws IOException {
    // Initializes test request input.
    String expectedContentType = "some content type";
    Blob testBlob =
        Blobs.from(outputStream -> outputStream.write(testBodyString.getBytes(Charsets.UTF_8)));

    // Constructs the request.
    Request request = new Request(fakeUrl, httpRequestFactoryMock, httpHeadersMock);
    request.setContentType(expectedContentType);
    sendMethod.send(request, testBlob);

    // Verifies that the content type was set correctly.
    Mockito.verify(httpHeadersMock).setContentType(expectedContentType);
  }

  /**
   * Checks that the request was built with the correct URL and body.
   *
   * @param withBody checks the body if true
   */
  private void verifyRequestBuilding(boolean withBody) throws IOException {
    // Checks that the request was sent to the correct URL.
    Assert.assertEquals(new GenericUrl(fakeUrl), urlArgumentCaptor.getValue());

    if (!withBody) {
      return;
    }

    // Checks that the correct content body was sent.
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    httpContentArgumentCaptor.getValue().writeTo(outputStream);
    Assert.assertEquals(testBodyString, outputStream.toString());
  }
}
