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

package com.google.cloud.tools.jib.gradle;

import com.google.cloud.tools.jib.configuration.ContainerConfiguration;
import com.google.cloud.tools.jib.frontend.JavaLayerConfigurations;
import com.google.cloud.tools.jib.image.InvalidImageReferenceException;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class PluginConfigurationProcessorTest {
  @Mock GradleJibLogger gradleJibLogger;
  @Mock JibExtension jibExtension;
  @Mock ImageParameters imageParameters;
  @Mock ContainerParameters containerParameters;
  @Mock GradleProjectProperties projectProperties;

  @Before
  public void setUp() throws Exception {
    Mockito.doReturn("gcr.io/distroless/java").when(jibExtension).getBaseImage();
    Mockito.doReturn(imageParameters).when(jibExtension).getFrom();
    Mockito.doReturn(new AuthParameters("mock")).when(imageParameters).getAuth();
    Mockito.doReturn(containerParameters).when(jibExtension).getContainer();
    Mockito.doReturn(Collections.emptyList()).when(containerParameters).getEntrypoint();

    Mockito.doReturn(JavaLayerConfigurations.builder().build())
        .when(projectProperties)
        .getJavaLayerConfigurations();
    Mockito.doReturn("java.lang.Object").when(projectProperties).getMainClass(jibExtension);
  }

  @Test
  public void testBase() throws InvalidImageReferenceException {
    PluginConfigurationProcessor processor =
        PluginConfigurationProcessor.processCommonConfiguration(
            gradleJibLogger, jibExtension, projectProperties);
    ContainerConfiguration configuration = processor.getContainerConfigurationBuilder().build();
    Assert.assertEquals(
        Arrays.asList(
            "java", "-cp", "/app/resources/:/app/classes/:/app/libs/*", "java.lang.Object"),
        configuration.getEntrypoint());
    Mockito.verifyZeroInteractions(gradleJibLogger);
  }

  @Test
  public void testEntrypoint() throws InvalidImageReferenceException {
    Mockito.doReturn(Arrays.asList("custom", "entrypoint"))
        .when(containerParameters)
        .getEntrypoint();

    PluginConfigurationProcessor processor =
        PluginConfigurationProcessor.processCommonConfiguration(
            gradleJibLogger, jibExtension, projectProperties);
    ContainerConfiguration configuration = processor.getContainerConfigurationBuilder().build();

    Assert.assertEquals(Arrays.asList("custom", "entrypoint"), configuration.getEntrypoint());
    Mockito.verifyZeroInteractions(gradleJibLogger);
  }

  @Test
  public void testEntrypoint_warningOnJvmFlags() throws InvalidImageReferenceException {
    Mockito.doReturn(Arrays.asList("custom", "entrypoint"))
        .when(containerParameters)
        .getEntrypoint();
    Mockito.doReturn(Arrays.asList("jvmFlag")).when(jibExtension).getJvmFlags();

    PluginConfigurationProcessor processor =
        PluginConfigurationProcessor.processCommonConfiguration(
            gradleJibLogger, jibExtension, projectProperties);
    ContainerConfiguration configuration = processor.getContainerConfigurationBuilder().build();

    Assert.assertEquals(Arrays.asList("custom", "entrypoint"), configuration.getEntrypoint());
    Mockito.verify(gradleJibLogger)
        .warn("mainClass and jvmFlags are ignored when entrypoint is specified");
  }

  @Test
  public void testEntrypoint_warningOnMainclass() throws InvalidImageReferenceException {
    Mockito.doReturn(Arrays.asList("custom", "entrypoint"))
        .when(containerParameters)
        .getEntrypoint();
    Mockito.doReturn("java.util.Object").when(jibExtension).getMainClass();

    PluginConfigurationProcessor processor =
        PluginConfigurationProcessor.processCommonConfiguration(
            gradleJibLogger, jibExtension, projectProperties);
    ContainerConfiguration configuration = processor.getContainerConfigurationBuilder().build();

    Assert.assertEquals(Arrays.asList("custom", "entrypoint"), configuration.getEntrypoint());
    Mockito.verify(gradleJibLogger)
        .warn("mainClass and jvmFlags are ignored when entrypoint is specified");
  }
}
