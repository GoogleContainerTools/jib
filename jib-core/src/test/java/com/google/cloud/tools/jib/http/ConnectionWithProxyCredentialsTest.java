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

package com.google.cloud.tools.jib.http;

import com.google.api.client.http.apache.ApacheHttpTransport;
import com.google.common.collect.ImmutableList;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.impl.client.DefaultHttpClient;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link Connection} with setting proxy credentials. */
public class ConnectionWithProxyCredentialsTest {

  private static final ImmutableList<String> proxyProperties =
      ImmutableList.of(
          "http.proxyHost",
          "http.proxyPort",
          "http.proxyUser",
          "http.proxyPassword",
          "https.proxyHost",
          "https.proxyPort",
          "https.proxyUser",
          "https.proxyPassword");

  // HashMap to allow saving null values.
  private final HashMap<String, String> savedProperties = new HashMap<>();

  private final ApacheHttpTransport transport = new ApacheHttpTransport();

  @Before
  public void setUp() {
    proxyProperties.stream().forEach(key -> savedProperties.put(key, System.getProperty(key)));
    proxyProperties.stream().forEach(key -> System.clearProperty(key));
  }

  @After
  public void tearDown() {
    Consumer<Map.Entry<String, String>> restoreProperty =
        entry -> {
          if (entry.getValue() == null) {
            System.clearProperty(entry.getKey());
          } else {
            System.setProperty(entry.getKey(), entry.getValue());
          }
        };
    savedProperties.entrySet().stream().forEach(restoreProperty);
  }

  @Test
  public void testAddProxyCredentials_undefined() {
    Connection.addProxyCredentials(transport);
    DefaultHttpClient httpClient = (DefaultHttpClient) transport.getHttpClient();
    Credentials credentials = httpClient.getCredentialsProvider().getCredentials(AuthScope.ANY);
    Assert.assertNull(credentials);
  }

  @Test
  public void testAddProxyCredentials() {
    System.setProperty("http.proxyHost", "http://localhost");
    System.setProperty("http.proxyPort", "1080");
    System.setProperty("http.proxyUser", "user");
    System.setProperty("http.proxyPassword", "pass");

    System.setProperty("https.proxyHost", "https://host.com");
    System.setProperty("https.proxyPort", "1443");
    System.setProperty("https.proxyUser", "s-user");
    System.setProperty("https.proxyPassword", "s-pass");

    Connection.addProxyCredentials(transport);
    DefaultHttpClient httpClient = (DefaultHttpClient) transport.getHttpClient();
    Credentials httpCredentials =
        httpClient.getCredentialsProvider().getCredentials(new AuthScope("http://localhost", 1080));
    Assert.assertEquals("user", httpCredentials.getUserPrincipal().getName());
    Assert.assertEquals("pass", httpCredentials.getPassword());

    Credentials httpsCredentials =
        httpClient.getCredentialsProvider().getCredentials(new AuthScope("https://host.com", 1443));
    Assert.assertEquals("s-user", httpsCredentials.getUserPrincipal().getName());
    Assert.assertEquals("s-pass", httpsCredentials.getPassword());
  }

  @Test
  public void testAddProxyCredentials_defaultPorts() {
    System.setProperty("http.proxyHost", "http://localhost");
    System.setProperty("http.proxyUser", "user");
    System.setProperty("http.proxyPassword", "pass");

    System.setProperty("https.proxyHost", "https://host.com");
    System.setProperty("https.proxyUser", "s-user");
    System.setProperty("https.proxyPassword", "s-pass");

    Connection.addProxyCredentials(transport);
    DefaultHttpClient httpClient = (DefaultHttpClient) transport.getHttpClient();
    Credentials httpCredentials =
        httpClient.getCredentialsProvider().getCredentials(new AuthScope("http://localhost", 80));
    Assert.assertEquals("user", httpCredentials.getUserPrincipal().getName());
    Assert.assertEquals("pass", httpCredentials.getPassword());

    Credentials httpsCredentials =
        httpClient.getCredentialsProvider().getCredentials(new AuthScope("https://host.com", 443));
    Assert.assertEquals("s-user", httpsCredentials.getUserPrincipal().getName());
    Assert.assertEquals("s-pass", httpsCredentials.getPassword());
  }

  @Test
  public void testAddProxyCredentials_hostUndefined() {
    System.setProperty("http.proxyUser", "user");
    System.setProperty("http.proxyPassword", "pass");

    System.setProperty("https.proxyUser", "s-user");
    System.setProperty("https.proxyPassword", "s-pass");

    Connection.addProxyCredentials(transport);
    DefaultHttpClient httpClient = (DefaultHttpClient) transport.getHttpClient();
    Credentials credentials = httpClient.getCredentialsProvider().getCredentials(AuthScope.ANY);
    Assert.assertNull(credentials);
  }

  @Test
  public void testAddProxyCredentials_userUndefined() {
    System.setProperty("http.proxyHost", "http://localhost");
    System.setProperty("http.proxyPassword", "pass");

    System.setProperty("https.proxyHost", "https://host.com");
    System.setProperty("https.proxyPassword", "s-pass");

    Connection.addProxyCredentials(transport);
    DefaultHttpClient httpClient = (DefaultHttpClient) transport.getHttpClient();
    Credentials credentials = httpClient.getCredentialsProvider().getCredentials(AuthScope.ANY);
    Assert.assertNull(credentials);
  }

  @Test
  public void testAddProxyCredentials_passwordUndefined() {
    System.setProperty("http.proxyHost", "http://localhost");
    System.setProperty("http.proxyUser", "user");

    System.setProperty("https.proxyHost", "https://host.com");
    System.setProperty("https.proxyUser", "s-user");

    Connection.addProxyCredentials(transport);
    DefaultHttpClient httpClient = (DefaultHttpClient) transport.getHttpClient();
    Credentials credentials = httpClient.getCredentialsProvider().getCredentials(AuthScope.ANY);
    Assert.assertNull(credentials);
  }
}
