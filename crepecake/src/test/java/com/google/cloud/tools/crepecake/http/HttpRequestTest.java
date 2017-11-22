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

import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

/** Tests for {@link HttpRequest}. */
public class HttpRequestTest {

  @Mock private ConnectionFactory connectionFactoryMock;
  @Mock private HttpURLConnection httpURLConnectionMock;

  private URL fakeUrl;

  @Before
  public void setUpMocksAndFakes() throws IOException {
    fakeUrl = new URL("http://crepecake/fake/url");

    initMocks(this);

    when(connectionFactoryMock.newConnection(fakeUrl)).thenReturn(httpURLConnectionMock);
  }

  @Test
  public void testSendGet_withoutBody() throws IOException {
    HttpRequest httpRequest = new HttpRequest(fakeUrl, connectionFactoryMock);
    httpRequest.send();

    verifyZeroInteractions(httpURLConnectionMock);
  }
}
