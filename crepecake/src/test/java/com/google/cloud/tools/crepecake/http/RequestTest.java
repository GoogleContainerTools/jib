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
import com.google.cloud.tools.crepecake.blob.BlobStream;
import java.io.IOException;
import java.net.URL;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

/** Tests for {@link Request}. */
public class RequestTest {

  @Mock private HttpRequest httpRequestMock;
  @Mock private HttpHeaders httpHeadersMock;
  @Mock private BlobStream blobStreamMock;

  private URL fakeUrl;

  @Before
  public void setUpMocksAndFakes() throws IOException {
    fakeUrl = new URL("http://crepecake/fake/url");

    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void testGet() throws IOException {
    Request request = new Request(fakeUrl, httpHeadersMock);
    request.setContentType("some content type");
    request.get();

    Mockito.verify(httpHeadersMock).setContentType("some content type");
    Mockito.verifyZeroInteractions(httpRequestMock);

    Assert.assertEquals(new GenericUrl(fakeUrl), request.getRequest().getUrl());
  }

  @Test
  public void testSendPut() throws IOException {
    Request request = new Request(fakeUrl, httpHeadersMock);
    request.setContentType("some content type");
    request.put(blobStreamMock);

    Mockito.verify(httpHeadersMock).setContentType("some content type");
    Mockito.verify(httpRequestMock).setRequestMethod(HttpMethods.PUT);
    Mockito.verify(httpRequestMock).setContent(blobStreamMock);
    Mockito.verifyNoMoreInteractions(httpRequestMock);

    Assert.assertEquals(new GenericUrl(fakeUrl), request.getRequest().getUrl());
  }

  @Test
  public void testSendPost() throws IOException {
    Request request = new Request(fakeUrl, httpHeadersMock);
    request.setContentType("some content type");
    request.post(blobStreamMock);

    Mockito.verify(httpHeadersMock).setContentType("some content type");
    Mockito.verify(httpRequestMock).setRequestMethod(HttpMethods.POST);
    Mockito.verify(httpRequestMock).setContent(blobStreamMock);
    Mockito.verifyNoMoreInteractions(httpRequestMock);

    Assert.assertEquals(new GenericUrl(fakeUrl), request.getRequest().getUrl());
  }
}
