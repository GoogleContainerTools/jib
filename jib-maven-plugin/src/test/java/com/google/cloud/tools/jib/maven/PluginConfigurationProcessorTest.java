/*
 * Copyright 2018 Google Inc.
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

@RunWith(MockitoJUnitRunner.class)
public class PluginConfigurationProcessorTest {
  @Mock MavenJibLogger mavenJibLogger;
  @Mock JibPluginConfiguration jibPluginConfiguration;
  @Mock MavenProjectProperties projectProperties;
  @Mock MavenSession mavenSession;
  @Mock Settings mavenSettings;

  @Before
  public void setUp() throws Exception {
    Mockito.doReturn(mavenSession).when(jibPluginConfiguration).getSession();
    Mockito.doReturn(mavenSettings).when(mavenSession).getSettings();

    Mockito.doReturn("gcr.io/distroless/java").when(jibPluginConfiguration).getBaseImage();
    Mockito.doReturn(new AuthConfiguration()).when(jibPluginConfiguration).getBaseImageAuth();
    Mockito.doReturn(Collections.emptyList()).when(jibPluginConfiguration).getEntrypoint();
    Mockito.doReturn(Collections.emptyList()).when(jibPluginConfiguration).getJvmFlags();
    Mockito.doReturn(Collections.emptyList()).when(jibPluginConfiguration).getArgs();
    Mockito.doReturn(Collections.emptyList()).when(jibPluginConfiguration).getExposedPorts();

    Mockito.doReturn(JavaLayerConfigurations.builder().build())
        .when(projectProperties)
        .getJavaLayerConfigurations();
    Mockito.doReturn("java.lang.Object")
        .when(projectProperties)
        .getMainClass(jibPluginConfiguration);
  }

  @Test
  public void testBase() throws MojoExecutionException {
    PluginConfigurationProcessor processor =
        PluginConfigurationProcessor.processCommonConfiguration(
            mavenJibLogger, jibPluginConfiguration, projectProperties);
    ContainerConfiguration configuration = processor.getContainerConfigurationBuilder().build();
    Assert.assertEquals(
        Arrays.asList(
            "java", "-cp", "/app/resources/:/app/classes/:/app/libs/*", "java.lang.Object"),
        configuration.getEntrypoint());
    Mockito.verifyZeroInteractions(mavenJibLogger);
  }

  @Test
  public void testEntrypoint() throws MojoExecutionException {
    Mockito.doReturn(Arrays.asList("custom", "entrypoint"))
        .when(jibPluginConfiguration)
        .getEntrypoint();

    PluginConfigurationProcessor processor =
        PluginConfigurationProcessor.processCommonConfiguration(
            mavenJibLogger, jibPluginConfiguration, projectProperties);
    ContainerConfiguration configuration = processor.getContainerConfigurationBuilder().build();
    
    Assert.assertEquals(Arrays.asList("custom", "entrypoint"), configuration.getEntrypoint());
    Mockito.verifyZeroInteractions(mavenJibLogger);
  }

  @Test
  public void testEntrypoint_warningOnJvmFlags() throws MojoExecutionException {
    Mockito.doReturn(Arrays.asList("custom", "entrypoint"))
        .when(jibPluginConfiguration)
        .getEntrypoint();
    Mockito.doReturn(Arrays.asList("jvmFlag")).when(jibPluginConfiguration).getJvmFlags();

    PluginConfigurationProcessor processor =
        PluginConfigurationProcessor.processCommonConfiguration(
            mavenJibLogger, jibPluginConfiguration, projectProperties);
    ContainerConfiguration configuration = processor.getContainerConfigurationBuilder().build();
    
    Assert.assertEquals(Arrays.asList("custom", "entrypoint"), configuration.getEntrypoint());
    Mockito.verify(mavenJibLogger)
        .warn("<mainClass> and <jvmFlags> are ignored when <entrypoint> is specified");
  }

  @Test
  public void testEntrypoint_warningOnMainclass() throws MojoExecutionException {
    Mockito.doReturn(Arrays.asList("custom", "entrypoint"))
        .when(jibPluginConfiguration)
        .getEntrypoint();
    Mockito.doReturn("java.util.Object").when(jibPluginConfiguration).getMainClass();

    PluginConfigurationProcessor processor =
        PluginConfigurationProcessor.processCommonConfiguration(
            mavenJibLogger, jibPluginConfiguration, projectProperties);
    ContainerConfiguration configuration = processor.getContainerConfigurationBuilder().build();
    
    Assert.assertEquals(Arrays.asList("custom", "entrypoint"), configuration.getEntrypoint());
    Mockito.verify(mavenJibLogger)
        .warn("<mainClass> and <jvmFlags> are ignored when <entrypoint> is specified");
  }
}
