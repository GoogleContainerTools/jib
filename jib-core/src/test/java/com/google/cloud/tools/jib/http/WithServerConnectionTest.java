/*
 * Copyright 2018 Google LLC. All rights reserved.
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URL;
import org.junit.Assert;
import org.junit.Test;

/** Tests for {@link Connection} using an actual local server. */
public class WithServerConnectionTest {

  @Test
  public void testGet() throws IOException, InterruptedException {
    try (TestWebServer server = new TestWebServer();
        Connection connection = new Connection(new URL(server.getEndpoint()))) {
      Response response = connection.send("GET", new Request.Builder().build());

      Assert.assertEquals(200, response.getStatusCode());

      ByteArrayOutputStream out = new ByteArrayOutputStream();
      response.getBody().writeTo(out);
      Assert.assertEquals("Hello World!", out.toString("UTF-8"));
    }
  }

  @Test
  public void testErrorOnSecondSend() throws IOException, InterruptedException {
    try (TestWebServer server = new TestWebServer();
        Connection connection = new Connection(new URL(server.getEndpoint()))) {
      connection.send("GET", new Request.Builder().build());
      try {
        connection.send("GET", new Request.Builder().build());
        Assert.fail("Should fail on the second send");
      } catch (IllegalStateException ex) {
        Assert.assertEquals("Connection can send only one request", ex.getMessage());
      }
    }
  }
}
