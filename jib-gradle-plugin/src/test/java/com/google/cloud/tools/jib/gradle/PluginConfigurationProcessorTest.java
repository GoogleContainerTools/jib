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

package com.google.cloud.tools.jib.gradle;

import com.google.cloud.tools.jib.configuration.ContainerConfiguration;
import com.google.cloud.tools.jib.filesystem.AbsoluteUnixPath;
import com.google.cloud.tools.jib.frontend.JavaLayerConfigurations;
import com.google.cloud.tools.jib.image.InvalidImageReferenceException;
import com.google.common.collect.ImmutableList;
import java.util.Arrays;
import java.util.Collections;
import org.gradle.api.GradleException;
import org.gradle.api.logging.Logger;
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

  @Mock private Logger mockLogger;
  @Mock private JibExtension mockJibExtension;
  @Mock private BaseImageParameters mockBaseImageParameters;
  @Mock private ContainerParameters mockContainerParameters;
  @Mock private GradleProjectProperties mockProjectProperties;

  @Before
  public void setUp() throws Exception {
    Mockito.doReturn("gcr.io/distroless/java").when(mockJibExtension).getBaseImage();
    Mockito.doReturn(mockBaseImageParameters).when(mockJibExtension).getFrom();
    Mockito.doReturn(new AuthParameters("mock")).when(mockBaseImageParameters).getAuth();
    Mockito.doReturn(mockContainerParameters).when(mockJibExtension).getContainer();
    Mockito.doReturn(Collections.emptyList()).when(mockContainerParameters).getEntrypoint();
    Mockito.doReturn("/app").when(mockContainerParameters).getAppRoot();

    Mockito.doReturn(JavaLayerConfigurations.builder().build())
        .when(mockProjectProperties)
        .getJavaLayerConfigurations();
    Mockito.doReturn("java.lang.Object").when(mockProjectProperties).getMainClass(mockJibExtension);
  }

  /** Test with our default mocks, which try to mimic the bare Gradle configuration. */
  @Test
  public void testPluginConfigurationProcessor_defaults() throws InvalidImageReferenceException {
    PluginConfigurationProcessor processor =
        PluginConfigurationProcessor.processCommonConfiguration(
            mockLogger, mockJibExtension, mockProjectProperties);
    ContainerConfiguration configuration = processor.getContainerConfigurationBuilder().build();
    Assert.assertEquals(
        Arrays.asList("java", "-cp", "/app/resources:/app/classes:/app/libs/*", "java.lang.Object"),
        configuration.getEntrypoint());
    Mockito.verifyZeroInteractions(mockLogger);
  }

  @Test
  public void testEntrypoint() throws InvalidImageReferenceException {
    Mockito.doReturn(Arrays.asList("custom", "entrypoint"))
        .when(mockContainerParameters)
        .getEntrypoint();

    PluginConfigurationProcessor processor =
        PluginConfigurationProcessor.processCommonConfiguration(
            mockLogger, mockJibExtension, mockProjectProperties);
    ContainerConfiguration configuration = processor.getContainerConfigurationBuilder().build();

    Assert.assertEquals(Arrays.asList("custom", "entrypoint"), configuration.getEntrypoint());
    Mockito.verifyZeroInteractions(mockLogger);
  }

  @Test
  public void testEntrypoint_warningOnJvmFlags() throws InvalidImageReferenceException {
    Mockito.doReturn(Arrays.asList("custom", "entrypoint"))
        .when(mockContainerParameters)
        .getEntrypoint();
    Mockito.doReturn(Arrays.asList("jvmFlag")).when(mockContainerParameters).getJvmFlags();

    PluginConfigurationProcessor processor =
        PluginConfigurationProcessor.processCommonConfiguration(
            mockLogger, mockJibExtension, mockProjectProperties);
    ContainerConfiguration configuration = processor.getContainerConfigurationBuilder().build();

    Assert.assertEquals(Arrays.asList("custom", "entrypoint"), configuration.getEntrypoint());
    Mockito.verify(mockLogger)
        .warn("mainClass and jvmFlags are ignored when entrypoint is specified");
  }

  @Test
  public void testEntrypoint_warningOnMainclass() throws InvalidImageReferenceException {
    Mockito.doReturn(Arrays.asList("custom", "entrypoint"))
        .when(mockContainerParameters)
        .getEntrypoint();
    Mockito.doReturn("java.util.Object").when(mockContainerParameters).getMainClass();

    PluginConfigurationProcessor processor =
        PluginConfigurationProcessor.processCommonConfiguration(
            mockLogger, mockJibExtension, mockProjectProperties);
    ContainerConfiguration configuration = processor.getContainerConfigurationBuilder().build();

    Assert.assertEquals(Arrays.asList("custom", "entrypoint"), configuration.getEntrypoint());
    Mockito.verify(mockLogger)
        .warn("mainClass and jvmFlags are ignored when entrypoint is specified");
  }

  @Test
  public void testEntrypointClasspath_nonDefaultAppRoot() throws InvalidImageReferenceException {
    Mockito.doReturn("/my/app").when(mockContainerParameters).getAppRoot();

    PluginConfigurationProcessor processor =
        PluginConfigurationProcessor.processCommonConfiguration(
            mockLogger, mockJibExtension, mockProjectProperties);
    ContainerConfiguration configuration = processor.getContainerConfigurationBuilder().build();

    Assert.assertEquals("java", configuration.getEntrypoint().get(0));
    Assert.assertEquals("-cp", configuration.getEntrypoint().get(1));
    Assert.assertEquals(
        "/my/app/resources:/my/app/classes:/my/app/libs/*", configuration.getEntrypoint().get(2));
  }

  @Test
  public void testWebappEntrypoint_default() throws InvalidImageReferenceException {
    Mockito.doReturn(true).when(mockProjectProperties).isWarProject();

    PluginConfigurationProcessor processor =
        PluginConfigurationProcessor.processCommonConfiguration(
            mockLogger, mockJibExtension, mockProjectProperties);
    ContainerConfiguration configuration = processor.getContainerConfigurationBuilder().build();

    Assert.assertEquals(
        ImmutableList.of("java", "-jar", "/jetty/start.jar"), configuration.getEntrypoint());
  }

  @Test
  public void testGetAppRootChecked() {
    Mockito.doReturn("/some/root").when(mockContainerParameters).getAppRoot();

    Assert.assertEquals(
        AbsoluteUnixPath.get("/some/root"),
        PluginConfigurationProcessor.getAppRootChecked(mockJibExtension));
  }

  @Test
  public void testGetAppRootChecked_errorOnNonAbsolutePath() {
    Mockito.doReturn("relative/path").when(mockContainerParameters).getAppRoot();

    try {
      PluginConfigurationProcessor.getAppRootChecked(mockJibExtension);
      Assert.fail();
    } catch (GradleException ex) {
      Assert.assertEquals(
          "container.appRoot is not an absolute Unix-style path: relative/path", ex.getMessage());
    }
  }

  @Test
  public void testGetAppRootChecked_errorOnWindowsPath() {
    Mockito.doReturn("\\windows\\path").when(mockContainerParameters).getAppRoot();

    try {
      PluginConfigurationProcessor.getAppRootChecked(mockJibExtension);
      Assert.fail();
    } catch (GradleException ex) {
      Assert.assertEquals(
          "container.appRoot is not an absolute Unix-style path: \\windows\\path", ex.getMessage());
    }
  }

  @Test
  public void testGetAppRootChecked_errorOnWindowsPathWithDriveLetter() {
    Mockito.doReturn("C:\\windows\\path").when(mockContainerParameters).getAppRoot();

    try {
      PluginConfigurationProcessor.getAppRootChecked(mockJibExtension);
      Assert.fail();
    } catch (GradleException ex) {
      Assert.assertEquals(
          "container.appRoot is not an absolute Unix-style path: C:\\windows\\path",
          ex.getMessage());
    }
  }

  // TODO should test other behaviours
}
