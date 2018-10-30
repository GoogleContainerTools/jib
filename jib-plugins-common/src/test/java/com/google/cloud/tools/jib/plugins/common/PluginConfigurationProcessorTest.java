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

package com.google.cloud.tools.jib.plugins.common;

import com.google.cloud.tools.jib.api.Containerizer;
import com.google.cloud.tools.jib.api.JibContainerBuilder;
import com.google.cloud.tools.jib.api.JibContainerBuilderTestHelper;
import com.google.cloud.tools.jib.api.RegistryImage;
import com.google.cloud.tools.jib.configuration.BuildConfiguration;
import com.google.cloud.tools.jib.configuration.CacheDirectoryCreationException;
import com.google.cloud.tools.jib.event.EventHandlers;
import com.google.cloud.tools.jib.event.JibEventType;
import com.google.cloud.tools.jib.event.events.LogEvent;
import com.google.cloud.tools.jib.filesystem.AbsoluteUnixPath;
import com.google.cloud.tools.jib.frontend.JavaLayerConfigurations;
import com.google.cloud.tools.jib.image.ImageReference;
import com.google.cloud.tools.jib.image.InvalidImageReferenceException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;
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

  @Mock private RawConfiguration rawConfiguration;
  @Mock private ProjectProperties projectProperties;
  @Mock private AuthProperty authProperty;
  @Mock private Consumer<LogEvent> logger;

  @Before
  public void setUp() {
    Mockito.when(rawConfiguration.getFromAuth()).thenReturn(authProperty);
    Mockito.when(rawConfiguration.getEntrypoint()).thenReturn(Optional.empty());
    Mockito.when(rawConfiguration.getAppRoot()).thenReturn("/app");

    Mockito.when(projectProperties.getJavaLayerConfigurations())
        .thenReturn(JavaLayerConfigurations.builder().build());
    Mockito.when(projectProperties.getMainClassFromJar()).thenReturn("java.lang.Object");
    Mockito.when(projectProperties.getEventHandlers())
        .thenReturn(new EventHandlers().add(JibEventType.LOGGING, logger));
  }

  /** Test with our default mocks, which try to mimic the bare Gradle configuration. */
  @Test
  public void testPluginConfigurationProcessor_defaults()
      throws InvalidImageReferenceException, IOException, CacheDirectoryCreationException,
          MainClassInferenceException, AppRootInvalidException, InferredAuthRetrievalException {
    PluginConfigurationProcessor processor =
        PluginConfigurationProcessor.processCommonConfiguration(
            rawConfiguration, projectProperties);
    BuildConfiguration buildConfiguration =
        getBuildConfiguration(processor.getJibContainerBuilder());
    Assert.assertNotNull(buildConfiguration.getContainerConfiguration());
    Assert.assertEquals(
        Arrays.asList("java", "-cp", "/app/resources:/app/classes:/app/libs/*", "java.lang.Object"),
        buildConfiguration.getContainerConfiguration().getEntrypoint());

    ArgumentMatcher<LogEvent> isLogWarn = logEvent -> logEvent.getLevel() == LogEvent.Level.WARN;
    Mockito.verify(logger, Mockito.never()).accept(Mockito.argThat(isLogWarn));
  }

  @Test
  public void testPluginConfigurationProcessor_warProjectBaseImage()
      throws InvalidImageReferenceException, FileNotFoundException, MainClassInferenceException,
          AppRootInvalidException, InferredAuthRetrievalException {
    Mockito.when(projectProperties.isWarProject()).thenReturn(true);

    PluginConfigurationProcessor processor =
        PluginConfigurationProcessor.processCommonConfiguration(
            rawConfiguration, projectProperties);

    Assert.assertEquals(
        ImageReference.parse("gcr.io/distroless/java/jetty").toString(),
        processor.getBaseImageReference().toString());
    Mockito.verifyNoMoreInteractions(logger);
  }

  @Test
  public void testEntrypoint()
      throws InvalidImageReferenceException, IOException, CacheDirectoryCreationException,
          MainClassInferenceException, AppRootInvalidException, InferredAuthRetrievalException {
    Mockito.when(rawConfiguration.getEntrypoint())
        .thenReturn(Optional.of(Arrays.asList("custom", "entrypoint")));

    PluginConfigurationProcessor processor =
        PluginConfigurationProcessor.processCommonConfiguration(
            rawConfiguration, projectProperties);
    BuildConfiguration buildConfiguration =
        getBuildConfiguration(processor.getJibContainerBuilder());

    Assert.assertNotNull(buildConfiguration.getContainerConfiguration());
    Assert.assertEquals(
        Arrays.asList("custom", "entrypoint"),
        buildConfiguration.getContainerConfiguration().getEntrypoint());
    Mockito.verifyZeroInteractions(logger);
  }

  @Test
  public void testEntrypoint_defaultWarPackaging()
      throws IOException, InvalidImageReferenceException, CacheDirectoryCreationException,
          MainClassInferenceException, AppRootInvalidException, InferredAuthRetrievalException {
    Mockito.when(rawConfiguration.getEntrypoint()).thenReturn(Optional.empty());
    Mockito.when(projectProperties.isWarProject()).thenReturn(true);

    PluginConfigurationProcessor processor =
        PluginConfigurationProcessor.processCommonConfiguration(
            rawConfiguration, projectProperties);

    JibContainerBuilder jibContainerBuilder = processor.getJibContainerBuilder();
    BuildConfiguration buildConfiguration = getBuildConfiguration(jibContainerBuilder);
    Assert.assertNull(buildConfiguration.getContainerConfiguration().getEntrypoint());
    Assert.assertNotNull(buildConfiguration.getContainerConfiguration());
    Assert.assertNull(buildConfiguration.getContainerConfiguration().getEntrypoint());
    Mockito.verifyZeroInteractions(logger);
  }

  @Test
  public void testEntrypoint_defaulNonWarPackaging()
      throws IOException, InvalidImageReferenceException, CacheDirectoryCreationException,
          MainClassInferenceException, AppRootInvalidException, InferredAuthRetrievalException {
    Mockito.when(rawConfiguration.getEntrypoint()).thenReturn(Optional.empty());
    Mockito.when(projectProperties.isWarProject()).thenReturn(false);

    PluginConfigurationProcessor processor =
        PluginConfigurationProcessor.processCommonConfiguration(
            rawConfiguration, projectProperties);
    JibContainerBuilder jibContainerBuilder = processor.getJibContainerBuilder();
    BuildConfiguration buildConfiguration = getBuildConfiguration(jibContainerBuilder);
    Assert.assertNotNull(buildConfiguration.getContainerConfiguration());
    Assert.assertEquals(
        Arrays.asList("java", "-cp", "/app/resources:/app/classes:/app/libs/*", "java.lang.Object"),
        buildConfiguration.getContainerConfiguration().getEntrypoint());

    ArgumentMatcher<LogEvent> isLogWarn = logEvent -> logEvent.getLevel() == LogEvent.Level.WARN;
    Mockito.verify(logger, Mockito.never()).accept(Mockito.argThat(isLogWarn));
  }

  @Test
  public void testUser()
      throws InvalidImageReferenceException, IOException, CacheDirectoryCreationException,
          MainClassInferenceException, AppRootInvalidException, InferredAuthRetrievalException {
    Mockito.when(rawConfiguration.getUser()).thenReturn(Optional.of("customUser"));

    PluginConfigurationProcessor processor =
        PluginConfigurationProcessor.processCommonConfiguration(
            rawConfiguration, projectProperties);
    BuildConfiguration buildConfiguration =
        getBuildConfiguration(processor.getJibContainerBuilder());

    Assert.assertNotNull(buildConfiguration.getContainerConfiguration());
    Assert.assertEquals("customUser", buildConfiguration.getContainerConfiguration().getUser());
  }

  @Test
  public void testUser_null()
      throws InvalidImageReferenceException, IOException, CacheDirectoryCreationException,
          MainClassInferenceException, AppRootInvalidException, InferredAuthRetrievalException {
    PluginConfigurationProcessor processor =
        PluginConfigurationProcessor.processCommonConfiguration(
            rawConfiguration, projectProperties);
    BuildConfiguration buildConfiguration =
        getBuildConfiguration(processor.getJibContainerBuilder());

    Assert.assertNotNull(buildConfiguration.getContainerConfiguration());
    Assert.assertNull(buildConfiguration.getContainerConfiguration().getUser());
  }

  @Test
  public void testEntrypoint_warningOnJvmFlags()
      throws InvalidImageReferenceException, IOException, CacheDirectoryCreationException,
          MainClassInferenceException, AppRootInvalidException, InferredAuthRetrievalException {
    Mockito.when(rawConfiguration.getEntrypoint())
        .thenReturn(Optional.of(Arrays.asList("custom", "entrypoint")));
    Mockito.when(rawConfiguration.getJvmFlags()).thenReturn(Collections.singletonList("jvmFlag"));

    PluginConfigurationProcessor processor =
        PluginConfigurationProcessor.processCommonConfiguration(
            rawConfiguration, projectProperties);
    BuildConfiguration buildConfiguration =
        getBuildConfiguration(processor.getJibContainerBuilder());

    Assert.assertNotNull(buildConfiguration.getContainerConfiguration());
    Assert.assertEquals(
        Arrays.asList("custom", "entrypoint"),
        buildConfiguration.getContainerConfiguration().getEntrypoint());
    Mockito.verify(logger)
        .accept(LogEvent.warn("mainClass and jvmFlags are ignored when entrypoint is specified"));
  }

  @Test
  public void testEntrypoint_warningOnMainclass()
      throws InvalidImageReferenceException, IOException, CacheDirectoryCreationException,
          MainClassInferenceException, AppRootInvalidException, InferredAuthRetrievalException {
    Mockito.when(rawConfiguration.getEntrypoint())
        .thenReturn(Optional.of(Arrays.asList("custom", "entrypoint")));
    Mockito.when(rawConfiguration.getMainClass()).thenReturn(Optional.of("java.util.Object"));

    PluginConfigurationProcessor processor =
        PluginConfigurationProcessor.processCommonConfiguration(
            rawConfiguration, projectProperties);
    BuildConfiguration buildConfiguration =
        getBuildConfiguration(processor.getJibContainerBuilder());

    Assert.assertNotNull(buildConfiguration.getContainerConfiguration());
    Assert.assertEquals(
        Arrays.asList("custom", "entrypoint"),
        buildConfiguration.getContainerConfiguration().getEntrypoint());
    Mockito.verify(logger)
        .accept(LogEvent.warn("mainClass and jvmFlags are ignored when entrypoint is specified"));
  }

  @Test
  public void testEntrypointClasspath_nonDefaultAppRoot()
      throws InvalidImageReferenceException, IOException, CacheDirectoryCreationException,
          MainClassInferenceException, AppRootInvalidException, InferredAuthRetrievalException {
    Mockito.when(rawConfiguration.getAppRoot()).thenReturn("/my/app");

    PluginConfigurationProcessor processor =
        PluginConfigurationProcessor.processCommonConfiguration(
            rawConfiguration, projectProperties);
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
  public void testWebAppEntrypoint_inheritedFromBaseImage()
      throws InvalidImageReferenceException, IOException, CacheDirectoryCreationException,
          MainClassInferenceException, AppRootInvalidException, InferredAuthRetrievalException {
    Mockito.when(projectProperties.isWarProject()).thenReturn(true);

    PluginConfigurationProcessor processor =
        PluginConfigurationProcessor.processCommonConfiguration(
            rawConfiguration, projectProperties);
    BuildConfiguration buildConfiguration =
        getBuildConfiguration(processor.getJibContainerBuilder());

    Assert.assertNotNull(buildConfiguration.getContainerConfiguration());
    Assert.assertNull(buildConfiguration.getContainerConfiguration().getEntrypoint());
  }

  @Test
  public void testGetAppRootChecked() throws AppRootInvalidException {
    Mockito.when(rawConfiguration.getAppRoot()).thenReturn("/some/root");

    Assert.assertEquals(
        AbsoluteUnixPath.get("/some/root"),
        PluginConfigurationProcessor.getAppRootChecked(rawConfiguration, projectProperties));
  }

  @Test
  public void testGetAppRootChecked_errorOnNonAbsolutePath() {
    Mockito.when(rawConfiguration.getAppRoot()).thenReturn("relative/path");

    try {
      PluginConfigurationProcessor.getAppRootChecked(rawConfiguration, projectProperties);
      Assert.fail();
    } catch (AppRootInvalidException ex) {
      Assert.assertEquals("relative/path", ex.getMessage());
    }
  }

  @Test
  public void testGetAppRootChecked_errorOnWindowsPath() {
    Mockito.when(rawConfiguration.getAppRoot()).thenReturn("\\windows\\path");

    try {
      PluginConfigurationProcessor.getAppRootChecked(rawConfiguration, projectProperties);
      Assert.fail();
    } catch (AppRootInvalidException ex) {
      Assert.assertEquals("\\windows\\path", ex.getMessage());
    }
  }

  @Test
  public void testGetAppRootChecked_errorOnWindowsPathWithDriveLetter() {
    Mockito.when(rawConfiguration.getAppRoot()).thenReturn("C:\\windows\\path");

    try {
      PluginConfigurationProcessor.getAppRootChecked(rawConfiguration, projectProperties);
      Assert.fail();
    } catch (AppRootInvalidException ex) {
      Assert.assertEquals("C:\\windows\\path", ex.getMessage());
    }
  }

  @Test
  public void testGetAppRootChecked_defaultNonWarProject() throws AppRootInvalidException {
    Mockito.when(rawConfiguration.getAppRoot()).thenReturn("");
    Mockito.when(projectProperties.isWarProject()).thenReturn(false);

    Assert.assertEquals(
        AbsoluteUnixPath.get("/app"),
        PluginConfigurationProcessor.getAppRootChecked(rawConfiguration, projectProperties));
  }

  @Test
  public void testGetAppRootChecked_defaultWarProject() throws AppRootInvalidException {
    Mockito.when(rawConfiguration.getAppRoot()).thenReturn("");
    Mockito.when(projectProperties.isWarProject()).thenReturn(true);

    Assert.assertEquals(
        AbsoluteUnixPath.get("/jetty/webapps/ROOT"),
        PluginConfigurationProcessor.getAppRootChecked(rawConfiguration, projectProperties));
  }

  @Test
  public void testGetBaseImage_defaultNonWarPackaging() {
    Mockito.when(projectProperties.isWarProject()).thenReturn(false);

    Assert.assertEquals(
        "gcr.io/distroless/java",
        PluginConfigurationProcessor.getBaseImage(rawConfiguration, projectProperties));
  }

  @Test
  public void testGetBaseImage_defaultWarProject() {
    Mockito.when(projectProperties.isWarProject()).thenReturn(true);

    Assert.assertEquals(
        "gcr.io/distroless/java/jetty",
        PluginConfigurationProcessor.getBaseImage(rawConfiguration, projectProperties));
  }

  @Test
  public void testGetBaseImage_nonDefault() {
    Mockito.when(rawConfiguration.getFromImage()).thenReturn(Optional.of("tomcat"));

    Assert.assertEquals(
        "tomcat", PluginConfigurationProcessor.getBaseImage(rawConfiguration, projectProperties));
  }
}
