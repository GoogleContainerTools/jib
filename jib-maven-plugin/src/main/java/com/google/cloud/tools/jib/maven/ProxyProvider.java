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
import org.apache.maven.settings.Proxy;
import org.apache.maven.settings.Settings;

/** Propagates proxy configuration from Maven settings to system properties * */
public class ProxyProvider {

  private static final ImmutableList<String> PROXY_PROPERTIES =
      ImmutableList.of("proxyHost", "proxyPort", "proxyUser", "proxyPassword");

  /**
   * Initializes proxy settings based on Maven settings.
   *
   * @param settings Maven settings from mojo
   */
  public static void init(Settings settings) {
    configureProxy(settings, "https");
    configureProxy(settings, "http");
  }

  private static void configureProxy(Settings settings, String protocol) {
    settings
        .getProxies()
        .stream()
        .filter(Proxy::isActive)
        .filter(proxy -> protocol.equals(proxy.getProtocol()))
        .findFirst()
        .ifPresent(ProxyProvider::setProxyProperties);
  }

  /**
   * Set proxy system properties based on Maven proxy configuration.
   *
   * @param proxy Maven proxy
   */
  private static void setProxyProperties(Proxy proxy) {
    String protocol = proxy.getProtocol();
    if (!proxyPropertiesSet(protocol)) {
      System.setProperty(protocol + ".proxyHost", proxy.getHost());
      System.setProperty(protocol + ".proxyPort", String.valueOf(proxy.getPort()));
      System.setProperty(protocol + ".proxyUser", proxy.getUsername());
      System.setProperty(protocol + ".proxyPassword", proxy.getPassword());
      System.setProperty("http.nonProxyHosts", proxy.getNonProxyHosts());
    }
  }

  /**
   * Check if any proxy system properties are already set for a given protocol. Note, <code>
   * nonProxyHosts</code> is excluded as it can only be set with <code>http</code>.
   *
   * @param protocol protocol
   */
  private static boolean proxyPropertiesSet(String protocol) {
    return PROXY_PROPERTIES
        .stream()
        .anyMatch(property -> System.getProperty(protocol + "." + property) != null);
  }
}
