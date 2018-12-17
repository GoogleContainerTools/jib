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

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.maven.settings.Proxy;
import org.apache.maven.settings.Settings;

/** Propagates proxy configuration from Maven settings to system properties * */
public class ProxyProvider {

  private static final List<String> PROXY_PROPERTIES =
      Arrays.asList("proxyHost,proxyPort,proxyUser,proxyPassword".split(","));

  /**
   * Initializes proxy settings based on Maven settings.
   *
   * @param settings - Maven settings from mojo
   */
  public static void init(Settings settings) {
    settings
        .getProxies()
        .stream()
        .filter(proxy -> proxy.isActive())
        .collect(Collectors.toList())
        .forEach(proxy -> setProxyProperties(proxy));
  }

  /**
   * Set proxy system properties based on Maven proxy configuration. These system properties will be
   * picked up by {@link java.net.ProxySelector} used in {@link
   * com.google.cloud.tools.jib.http.Connection}, while connecting to container image registries.
   *
   * @param proxy Maven proxy
   */
  private static void setProxyProperties(Proxy proxy) {
    String protocol = proxy.getProtocol();
    if (protocol != null && !proxyPropertiesSet(protocol)) {
      setProxyProperty(protocol + ".proxyHost", proxy.getHost());
      setProxyProperty(protocol + ".proxyPort", String.valueOf(proxy.getPort()));
      setProxyProperty(protocol + ".proxyUser", proxy.getUsername());
      setProxyProperty(protocol + ".proxyPassword", proxy.getPassword());
      setProxyProperty("http.nonProxyHosts", proxy.getNonProxyHosts());
    }
  }

  /**
   * Set proxy system property if it has a proper value
   *
   * @param property property name
   * @param value property value
   */
  private static void setProxyProperty(String property, String value) {
      if (property != null && value != null) {
          System.setProperty(property, value);
      }
  }

  /**
   * Check if any proxy system properties are already set for a given protocol. Note, <code>
   * nonProxyHosts</code> is excluded as it can only be set with <code>http</code>.
   *
   * @param protocol protocol
   */
  private static boolean proxyPropertiesSet(String protocol) {
    return PROXY_PROPERTIES.stream().anyMatch(p -> System.getProperty(protocol + "." + p) != null);
  }
}
