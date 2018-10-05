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
      Thread.sleep(500);
      try {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
          try (InputStream in = connection.getInputStream()) {
            return Blobs.writeToString(Blobs.from(in));
          }
        }
      } catch (IOException ex) {
      }
    }
    return null;
  }
}
