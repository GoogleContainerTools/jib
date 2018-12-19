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

import com.google.common.collect.ImmutableList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import org.apache.maven.settings.Proxy;
import org.apache.maven.settings.Settings;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

/** Test for {@link ProxyProvider}. */
public class ProxyProviderTest {

  private static final ImmutableList<String> proxyProperties =
      ImmutableList.of(
          "http.proxyHost",
          "http.proxyPort",
          "http.proxyUser",
          "http.proxyPassword",
          "https.proxyHost",
          "https.proxyPort",
          "https.proxyUser",
          "https.proxyPassword",
          "http.nonProxyHosts");

  // private static Proxy createProxy(@Nullable protocol, @Nullable )
  // HashMap to allow saving null values.
  private HashMap<String, String> savedProperties = new HashMap<>();

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
  public void testAreProxyPropertiesSet() {
    Assert.assertFalse(ProxyProvider.areProxyPropertiesSet("http"));
    Assert.assertFalse(ProxyProvider.areProxyPropertiesSet("https"));
  }

  @Test
  public void testAreProxyPropertiesSet_httpHostSet() {
    System.setProperty("http.proxyHost", "host");
    Assert.assertTrue(ProxyProvider.areProxyPropertiesSet("http"));
    Assert.assertFalse(ProxyProvider.areProxyPropertiesSet("https"));
  }

  @Test
  public void testAreProxyPropertiesSet_httpsHostSet() {
    System.setProperty("https.proxyHost", "host");
    Assert.assertFalse(ProxyProvider.areProxyPropertiesSet("http"));
    Assert.assertTrue(ProxyProvider.areProxyPropertiesSet("https"));
  }

  @Test
  public void testAreProxyPropertiesSet_httpPortSet() {
    System.setProperty("http.proxyPort", "port");
    Assert.assertTrue(ProxyProvider.areProxyPropertiesSet("http"));
    Assert.assertFalse(ProxyProvider.areProxyPropertiesSet("https"));
  }

  @Test
  public void testAreProxyPropertiesSet_httpsPortSet() {
    System.setProperty("https.proxyPort", "port");
    Assert.assertFalse(ProxyProvider.areProxyPropertiesSet("http"));
    Assert.assertTrue(ProxyProvider.areProxyPropertiesSet("https"));
  }

  @Test
  public void testAreProxyPropertiesSet_httpUserSet() {
    System.setProperty("http.proxyUser", "user");
    Assert.assertTrue(ProxyProvider.areProxyPropertiesSet("http"));
    Assert.assertFalse(ProxyProvider.areProxyPropertiesSet("https"));
  }

  @Test
  public void testAreProxyPropertiesSet_httpsUserSet() {
    System.setProperty("https.proxyUser", "user");
    Assert.assertFalse(ProxyProvider.areProxyPropertiesSet("http"));
    Assert.assertTrue(ProxyProvider.areProxyPropertiesSet("https"));
  }

  @Test
  public void testAreProxyPropertiesSet_httpPasswordSet() {
    System.setProperty("http.proxyPassword", "password");
    Assert.assertTrue(ProxyProvider.areProxyPropertiesSet("http"));
    Assert.assertFalse(ProxyProvider.areProxyPropertiesSet("https"));
  }

  @Test
  public void testAreProxyPropertiesSet_httpsPasswordSet() {
    System.setProperty("https.proxyPassword", "password");
    Assert.assertFalse(ProxyProvider.areProxyPropertiesSet("http"));
    Assert.assertTrue(ProxyProvider.areProxyPropertiesSet("https"));
  }

  @Test
  public void testAreProxyPropertiesSet_ignoresHttpNonProxyHosts() {
    System.setProperty("http.nonProxyHosts", "non proxy hosts");
    Assert.assertFalse(ProxyProvider.areProxyPropertiesSet("http"));
    Assert.assertFalse(ProxyProvider.areProxyPropertiesSet("https"));
  }

  @Test
  public void testSetProxyProperties() {
    Proxy httpProxy = new Proxy();
    httpProxy.setProtocol("http");
    httpProxy.setHost("host");
    httpProxy.setPort(1080);
    httpProxy.setUsername("user");
    httpProxy.setPassword("pass");
    httpProxy.setNonProxyHosts("non proxy hosts");

    ProxyProvider.setProxyProperties(httpProxy);
    Assert.assertEquals("host", System.getProperty("http.proxyHost"));
    Assert.assertEquals("1080", System.getProperty("http.proxyPort"));
    Assert.assertEquals("user", System.getProperty("http.proxyUser"));
    Assert.assertEquals("pass", System.getProperty("http.proxyPassword"));
    Assert.assertEquals("non proxy hosts", System.getProperty("http.nonProxyHosts"));

    Proxy httpsProxy = new Proxy();
    httpsProxy.setProtocol("https");
    httpsProxy.setHost("https host");
    httpsProxy.setPort(1443);
    httpsProxy.setUsername("https user");
    httpsProxy.setPassword("https pass");
    ProxyProvider.setProxyProperties(httpsProxy);
    Assert.assertEquals("https host", System.getProperty("https.proxyHost"));
    Assert.assertEquals("1443", System.getProperty("https.proxyPort"));
    Assert.assertEquals("https user", System.getProperty("https.proxyUser"));
    Assert.assertEquals("https pass", System.getProperty("https.proxyPassword"));
  }

  @Test
  public void testSetProxyProperties_someValuesUndefined() {
    Proxy httpProxy = new Proxy();
    httpProxy.setProtocol("http");
    httpProxy.setHost("http://host");

    ProxyProvider.setProxyProperties(httpProxy);
    Assert.assertEquals("http://host", System.getProperty("http.proxyHost"));
    Assert.assertNull(System.getProperty("http.proxyUser"));
    Assert.assertNull(System.getProperty("http.proxyPassword"));
    Assert.assertNull(System.getProperty("http.nonProxyHosts"));

    Proxy httpsProxy = new Proxy();
    httpsProxy.setProtocol("https");
    httpsProxy.setUsername("https user");
    httpsProxy.setPassword("https pass");
    ProxyProvider.setProxyProperties(httpsProxy);
    Assert.assertNull(System.getProperty("https.proxyHost"));
    Assert.assertEquals("https user", System.getProperty("https.proxyUser"));
    Assert.assertEquals("https pass", System.getProperty("https.proxyPassword"));
  }

  @Test
  public void testInit_noActiveProxy() {
    Proxy httpProxy = new Proxy();
    httpProxy.setProtocol("http");
    httpProxy.setHost("proxy1 host");
    httpProxy.setActive(false);

    Proxy httpsProxy = new Proxy();
    httpsProxy.setProtocol("https");
    httpsProxy.setHost("proxy2 host");
    httpsProxy.setActive(false);

    Settings settings = Mockito.mock(Settings.class);
    Mockito.when(settings.getProxies()).thenReturn(Arrays.asList(httpProxy, httpsProxy));
    ProxyProvider.init(settings);

    Assert.assertNull(System.getProperty("http.proxyHost"));
    Assert.assertNull(System.getProperty("https.proxyHost"));
  }

  @Test
  public void testInit_firstActiveHttpProxy() {
    Proxy proxy1 = new Proxy();
    proxy1.setProtocol("http");
    proxy1.setHost("proxy1 host");
    proxy1.setActive(false);

    Proxy proxy2 = new Proxy();
    proxy2.setProtocol("http");
    proxy2.setHost("proxy2 host");
    proxy2.setActive(true);

    Proxy proxy3 = new Proxy();
    proxy3.setProtocol("http");
    proxy3.setHost("proxy3 host");
    proxy3.setActive(false);

    Proxy proxy4 = new Proxy();
    proxy4.setProtocol("http");
    proxy4.setHost("proxy4 host");
    proxy4.setActive(true);

    Settings settings = Mockito.mock(Settings.class);
    Mockito.when(settings.getProxies()).thenReturn(Arrays.asList(proxy1, proxy2, proxy3, proxy4));
    ProxyProvider.init(settings);

    Assert.assertEquals("proxy2 host", System.getProperty("http.proxyHost"));
    Assert.assertNull(System.getProperty("https.proxyHost"));
  }

  @Test
  public void testInit_firstActiveHttpsProxy() {
    Proxy proxy1 = new Proxy();
    proxy1.setProtocol("https");
    proxy1.setHost("proxy1 host");
    proxy1.setActive(false);

    Proxy proxy2 = new Proxy();
    proxy2.setProtocol("https");
    proxy2.setHost("proxy2 host");
    proxy2.setActive(true);

    Proxy proxy3 = new Proxy();
    proxy3.setProtocol("https");
    proxy3.setHost("proxy3 host");
    proxy3.setActive(false);

    Proxy proxy4 = new Proxy();
    proxy4.setProtocol("https");
    proxy4.setHost("proxy4 host");
    proxy4.setActive(true);

    Settings settings = Mockito.mock(Settings.class);
    Mockito.when(settings.getProxies()).thenReturn(Arrays.asList(proxy1, proxy2, proxy3, proxy4));
    ProxyProvider.init(settings);

    Assert.assertNull(System.getProperty("http.proxyHost"));
    Assert.assertEquals("proxy2 host", System.getProperty("https.proxyHost"));
  }
}
