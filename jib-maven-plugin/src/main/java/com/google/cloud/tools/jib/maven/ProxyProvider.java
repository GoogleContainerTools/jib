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

import java.util.List;
import java.util.stream.Collectors;
import org.apache.maven.settings.Proxy;
import org.apache.maven.settings.Settings;

/** Initializes and retrieves proxy settings from either Maven settings or system properties * */
public class ProxyProvider {

  /**
   * Initializes proxy settings based on Maven settings and system properties.
   *
   * @param settings - Maven settings from mojo
   */
  public static void init(Settings settings) {
    List<Proxy> activeProxies =
        settings
            .getProxies()
            .stream()
            .filter(proxy -> proxy.isActive())
            .collect(Collectors.toList());
    for (Proxy proxy : activeProxies) {
      propagateProxyProperties(proxy);
    }
  }

  /**
   * Propagate Maven proxy properties into system properties to be picked up by Connections.
   *
   * @param proxy Maven proxy
   */
  private static void propagateProxyProperties(Proxy proxy) {
    if (proxy.getProtocol().equalsIgnoreCase("http")) {
      propagateProxyProperty("http.proxyHost", proxy.getHost());
      propagateProxyProperty("http.proxyPort", String.valueOf(proxy.getPort()));
      propagateProxyProperty("http.proxyUser", proxy.getUsername());
      propagateProxyProperty("http.proxyPassword", proxy.getPassword());
    } else if (proxy.getProtocol().equalsIgnoreCase("https")) {
      propagateProxyProperty("https.proxyHost", proxy.getHost());
      propagateProxyProperty("https.proxyPort", String.valueOf(proxy.getPort()));
      propagateProxyProperty("https.proxyUser", proxy.getUsername());
      propagateProxyProperty("https.proxyPassword", proxy.getPassword());
    }
    propagateProxyProperty("http.nonProxyHosts", proxy.getNonProxyHosts());
  }

  /**
   * Propagate Maven proxy property into system property to be picked up by Connections. Only set
   * the system property if not already set.
   *
   * @param name proxy system property name
   * @param value proxy system property value
   */
  private static void propagateProxyProperty(String name, String value) {
    if (value != null && System.getProperty(name) == null) {
      System.setProperty(name, value);
    }
  }
}
