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
import com.google.cloud.tools.jib.maven.JibPluginConfiguration.AuthConfiguration;
import com.google.cloud.tools.jib.plugins.common.AuthProperty;
import com.google.cloud.tools.jib.plugins.common.InferredAuthRetrievalException;
import com.google.common.collect.ImmutableMap;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

/** Test for {@link MavenRawConfiguration}. */
public class MavenRawConfigurationTest {

  @Test
  public void testGetters() throws InferredAuthRetrievalException {
    JibPluginConfiguration jibPluginConfiguration = Mockito.mock(JibPluginConfiguration.class);
    EventDispatcher eventDispatcher = Mockito.mock(EventDispatcher.class);

    Server server = Mockito.mock(Server.class);
    Mockito.when(server.getUsername()).thenReturn("maven settings user");
    Mockito.when(server.getPassword()).thenReturn("maven settings password");

    Settings mavenSettings = Mockito.mock(Settings.class);
    Mockito.when(mavenSettings.getServer("base registry")).thenReturn(server);

    MavenSession mavenSession = Mockito.mock(MavenSession.class);
    Mockito.when(mavenSession.getSettings()).thenReturn(mavenSettings);

    AuthConfiguration auth = Mockito.mock(AuthConfiguration.class);
    Mockito.when(auth.getUsername()).thenReturn("user");
    Mockito.when(auth.getPassword()).thenReturn("password");

    Mockito.when(jibPluginConfiguration.getSession()).thenReturn(mavenSession);
    Mockito.when(jibPluginConfiguration.getBaseImageAuth()).thenReturn(auth);

    Mockito.when(jibPluginConfiguration.getAllowInsecureRegistries()).thenReturn(true);
    Mockito.when(jibPluginConfiguration.getAppRoot()).thenReturn("/app/root");
    Mockito.when(jibPluginConfiguration.getArgs()).thenReturn(Arrays.asList("--log", "info"));
    Mockito.when(jibPluginConfiguration.getBaseImage()).thenReturn("openjdk:15");
    Mockito.when(jibPluginConfiguration.getBaseImageCredentialHelperName()).thenReturn("gcr");
    Mockito.when(jibPluginConfiguration.getEntrypoint()).thenReturn(Arrays.asList("java", "Main"));
    Mockito.when(jibPluginConfiguration.getEnvironment())
        .thenReturn(new HashMap<>(ImmutableMap.of("currency", "dollar")));
    Mockito.when(jibPluginConfiguration.getExposedPorts()).thenReturn(Arrays.asList("80/tcp", "0"));
    Mockito.when(jibPluginConfiguration.getJvmFlags()).thenReturn(Arrays.asList("-cp", "."));
    Mockito.when(jibPluginConfiguration.getLabels())
        .thenReturn(new HashMap<>(ImmutableMap.of("unit", "cm")));
    Mockito.when(jibPluginConfiguration.getMainClass()).thenReturn("com.example.Main");
    Mockito.when(jibPluginConfiguration.getTargetImageAdditionalTags())
        .thenReturn(new HashSet<>(Arrays.asList("additional", "tags")));
    Mockito.when(jibPluginConfiguration.getUseCurrentTimestamp()).thenReturn(true);
    Mockito.when(jibPluginConfiguration.getUseOnlyProjectCache()).thenReturn(true);
    Mockito.when(jibPluginConfiguration.getUser()).thenReturn("admin:wheel");

    MavenRawConfiguration rawConfiguration =
        new MavenRawConfiguration(jibPluginConfiguration, eventDispatcher);

    AuthProperty fromAuth = rawConfiguration.getFromAuth();
    Assert.assertEquals("user", fromAuth.getUsername());
    Assert.assertEquals("password", fromAuth.getPassword());
    Assert.assertEquals("<test><auth>", rawConfiguration.getAuthDescriptor("test"));
    Assert.assertEquals(
        "<test><auth><username>", rawConfiguration.getUsernameAuthDescriptor("test"));
    Assert.assertEquals(
        "<test><auth><password>", rawConfiguration.getPasswordAuthDescriptor("test"));

    AuthProperty mavenSettingsAuth = rawConfiguration.getInferredAuth("base registry").get();
    Assert.assertEquals("maven settings user", mavenSettingsAuth.getUsername());
    Assert.assertEquals("maven settings password", mavenSettingsAuth.getPassword());

    Assert.assertFalse(rawConfiguration.getInferredAuth("unknown registry").isPresent());

    Assert.assertTrue(rawConfiguration.getAllowInsecureRegistries());
    Assert.assertEquals(Arrays.asList("java", "Main"), rawConfiguration.getEntrypoint().get());
    Assert.assertEquals(
        new HashMap<>(ImmutableMap.of("currency", "dollar")), rawConfiguration.getEnvironment());
    Assert.assertEquals("/app/root", rawConfiguration.getAppRoot());
    Assert.assertEquals("gcr", rawConfiguration.getFromCredHelper().get());
    Assert.assertEquals("openjdk:15", rawConfiguration.getFromImage().get());
    Assert.assertEquals(Arrays.asList("-cp", "."), rawConfiguration.getJvmFlags());
    Assert.assertEquals(new HashMap<>(ImmutableMap.of("unit", "cm")), rawConfiguration.getLabels());
    Assert.assertEquals("com.example.Main", rawConfiguration.getMainClass().get());
    Assert.assertEquals(Arrays.asList("80/tcp", "0"), rawConfiguration.getPorts());
    Assert.assertEquals(
        Arrays.asList("--log", "info"), rawConfiguration.getProgramArguments().get());
    Assert.assertEquals(
        new HashSet<>(Arrays.asList("additional", "tags")), rawConfiguration.getToTags());
    Assert.assertTrue(rawConfiguration.getUseCurrentTimestamp());
    Assert.assertTrue(rawConfiguration.getUseOnlyProjectCache());
    Assert.assertEquals("admin:wheel", rawConfiguration.getUser().get());

    Mockito.verifyNoMoreInteractions(eventDispatcher);
  }
}
