/*
 * Copyright 2018 Google LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud.tools.jib.maven;

import com.google.cloud.tools.jib.configuration.ContainerConfiguration;
import com.google.cloud.tools.jib.frontend.JavaLayerConfigurations;
import com.google.cloud.tools.jib.maven.JibPluginConfiguration.AuthConfiguration;
import java.util.Arrays;
import java.util.Collections;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.settings.Settings;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests for {@link PluginConfigurationProcessor}. */
@RunWith(MockitoJUnitRunner.class)
public class PluginConfigurationProcessorTest {

  @Mock MavenJibLogger mockMavenJibLogger;
  @Mock JibPluginConfiguration mockJibPluginConfiguration;
  @Mock MavenProjectProperties mockProjectProperties;
  @Mock MavenSession mockMavenSession;
  @Mock Settings mockMavenSettings;

  @Before
  public void setUp() throws Exception {
    Mockito.doReturn(mockMavenSession).when(mockJibPluginConfiguration).getSession();
    Mockito.doReturn(mockMavenSettings).when(mockMavenSession).getSettings();

    Mockito.doReturn("gcr.io/distroless/java").when(mockJibPluginConfiguration).getBaseImage();
    Mockito.doReturn(new AuthConfiguration()).when(mockJibPluginConfiguration).getBaseImageAuth();
    Mockito.doReturn(Collections.emptyList()).when(mockJibPluginConfiguration).getEntrypoint();
    Mockito.doReturn(Collections.emptyList()).when(mockJibPluginConfiguration).getJvmFlags();
    Mockito.doReturn(Collections.emptyList()).when(mockJibPluginConfiguration).getArgs();
    Mockito.doReturn(Collections.emptyList()).when(mockJibPluginConfiguration).getExposedPorts();

    Mockito.doReturn(JavaLayerConfigurations.builder().build())
        .when(mockProjectProperties)
        .getJavaLayerConfigurations();
    Mockito.doReturn("java.lang.Object")
        .when(mockProjectProperties)
        .getMainClass(mockJibPluginConfiguration);
  }

  /** Test with our default mocks, which try to mimic the bare Maven configuration. */
  @Test
  public void testPluginConfigurationProcessor_defaults() throws MojoExecutionException {
    PluginConfigurationProcessor processor =
        PluginConfigurationProcessor.processCommonConfiguration(
            mockMavenJibLogger, mockJibPluginConfiguration, mockProjectProperties);
    ContainerConfiguration configuration = processor.getContainerConfigurationBuilder().build();
    Assert.assertEquals(
        Arrays.asList(
            "java", "-cp", "/app/resources/:/app/classes/:/app/libs/*", "java.lang.Object"),
        configuration.getEntrypoint());
    Mockito.verifyZeroInteractions(mockMavenJibLogger);
  }

  @Test
  public void testEntrypoint() throws MojoExecutionException {
    Mockito.doReturn(Arrays.asList("custom", "entrypoint"))
        .when(mockJibPluginConfiguration)
        .getEntrypoint();

    PluginConfigurationProcessor processor =
        PluginConfigurationProcessor.processCommonConfiguration(
            mockMavenJibLogger, mockJibPluginConfiguration, mockProjectProperties);
    ContainerConfiguration configuration = processor.getContainerConfigurationBuilder().build();

    Assert.assertEquals(Arrays.asList("custom", "entrypoint"), configuration.getEntrypoint());
    Mockito.verifyZeroInteractions(mockMavenJibLogger);
  }

  @Test
  public void testEntrypoint_warningOnJvmFlags() throws MojoExecutionException {
    Mockito.doReturn(Arrays.asList("custom", "entrypoint"))
        .when(mockJibPluginConfiguration)
        .getEntrypoint();
    Mockito.doReturn(Arrays.asList("jvmFlag")).when(mockJibPluginConfiguration).getJvmFlags();

    PluginConfigurationProcessor processor =
        PluginConfigurationProcessor.processCommonConfiguration(
            mockMavenJibLogger, mockJibPluginConfiguration, mockProjectProperties);
    ContainerConfiguration configuration = processor.getContainerConfigurationBuilder().build();

    Assert.assertEquals(Arrays.asList("custom", "entrypoint"), configuration.getEntrypoint());
    Mockito.verify(mockMavenJibLogger)
        .warn("<mainClass> and <jvmFlags> are ignored when <entrypoint> is specified");
  }

  @Test
  public void testEntrypoint_warningOnMainclass() throws MojoExecutionException {
    Mockito.doReturn(Arrays.asList("custom", "entrypoint"))
        .when(mockJibPluginConfiguration)
        .getEntrypoint();
    Mockito.doReturn("java.util.Object").when(mockJibPluginConfiguration).getMainClass();

    PluginConfigurationProcessor processor =
        PluginConfigurationProcessor.processCommonConfiguration(
            mockMavenJibLogger, mockJibPluginConfiguration, mockProjectProperties);
    ContainerConfiguration configuration = processor.getContainerConfigurationBuilder().build();

    Assert.assertEquals(Arrays.asList("custom", "entrypoint"), configuration.getEntrypoint());
    Mockito.verify(mockMavenJibLogger)
        .warn("<mainClass> and <jvmFlags> are ignored when <entrypoint> is specified");
  }

  // TODO should test other behaviours
}
