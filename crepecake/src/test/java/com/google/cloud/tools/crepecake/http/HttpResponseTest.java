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
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class HttpResponseTest {

  @Mock private HttpURLConnection httpUrlConnectionMock;

  @Before
  public void setUpMocksAndFakes() throws IOException {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void testGetContent_success() throws IOException {
    String expectedResponse = "crepecake\nis\ngood!";
    ByteArrayInputStream responseInputStream =
        new ByteArrayInputStream(expectedResponse.getBytes());

    Mockito.when(httpUrlConnectionMock.getResponseCode()).thenReturn(HttpURLConnection.HTTP_OK);
    Mockito.when(httpUrlConnectionMock.getInputStream()).thenReturn(responseInputStream);

    HttpResponse httpResponse = new HttpResponse(httpUrlConnectionMock);
    BlobStream responseStream = httpResponse.getContent();

    ByteArrayOutputStream responseOutputStream = new ByteArrayOutputStream();
    responseStream.writeTo(responseOutputStream);

    Assert.assertEquals(expectedResponse, responseOutputStream.toString());
  }

  @Test
  public void testGetContent_error() throws IOException {
    String expectedResponse = "crepecake\nhas\ngone\nbad";
    ByteArrayInputStream responseErrorStream =
        new ByteArrayInputStream(expectedResponse.getBytes());

    Mockito.when(httpUrlConnectionMock.getResponseCode())
        .thenReturn(HttpURLConnection.HTTP_BAD_REQUEST);
    Mockito.when(httpUrlConnectionMock.getErrorStream()).thenReturn(responseErrorStream);

    HttpResponse httpResponse = new HttpResponse(httpUrlConnectionMock);
    BlobStream responseStream = httpResponse.getContent();

    ByteArrayOutputStream responseOutputStream = new ByteArrayOutputStream();
    responseStream.writeTo(responseOutputStream);

    Assert.assertEquals(expectedResponse, responseOutputStream.toString());
  }
}
