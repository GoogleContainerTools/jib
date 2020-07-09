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

import java.nio.file.Paths;
import java.util.Arrays;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.settings.Proxy;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.crypto.SettingsDecrypter;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.RestoreSystemProperties;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

/** Test for {@link MavenSettingsProxyProvider}. */
@RunWith(MockitoJUnitRunner.class)
public class MavenSettingsProxyProviderTest {

  @Rule public final RestoreSystemProperties systemPropertyRestorer = new RestoreSystemProperties();

  private static Settings noActiveProxiesSettings;
  private static Settings httpOnlyProxySettings;
  private static Settings httpsOnlyProxySettings;
  private static Settings mixedProxyEncryptedSettings;
  private static Settings badProxyEncryptedSettings;
  private static SettingsDecrypter settingsDecrypter;
  private static SettingsDecrypter emptySettingsDecrypter;

  @BeforeClass
  public static void setUpTestFixtures() {
    noActiveProxiesSettings =
        SettingsFixture.newSettings(
            Paths.get("src/test/resources/maven/settings/no-active-proxy-settings.xml"));
    httpOnlyProxySettings =
        SettingsFixture.newSettings(
            Paths.get("src/test/resources/maven/settings/http-only-proxy-settings.xml"));
    httpsOnlyProxySettings =
        SettingsFixture.newSettings(
            Paths.get("src/test/resources/maven/settings/https-only-proxy-settings.xml"));
    mixedProxyEncryptedSettings =
        SettingsFixture.newSettings(
            Paths.get("src/test/resources/maven/settings/encrypted-proxy-settings.xml"));
    badProxyEncryptedSettings =
        SettingsFixture.newSettings(
            Paths.get("src/test/resources/maven/settings/bad-encrypted-proxy-settings.xml"));
    settingsDecrypter =
        SettingsFixture.newSettingsDecrypter(
            Paths.get("src/test/resources/maven/settings/settings-security.xml"));
    emptySettingsDecrypter =
        SettingsFixture.newSettingsDecrypter(
            Paths.get("src/test/resources/maven/settings/settings-security.empty.xml"));
  }

  @Before
  public void setUp() {
    Arrays.asList(
            "http.proxyHost",
            "http.proxyPort",
            "http.proxyUser",
            "http.proxyPassword",
            "https.proxyHost",
            "https.proxyPort",
            "https.proxyUser",
            "https.proxyPassword",
            "http.nonProxyHosts")
        .forEach(System::clearProperty);
  }

  @Test
  public void testAreProxyPropertiesSet() {
    Assert.assertFalse(MavenSettingsProxyProvider.areProxyPropertiesSet("http"));
    Assert.assertFalse(MavenSettingsProxyProvider.areProxyPropertiesSet("https"));
  }

  @Test
  public void testAreProxyPropertiesSet_httpHostSet() {
    System.setProperty("http.proxyHost", "host");
    Assert.assertTrue(MavenSettingsProxyProvider.areProxyPropertiesSet("http"));
    Assert.assertFalse(MavenSettingsProxyProvider.areProxyPropertiesSet("https"));
  }

  @Test
  public void testAreProxyPropertiesSet_httpsHostSet() {
    System.setProperty("https.proxyHost", "host");
    Assert.assertFalse(MavenSettingsProxyProvider.areProxyPropertiesSet("http"));
    Assert.assertTrue(MavenSettingsProxyProvider.areProxyPropertiesSet("https"));
  }

  @Test
  public void testAreProxyPropertiesSet_httpPortSet() {
    System.setProperty("http.proxyPort", "port");
    Assert.assertTrue(MavenSettingsProxyProvider.areProxyPropertiesSet("http"));
    Assert.assertFalse(MavenSettingsProxyProvider.areProxyPropertiesSet("https"));
  }

  @Test
  public void testAreProxyPropertiesSet_httpsPortSet() {
    System.setProperty("https.proxyPort", "port");
    Assert.assertFalse(MavenSettingsProxyProvider.areProxyPropertiesSet("http"));
    Assert.assertTrue(MavenSettingsProxyProvider.areProxyPropertiesSet("https"));
  }

  @Test
  public void testAreProxyPropertiesSet_httpUserSet() {
    System.setProperty("http.proxyUser", "user");
    Assert.assertTrue(MavenSettingsProxyProvider.areProxyPropertiesSet("http"));
    Assert.assertFalse(MavenSettingsProxyProvider.areProxyPropertiesSet("https"));
  }

  @Test
  public void testAreProxyPropertiesSet_httpsUserSet() {
    System.setProperty("https.proxyUser", "user");
    Assert.assertFalse(MavenSettingsProxyProvider.areProxyPropertiesSet("http"));
    Assert.assertTrue(MavenSettingsProxyProvider.areProxyPropertiesSet("https"));
  }

  @Test
  public void testAreProxyPropertiesSet_httpPasswordSet() {
    System.setProperty("http.proxyPassword", "password");
    Assert.assertTrue(MavenSettingsProxyProvider.areProxyPropertiesSet("http"));
    Assert.assertFalse(MavenSettingsProxyProvider.areProxyPropertiesSet("https"));
  }

  @Test
  public void testAreProxyPropertiesSet_httpsPasswordSet() {
    System.setProperty("https.proxyPassword", "password");
    Assert.assertFalse(MavenSettingsProxyProvider.areProxyPropertiesSet("http"));
    Assert.assertTrue(MavenSettingsProxyProvider.areProxyPropertiesSet("https"));
  }

  @Test
  public void testAreProxyPropertiesSet_ignoresHttpNonProxyHosts() {
    System.setProperty("http.nonProxyHosts", "non proxy hosts");
    Assert.assertFalse(MavenSettingsProxyProvider.areProxyPropertiesSet("http"));
    Assert.assertFalse(MavenSettingsProxyProvider.areProxyPropertiesSet("https"));
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

    MavenSettingsProxyProvider.setProxyProperties(httpProxy);
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
    MavenSettingsProxyProvider.setProxyProperties(httpsProxy);
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

    MavenSettingsProxyProvider.setProxyProperties(httpProxy);
    Assert.assertEquals("http://host", System.getProperty("http.proxyHost"));
    Assert.assertNull(System.getProperty("http.proxyUser"));
    Assert.assertNull(System.getProperty("http.proxyPassword"));
    Assert.assertNull(System.getProperty("http.nonProxyHosts"));

    Proxy httpsProxy = new Proxy();
    httpsProxy.setProtocol("https");
    httpsProxy.setUsername("https user");
    httpsProxy.setPassword("https pass");
    MavenSettingsProxyProvider.setProxyProperties(httpsProxy);
    Assert.assertNull(System.getProperty("https.proxyHost"));
    Assert.assertEquals("https user", System.getProperty("https.proxyUser"));
    Assert.assertEquals("https pass", System.getProperty("https.proxyPassword"));
  }

  @Test
  public void testActivateHttpAndHttpsProxies_noActiveProxy() throws MojoExecutionException {

    MavenSettingsProxyProvider.activateHttpAndHttpsProxies(
        noActiveProxiesSettings, settingsDecrypter);

    Assert.assertNull(System.getProperty("http.proxyHost"));
    Assert.assertNull(System.getProperty("https.proxyHost"));
  }

  @Test
  public void testActivateHttpAndHttpsProxies_firstActiveHttpProxy() throws MojoExecutionException {
    MavenSettingsProxyProvider.activateHttpAndHttpsProxies(
        httpOnlyProxySettings, settingsDecrypter);

    Assert.assertEquals("proxy2.example.com", System.getProperty("http.proxyHost"));
    Assert.assertNull(System.getProperty("https.proxyHost"));
  }

  @Test
  public void testActivateHttpAndHttpsProxies_firstActiveHttpsProxy()
      throws MojoExecutionException {
    MavenSettingsProxyProvider.activateHttpAndHttpsProxies(
        httpsOnlyProxySettings, settingsDecrypter);

    Assert.assertEquals("proxy2.example.com", System.getProperty("https.proxyHost"));
    Assert.assertNull(System.getProperty("http.proxyHost"));
  }

  @Test
  public void testActivateHttpAndHttpsProxies_encryptedProxy() throws MojoExecutionException {
    MavenSettingsProxyProvider.activateHttpAndHttpsProxies(
        mixedProxyEncryptedSettings, settingsDecrypter);

    Assert.assertEquals("password1", System.getProperty("http.proxyPassword"));
    Assert.assertEquals("password2", System.getProperty("https.proxyPassword"));
  }

  @Test
  public void testActivateHttpAndHttpsProxies_dontOverwriteUserHttp()
      throws MojoExecutionException {
    System.setProperty("http.proxyHost", "host");
    MavenSettingsProxyProvider.activateHttpAndHttpsProxies(
        mixedProxyEncryptedSettings, settingsDecrypter);

    Assert.assertNull(System.getProperty("http.proxyPassword"));
    Assert.assertEquals("password2", System.getProperty("https.proxyPassword"));
  }

  @Test
  public void testActivateHttpAndHttpsProxies_dontOverwriteUserHttps()
      throws MojoExecutionException {
    System.setProperty("https.proxyHost", "host");
    MavenSettingsProxyProvider.activateHttpAndHttpsProxies(
        mixedProxyEncryptedSettings, settingsDecrypter);

    Assert.assertEquals("password1", System.getProperty("http.proxyPassword"));
    Assert.assertNull(System.getProperty("https.proxyPassword"));
  }

  @Test
  public void testActivateHttpAndHttpsProxies_decryptionFailure() {
    try {
      MavenSettingsProxyProvider.activateHttpAndHttpsProxies(
          badProxyEncryptedSettings, settingsDecrypter);
      Assert.fail();
    } catch (MojoExecutionException ex) {
      MatcherAssert.assertThat(
          ex.getMessage(),
          CoreMatchers.startsWith("Unable to decrypt proxy info from settings.xml:"));
    }
  }

  @Test
  public void testActivateHttpAndHttpsProxies_emptySettingsDecrypter() {
    try {
      MavenSettingsProxyProvider.activateHttpAndHttpsProxies(
          mixedProxyEncryptedSettings, emptySettingsDecrypter);
      Assert.fail();
    } catch (MojoExecutionException ex) {
      MatcherAssert.assertThat(
          ex.getMessage(),
          CoreMatchers.startsWith("Unable to decrypt proxy info from settings.xml:"));
    }
  }
}
