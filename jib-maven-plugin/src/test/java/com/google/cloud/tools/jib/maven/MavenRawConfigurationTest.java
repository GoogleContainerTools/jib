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

import com.google.cloud.tools.jib.event.EventHandlers;
import com.google.cloud.tools.jib.maven.JibPluginConfiguration.FromAuthConfiguration;
import com.google.cloud.tools.jib.plugins.common.AuthProperty;
import com.google.cloud.tools.jib.plugins.common.RawConfiguration.CredHelperConfiguration;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Optional;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

/** Test for {@link MavenRawConfiguration}. */
public class MavenRawConfigurationTest {

  @Test
  public void testGetters() {
    JibPluginConfiguration jibPluginConfiguration = Mockito.mock(JibPluginConfiguration.class);
    EventHandlers eventHandlers = Mockito.mock(EventHandlers.class);

    Server server = Mockito.mock(Server.class);
    Mockito.when(server.getUsername()).thenReturn("maven settings user");
    Mockito.when(server.getPassword()).thenReturn("maven settings password");

    Settings mavenSettings = Mockito.mock(Settings.class);
    Mockito.when(mavenSettings.getServer("base registry")).thenReturn(server);

    MavenSession mavenSession = Mockito.mock(MavenSession.class);
    Mockito.when(mavenSession.getSettings()).thenReturn(mavenSettings);

    FromAuthConfiguration auth = Mockito.mock(FromAuthConfiguration.class);
    Mockito.when(auth.getUsername()).thenReturn("user");
    Mockito.when(auth.getPassword()).thenReturn("password");
    Mockito.when(auth.getAuthDescriptor()).thenReturn("<from><auth>");
    Mockito.when(auth.getUsernameDescriptor()).thenReturn("<from><auth><username>");
    Mockito.when(auth.getPasswordDescriptor()).thenReturn("<from><auth><password>");

    Mockito.when(jibPluginConfiguration.getSession()).thenReturn(mavenSession);
    Mockito.when(jibPluginConfiguration.getBaseImageAuth()).thenReturn(auth);

    Mockito.when(jibPluginConfiguration.getAllowInsecureRegistries()).thenReturn(true);
    Mockito.when(jibPluginConfiguration.getAppRoot()).thenReturn("/app/root");
    Mockito.when(jibPluginConfiguration.getArgs()).thenReturn(Arrays.asList("--log", "info"));
    Mockito.when(jibPluginConfiguration.getBaseImage()).thenReturn("openjdk:15");

    CredHelperConfiguration baseImageCredHelperConfig = Mockito.mock(CredHelperConfiguration.class);
    Mockito.when(baseImageCredHelperConfig.getHelperName()).thenReturn(Optional.of("gcr"));
    Mockito.when(baseImageCredHelperConfig.getEnvironment())
        .thenReturn(Collections.singletonMap("ENV_VARIABLE", "Value1"));
    Mockito.when(jibPluginConfiguration.getBaseImageCredHelperConfig())
        .thenReturn(baseImageCredHelperConfig);

    CredHelperConfiguration targetImageCredHelperConfig =
        Mockito.mock(CredHelperConfiguration.class);
    Mockito.when(targetImageCredHelperConfig.getHelperName()).thenReturn(Optional.of("gcr"));
    Mockito.when(targetImageCredHelperConfig.getEnvironment())
        .thenReturn(Collections.singletonMap("ENV_VARIABLE", "Value2"));
    Mockito.when(jibPluginConfiguration.getTargetImageCredentialHelperConfig())
        .thenReturn(targetImageCredHelperConfig);

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
    Mockito.when(jibPluginConfiguration.getUser()).thenReturn("admin:wheel");
    Mockito.when(jibPluginConfiguration.getFilesModificationTime())
        .thenReturn("2011-12-03T22:42:05Z");
    Mockito.when(jibPluginConfiguration.getDockerClientExecutable()).thenReturn(Paths.get("test"));
    Mockito.when(jibPluginConfiguration.getDockerClientEnvironment())
        .thenReturn(new HashMap<>(ImmutableMap.of("docker", "client")));
    Mockito.when(jibPluginConfiguration.getDigestOutputPath()).thenReturn(Paths.get("digest/path"));
    Mockito.when(jibPluginConfiguration.getImageTagDigestOutputPath()).thenReturn(Paths.get("image-tag-digest/path"));
    Mockito.when(jibPluginConfiguration.getImageIdOutputPath()).thenReturn(Paths.get("id/path"));
    Mockito.when(jibPluginConfiguration.getImageJsonOutputPath())
        .thenReturn(Paths.get("json/path"));
    Mockito.when(jibPluginConfiguration.getTarOutputPath()).thenReturn(Paths.get("tar/path"));

    MavenRawConfiguration rawConfiguration = new MavenRawConfiguration(jibPluginConfiguration);

    AuthProperty fromAuth = rawConfiguration.getFromAuth();
    Assert.assertEquals("user", fromAuth.getUsername());
    Assert.assertEquals("password", fromAuth.getPassword());
    Assert.assertEquals("<from><auth>", fromAuth.getAuthDescriptor());
    Assert.assertEquals("<from><auth><username>", fromAuth.getUsernameDescriptor());
    Assert.assertEquals("<from><auth><password>", fromAuth.getPasswordDescriptor());

    Assert.assertTrue(rawConfiguration.getAllowInsecureRegistries());
    Assert.assertEquals(Arrays.asList("java", "Main"), rawConfiguration.getEntrypoint().get());
    Assert.assertEquals(
        new HashMap<>(ImmutableMap.of("currency", "dollar")), rawConfiguration.getEnvironment());
    Assert.assertEquals("/app/root", rawConfiguration.getAppRoot());
    Assert.assertEquals("gcr", rawConfiguration.getFromCredHelper().getHelperName().get());
    Assert.assertEquals(
        Collections.singletonMap("ENV_VARIABLE", "Value1"),
        rawConfiguration.getFromCredHelper().getEnvironment());
    Assert.assertEquals("gcr", rawConfiguration.getToCredHelper().getHelperName().get());
    Assert.assertEquals(
        Collections.singletonMap("ENV_VARIABLE", "Value2"),
        rawConfiguration.getToCredHelper().getEnvironment());
    Assert.assertEquals("openjdk:15", rawConfiguration.getFromImage().get());
    Assert.assertEquals(Arrays.asList("-cp", "."), rawConfiguration.getJvmFlags());
    Assert.assertEquals(new HashMap<>(ImmutableMap.of("unit", "cm")), rawConfiguration.getLabels());
    Assert.assertEquals("com.example.Main", rawConfiguration.getMainClass().get());
    Assert.assertEquals(Arrays.asList("80/tcp", "0"), rawConfiguration.getPorts());
    Assert.assertEquals(
        Arrays.asList("--log", "info"), rawConfiguration.getProgramArguments().get());
    Assert.assertEquals(
        new HashSet<>(Arrays.asList("additional", "tags")),
        Sets.newHashSet(rawConfiguration.getToTags()));
    Assert.assertEquals("admin:wheel", rawConfiguration.getUser().get());
    Assert.assertEquals("2011-12-03T22:42:05Z", rawConfiguration.getFilesModificationTime());
    Assert.assertEquals(Paths.get("test"), rawConfiguration.getDockerExecutable().get());
    Assert.assertEquals(
        new HashMap<>(ImmutableMap.of("docker", "client")),
        rawConfiguration.getDockerEnvironment());
    Assert.assertEquals(Paths.get("digest/path"), jibPluginConfiguration.getDigestOutputPath());
    Assert.assertEquals(Paths.get("image-tag-digest/path"), jibPluginConfiguration.getImageTagDigestOutputPath());
    Assert.assertEquals(Paths.get("id/path"), jibPluginConfiguration.getImageIdOutputPath());
    Assert.assertEquals(Paths.get("json/path"), jibPluginConfiguration.getImageJsonOutputPath());
    Assert.assertEquals(Paths.get("tar/path"), jibPluginConfiguration.getTarOutputPath());

    Mockito.verifyNoMoreInteractions(eventHandlers);
  }
}
