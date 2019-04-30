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

import com.google.api.client.http.HttpResponse;
import com.google.common.io.ByteStreams;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests for {@link Response}. */
@RunWith(MockitoJUnitRunner.class)
public class ResponseTest {

  @Mock private HttpResponse httpResponseMock;

  @Test
  public void testGetContent() throws IOException {
    byte[] expectedResponse = "crepecake\nis\ngood!".getBytes(StandardCharsets.UTF_8);
    ByteArrayInputStream responseInputStream = new ByteArrayInputStream(expectedResponse);

    Mockito.when(httpResponseMock.getContent()).thenReturn(responseInputStream);

    Response response = new Response(httpResponseMock);

    Assert.assertArrayEquals(expectedResponse, ByteStreams.toByteArray(response.getBody()));
  }
}
