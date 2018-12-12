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

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.maven.settings.Proxy;
import org.apache.maven.settings.Settings;

/** Initializes and retrieves proxy settings from either Maven settings or system properties * */
public class ProxyProvider {

  static Settings mavenSettings = new Settings();

  /**
   * Initializes proxy settings based on Maven settings and system properties.
   *
   * @param settings - Maven settings from mojo
   */
  public static void init(Settings settings) {
    mavenSettings = settings;
    for (Proxy proxy : mavenSettings.getProxies()) {
      // Poor way to push proxy settings onto Connection instances
      if (proxy != null && proxy.getProtocol().equalsIgnoreCase("http")) {
        System.setProperty("http.proxyHost", proxy.getHost());
        System.setProperty("http.proxyPort", String.valueOf(proxy.getPort()));
        System.setProperty("http.nonProxyHosts", proxy.getNonProxyHosts());
        System.setProperty("http.proxyUser", String.valueOf(proxy.getUsername()));
        System.setProperty("http.proxyPassword", proxy.getPassword());
      } else if (proxy != null && proxy.getProtocol().equalsIgnoreCase("https")) {
        System.setProperty("https.proxyHost", proxy.getHost());
        System.setProperty("https.proxyPort", String.valueOf(proxy.getPort()));
        System.setProperty("http.nonProxyHosts", proxy.getNonProxyHosts());
        System.setProperty("https.proxyUser", String.valueOf(proxy.getUsername()));
        System.setProperty("https.proxyPassword", proxy.getPassword());
      }
    }
  }

  /**
   * Attempts to retrieve proxy from either Maven settings or system properties.
   *
   * @return the active proxies
   */
  public List<Proxy> getProxies() {
    List<Proxy> proxies = new ArrayList<Proxy>();
    if (mavenSettings != null) {
      proxies.addAll(mavenSettings.getProxies());
    } else {
      if (System.getProperty("http.proxyHost") != null) {
        Proxy proxy = new Proxy();
        proxy.setHost(System.getProperty("http.proxyHost"));
        proxy.setPort(Integer.parseInt(System.getProperty("http.proxyPort")));
        proxy.setActive(true);
        proxy.setUsername(System.getProperty("http.proxyUser"));
        proxy.setPassword(System.getProperty("http.proxyPassword"));
        proxy.setNonProxyHosts(System.getProperty("http.nonProxyHosts"));
        proxies.add(proxy);
      }
      if (System.getProperty("https.proxyHost") != null) {
        Proxy proxy = new Proxy();
        proxy.setHost(System.getProperty("https.proxyHost"));
        proxy.setPort(Integer.parseInt(System.getProperty("https.proxyPort")));
        proxy.setActive(true);
        proxy.setUsername(System.getProperty("https.proxyUser"));
        proxy.setPassword(System.getProperty("https.proxyPassword"));
        proxy.setNonProxyHosts(System.getProperty("http.nonProxyHosts"));
        proxies.add(proxy);
      }
    }
    return proxies.stream().filter(proxy -> proxy.isActive()).collect(Collectors.toList());
  }
}
