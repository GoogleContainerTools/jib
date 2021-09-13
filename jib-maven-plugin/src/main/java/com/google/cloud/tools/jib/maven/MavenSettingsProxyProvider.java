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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.settings.Proxy;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.building.SettingsProblem;
import org.apache.maven.settings.crypto.DefaultSettingsDecryptionRequest;
import org.apache.maven.settings.crypto.SettingsDecrypter;
import org.apache.maven.settings.crypto.SettingsDecryptionRequest;
import org.apache.maven.settings.crypto.SettingsDecryptionResult;

/** Propagates proxy configuration from Maven settings to system properties. */
class MavenSettingsProxyProvider {

  private static final ImmutableList<String> PROXY_PROPERTIES =
      ImmutableList.of("proxyHost", "proxyPort", "proxyUser", "proxyPassword");

  private MavenSettingsProxyProvider() {}

  /**
   * Initializes proxy settings based on Maven settings if they are not already set by the user
   * directly.
   *
   * @param settings Maven settings
   */
  static void activateHttpAndHttpsProxies(Settings settings, SettingsDecrypter decrypter)
      throws MojoExecutionException {
    List<Proxy> proxies = new ArrayList<>(2);
    for (String protocol : ImmutableList.of("http", "https")) {
      if (areProxyPropertiesSet(protocol)) {
        continue;
      }
      settings.getProxies().stream()
          .filter(Proxy::isActive)
          .filter(proxy -> protocol.equals(proxy.getProtocol()))
          .findFirst()
          .ifPresent(proxies::add);
    }

    if (proxies.isEmpty()) {
      return;
    }

    SettingsDecryptionRequest request = new DefaultSettingsDecryptionRequest().setProxies(proxies);
    SettingsDecryptionResult result = decrypter.decrypt(request);

    for (SettingsProblem problem : result.getProblems()) {
      if (problem.getSeverity() == SettingsProblem.Severity.ERROR
          || problem.getSeverity() == SettingsProblem.Severity.FATAL) {
        throw new MojoExecutionException(
            "Unable to decrypt proxy info from settings.xml: " + problem);
      }
    }

    result.getProxies().forEach(MavenSettingsProxyProvider::setProxyProperties);
  }

  /**
   * Set proxy system properties based on Maven proxy configuration.
   *
   * @param proxy Maven proxy settings
   */
  @VisibleForTesting
  static void setProxyProperties(Proxy proxy) {
    String protocol = proxy.getProtocol();

    setPropertySafe(protocol + ".proxyHost", proxy.getHost());
    setPropertySafe(protocol + ".proxyPort", String.valueOf(proxy.getPort()));
    setPropertySafe(protocol + ".proxyUser", proxy.getUsername());
    setPropertySafe(protocol + ".proxyPassword", proxy.getPassword());
    setPropertySafe("http.nonProxyHosts", proxy.getNonProxyHosts());
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
    return PROXY_PROPERTIES.stream()
        .anyMatch(property -> System.getProperty(protocol + "." + property) != null);
  }
}
