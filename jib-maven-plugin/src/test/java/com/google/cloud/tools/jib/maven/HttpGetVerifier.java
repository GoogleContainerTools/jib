/*
 * Copyright 2018 Google LLC.
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

package com.google.cloud.tools.jib.maven;

import com.google.cloud.tools.jib.blob.Blobs;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import javax.annotation.Nullable;
import org.junit.Assert;

/** Verifies the response of HTTP GET. */
class HttpGetVerifier {

  /**
   * Verifies the response body. Repeatedly tries {@code url} at the interval of .5 seconds for up
   * to 20 seconds until getting OK HTTP response code.
   */
  static void verifyBody(String expectedBody, URL url) throws InterruptedException {
    Assert.assertEquals(expectedBody, getContent(url));
  }

  @Nullable
  private static String getContent(URL url) throws InterruptedException {
    for (int i = 0; i < 40; i++) {
      System.out.println("URL: " + url);
      Thread.sleep(500);
      try {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
          try (InputStream in = connection.getInputStream()) {
            return Blobs.writeToString(Blobs.from(in));
          }
        }
      } catch (IOException ignored) {
        // ignored
        System.out.println("Ignored exception: " + ignored.getMessage());
      }
    }
    return null;
  }
}
