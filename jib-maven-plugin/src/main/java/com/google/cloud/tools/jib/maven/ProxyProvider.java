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

import com.google.cloud.tools.jib.event.EventDispatcher;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import java.util.List;
import javax.annotation.Nullable;
import org.apache.maven.settings.Proxy;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.crypto.SettingsDecryptionResult;

/** Propagates proxy configuration from Maven settings to system properties. */
class ProxyProvider {

  private static final ImmutableList<String> PROXY_PROPERTIES =
      ImmutableList.of("proxyHost", "proxyPort", "proxyUser", "proxyPassword");

  /**
   * Initializes proxy settings based on Maven settings.
   *
   * @param settings Maven settings
   * @param settingsDecrypter the Maven decrypter component
   * @param eventDispatcher the Jib event dispatcher
   */
  static void init(
      SettingsDecryptionResult decryptedSettings,
      Settings settings,
      EventDispatcher eventDispatcher) {
    configureProxy(decryptedSettings, settings, "https");
    configureProxy(decryptedSettings, settings, "http");
  }

  private static void configureProxy(
      SettingsDecryptionResult decryptedSettings, Settings settings, String protocol) {
    setProxyProperties(decryptedSettings.getProxies(), protocol);
    setProxyProperties(settings.getProxies(), protocol);
  }

  /**
   * Set proxy system properties based on Maven proxy configuration.
   *
   * @param proxy Maven proxy settings
   */
  @VisibleForTesting
  static void setProxyProperties(Proxy proxy) {
    String protocol = proxy.getProtocol();
    if (areProxyPropertiesSet(protocol)) {
      return;
    }

    setPropertySafe(protocol + ".proxyHost", proxy.getHost());
    setPropertySafe(protocol + ".proxyPort", String.valueOf(proxy.getPort()));
    setPropertySafe(protocol + ".proxyUser", proxy.getUsername());
    setPropertySafe(protocol + ".proxyPassword", proxy.getPassword());
    setPropertySafe("http.nonProxyHosts", proxy.getNonProxyHosts());
  }

  private static void setProxyProperties(List<Proxy> proxies, String protocol) {
    proxies
        .stream()
        .filter(Proxy::isActive)
        .filter(proxy -> protocol.equals(proxy.getProtocol()))
        .findFirst()
        .ifPresent(ProxyProvider::setProxyProperties);
  }

  private static void setPropertySafe(String property, @Nullable String value) {
    if (value != null) {
      System.setProperty(property, value);
    }
  }

  /**
   * Check if any proxy system properties are already set for a given protocol. Note, <code>
   * nonProxyHosts</code> is excluded as it can only be set with <code>http</code>.
   *
   * @param protocol protocol
   */
  @VisibleForTesting
  static boolean areProxyPropertiesSet(String protocol) {
    return PROXY_PROPERTIES
        .stream()
        .anyMatch(property -> System.getProperty(protocol + "." + property) != null);
  }
}
