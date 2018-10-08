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

import com.google.cloud.tools.jib.api.Containerizer;
import com.google.cloud.tools.jib.api.JibContainerBuilder;
import com.google.cloud.tools.jib.api.JibContainerBuilderTestHelper;
import com.google.cloud.tools.jib.api.RegistryImage;
import com.google.cloud.tools.jib.configuration.BuildConfiguration;
import com.google.cloud.tools.jib.configuration.CacheDirectoryCreationException;
import com.google.cloud.tools.jib.event.EventHandlers;
import com.google.cloud.tools.jib.filesystem.AbsoluteUnixPath;
import com.google.cloud.tools.jib.frontend.JavaLayerConfigurations;
import com.google.cloud.tools.jib.image.InvalidImageReferenceException;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
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

  private static BuildConfiguration getBuildConfiguration(JibContainerBuilder jibContainerBuilder)
      throws InvalidImageReferenceException, IOException, CacheDirectoryCreationException {
    return JibContainerBuilderTestHelper.toBuildConfiguration(
        jibContainerBuilder,
        BuildConfiguration.builder(),
        Containerizer.to(RegistryImage.named("ignored")));
  }

  @Mock private Logger mockLogger;
  @Mock private JibExtension mockJibExtension;
  @Mock private BaseImageParameters mockBaseImageParameters;
  @Mock private ContainerParameters mockContainerParameters;
  @Mock private GradleProjectProperties mockProjectProperties;

  @Before
  public void setUp() {
    Mockito.when(mockBaseImageParameters.getImage()).thenReturn("gcr.io/distroless/java");
    Mockito.when(mockJibExtension.getFrom()).thenReturn(mockBaseImageParameters);
    Mockito.when(mockBaseImageParameters.getAuth()).thenReturn(new AuthParameters("mock"));
    Mockito.when(mockJibExtension.getContainer()).thenReturn(mockContainerParameters);
    Mockito.when(mockContainerParameters.getEntrypoint()).thenReturn(Collections.emptyList());
    Mockito.when(mockContainerParameters.getAppRoot()).thenReturn("/app");

    Mockito.when(mockProjectProperties.getJavaLayerConfigurations())
        .thenReturn(JavaLayerConfigurations.builder().build());
    Mockito.when(mockProjectProperties.getMainClass(mockJibExtension))
        .thenReturn("java.lang.Object");
    Mockito.when(mockProjectProperties.getEventHandlers()).thenReturn(new EventHandlers());
  }

  /** Test with our default mocks, which try to mimic the bare Gradle configuration. */
  @Test
  public void testPluginConfigurationProcessor_defaults()
      throws InvalidImageReferenceException, IOException, CacheDirectoryCreationException {
    PluginConfigurationProcessor processor =
        PluginConfigurationProcessor.processCommonConfiguration(
            mockLogger, mockJibExtension, mockProjectProperties);
    BuildConfiguration buildConfiguration =
        getBuildConfiguration(processor.getJibContainerBuilder());
    Assert.assertNotNull(buildConfiguration.getContainerConfiguration());
    Assert.assertEquals(
        Arrays.asList("java", "-cp", "/app/resources:/app/classes:/app/libs/*", "java.lang.Object"),
        buildConfiguration.getContainerConfiguration().getEntrypoint());
    Mockito.verifyZeroInteractions(mockLogger);
  }

  @Test
  public void testEntrypoint()
      throws InvalidImageReferenceException, IOException, CacheDirectoryCreationException {
    Mockito.when(mockContainerParameters.getEntrypoint())
        .thenReturn(Arrays.asList("custom", "entrypoint"));

    PluginConfigurationProcessor processor =
        PluginConfigurationProcessor.processCommonConfiguration(
            mockLogger, mockJibExtension, mockProjectProperties);
    BuildConfiguration buildConfiguration =
        getBuildConfiguration(processor.getJibContainerBuilder());

    Assert.assertNotNull(buildConfiguration.getContainerConfiguration());
    Assert.assertEquals(
        Arrays.asList("custom", "entrypoint"),
        buildConfiguration.getContainerConfiguration().getEntrypoint());
    Mockito.verifyZeroInteractions(mockLogger);
  }

  @Test
  public void testUser()
      throws InvalidImageReferenceException, IOException, CacheDirectoryCreationException {
    Mockito.when(mockContainerParameters.getUser()).thenReturn("customUser");

    PluginConfigurationProcessor processor =
        PluginConfigurationProcessor.processCommonConfiguration(
            mockLogger, mockJibExtension, mockProjectProperties);
    BuildConfiguration buildConfiguration =
        getBuildConfiguration(processor.getJibContainerBuilder());

    Assert.assertNotNull(buildConfiguration.getContainerConfiguration());
    Assert.assertEquals("customUser", buildConfiguration.getContainerConfiguration().getUser());
  }

  @Test
  public void testUser_null()
      throws InvalidImageReferenceException, IOException, CacheDirectoryCreationException {
    PluginConfigurationProcessor processor =
        PluginConfigurationProcessor.processCommonConfiguration(
            mockLogger, mockJibExtension, mockProjectProperties);
    BuildConfiguration buildConfiguration =
        getBuildConfiguration(processor.getJibContainerBuilder());

    Assert.assertNotNull(buildConfiguration.getContainerConfiguration());
    Assert.assertNull(buildConfiguration.getContainerConfiguration().getUser());
  }

  @Test
  public void testEntrypoint_warningOnJvmFlags()
      throws InvalidImageReferenceException, IOException, CacheDirectoryCreationException {
    Mockito.when(mockContainerParameters.getEntrypoint())
        .thenReturn(Arrays.asList("custom", "entrypoint"));
    Mockito.when(mockContainerParameters.getJvmFlags())
        .thenReturn(Collections.singletonList("jvmFlag"));

    PluginConfigurationProcessor processor =
        PluginConfigurationProcessor.processCommonConfiguration(
            mockLogger, mockJibExtension, mockProjectProperties);
    BuildConfiguration buildConfiguration =
        getBuildConfiguration(processor.getJibContainerBuilder());

    Assert.assertNotNull(buildConfiguration.getContainerConfiguration());
    Assert.assertEquals(
        Arrays.asList("custom", "entrypoint"),
        buildConfiguration.getContainerConfiguration().getEntrypoint());
    Mockito.verify(mockLogger)
        .warn("mainClass and jvmFlags are ignored when entrypoint is specified");
  }

  @Test
  public void testEntrypoint_warningOnMainclass()
      throws InvalidImageReferenceException, IOException, CacheDirectoryCreationException {
    Mockito.when(mockContainerParameters.getEntrypoint())
        .thenReturn(Arrays.asList("custom", "entrypoint"));
    Mockito.when(mockContainerParameters.getMainClass()).thenReturn("java.util.Object");

    PluginConfigurationProcessor processor =
        PluginConfigurationProcessor.processCommonConfiguration(
            mockLogger, mockJibExtension, mockProjectProperties);
    BuildConfiguration buildConfiguration =
        getBuildConfiguration(processor.getJibContainerBuilder());

    Assert.assertNotNull(buildConfiguration.getContainerConfiguration());
    Assert.assertEquals(
        Arrays.asList("custom", "entrypoint"),
        buildConfiguration.getContainerConfiguration().getEntrypoint());
    Mockito.verify(mockLogger)
        .warn("mainClass and jvmFlags are ignored when entrypoint is specified");
  }

  @Test
  public void testEntrypointClasspath_nonDefaultAppRoot()
      throws InvalidImageReferenceException, IOException, CacheDirectoryCreationException {
    Mockito.when(mockContainerParameters.getAppRoot()).thenReturn("/my/app");

    PluginConfigurationProcessor processor =
        PluginConfigurationProcessor.processCommonConfiguration(
            mockLogger, mockJibExtension, mockProjectProperties);
    BuildConfiguration buildConfiguration =
        getBuildConfiguration(processor.getJibContainerBuilder());

    Assert.assertNotNull(buildConfiguration.getContainerConfiguration());
    Assert.assertNotNull(buildConfiguration.getContainerConfiguration().getEntrypoint());
    Assert.assertEquals(
        "java", buildConfiguration.getContainerConfiguration().getEntrypoint().get(0));
    Assert.assertEquals(
        "-cp", buildConfiguration.getContainerConfiguration().getEntrypoint().get(1));
    Assert.assertEquals(
        "/my/app/resources:/my/app/classes:/my/app/libs/*",
        buildConfiguration.getContainerConfiguration().getEntrypoint().get(2));
  }

  @Test
  public void testWebAppEntrypoint_inferredFromBaseImage()
      throws InvalidImageReferenceException, IOException, CacheDirectoryCreationException {
    Mockito.when(mockProjectProperties.isWarProject()).thenReturn(true);

    PluginConfigurationProcessor processor =
        PluginConfigurationProcessor.processCommonConfiguration(
            mockLogger, mockJibExtension, mockProjectProperties);
    BuildConfiguration buildConfiguration =
        getBuildConfiguration(processor.getJibContainerBuilder());

    Assert.assertNotNull(buildConfiguration.getContainerConfiguration());
    Assert.assertNull(buildConfiguration.getContainerConfiguration().getEntrypoint());
  }

  @Test
  public void testGetAppRootChecked() {
    Mockito.when(mockContainerParameters.getAppRoot()).thenReturn("/some/root");

    Assert.assertEquals(
        AbsoluteUnixPath.get("/some/root"),
        PluginConfigurationProcessor.getAppRootChecked(mockJibExtension));
  }

  @Test
  public void testGetAppRootChecked_errorOnNonAbsolutePath() {
    Mockito.when(mockContainerParameters.getAppRoot()).thenReturn("relative/path");

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
    Mockito.when(mockContainerParameters.getAppRoot()).thenReturn("\\windows\\path");

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
    Mockito.when(mockContainerParameters.getAppRoot()).thenReturn("C:\\windows\\path");

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
