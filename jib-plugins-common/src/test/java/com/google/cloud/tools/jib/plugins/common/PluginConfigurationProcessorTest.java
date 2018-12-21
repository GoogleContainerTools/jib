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
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
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
        jibContainerBuilder, Containerizer.to(RegistryImage.named("ignored")));
  }

  @Mock private RawConfiguration rawConfiguration;
  @Mock private ProjectProperties projectProperties;
  @Mock private Containerizer containerizer;
  @Mock private ImageReference targetImageReference;
  @Mock private AuthProperty authProperty;
  @Mock private Consumer<LogEvent> logger;

  @Before
  public void setUp() {
    Mockito.when(rawConfiguration.getFromAuth()).thenReturn(authProperty);
    Mockito.when(rawConfiguration.getEntrypoint()).thenReturn(Optional.empty());
    Mockito.when(rawConfiguration.getAppRoot()).thenReturn("/app");

    Mockito.when(projectProperties.getToolName()).thenReturn("tool");
    Mockito.when(projectProperties.getJavaLayerConfigurations())
        .thenReturn(JavaLayerConfigurations.builder().build());
    Mockito.when(projectProperties.getMainClassFromJar()).thenReturn("java.lang.Object");
    Mockito.when(projectProperties.getEventHandlers())
        .thenReturn(new EventHandlers().add(JibEventType.LOGGING, logger));
    Mockito.when(projectProperties.getDefaultCacheDirectory()).thenReturn(Paths.get("cache"));

    Mockito.when(containerizer.setToolName(Mockito.anyString())).thenReturn(containerizer);
    Mockito.when(containerizer.setEventHandlers(Mockito.any(EventHandlers.class)))
        .thenReturn(containerizer);
    Mockito.when(containerizer.setAllowInsecureRegistries(Mockito.anyBoolean()))
        .thenReturn(containerizer);
    Mockito.when(containerizer.setBaseImageLayersCache(Mockito.any(Path.class)))
        .thenReturn(containerizer);
    Mockito.when(containerizer.setApplicationLayersCache(Mockito.any(Path.class)))
        .thenReturn(containerizer);
  }

  @Test
  public void testPluginConfigurationProcessor_defaults()
      throws InvalidImageReferenceException, IOException, CacheDirectoryCreationException,
          MainClassInferenceException, InvalidAppRootException, InferredAuthRetrievalException,
          InvalidWorkingDirectoryException, InvalidContainerVolumeException {
    PluginConfigurationProcessor processor = createPluginConfigurationProcessor(false);
    BuildConfiguration buildConfiguration =
        getBuildConfiguration(processor.getJibContainerBuilder());

    Assert.assertNotNull(buildConfiguration.getContainerConfiguration());
    Assert.assertEquals(
        Arrays.asList("java", "-cp", "/app/resources:/app/classes:/app/libs/*", "java.lang.Object"),
        buildConfiguration.getContainerConfiguration().getEntrypoint());

    Mockito.verify(containerizer)
        .setBaseImageLayersCache(Containerizer.DEFAULT_BASE_CACHE_DIRECTORY);
    Mockito.verify(containerizer).setApplicationLayersCache(Paths.get("cache"));

    ArgumentMatcher<LogEvent> isLogWarn = logEvent -> logEvent.getLevel() == LogEvent.Level.WARN;
    Mockito.verify(logger, Mockito.never()).accept(Mockito.argThat(isLogWarn));
  }

  @Test
  public void testPluginConfigurationProcessor_cacheDirectorySystemProperties()
      throws InferredAuthRetrievalException, InvalidContainerVolumeException,
          MainClassInferenceException, InvalidAppRootException, IOException,
          InvalidWorkingDirectoryException, InvalidImageReferenceException {
    System.setProperty(PropertyNames.BASE_IMAGE_CACHE, "new/base/cache");
    System.setProperty(PropertyNames.APPLICATION_CACHE, "/new/application/cache");

    createPluginConfigurationProcessor(false);

    Mockito.verify(containerizer).setBaseImageLayersCache(Paths.get("new/base/cache"));
    Mockito.verify(containerizer).setApplicationLayersCache(Paths.get("/new/application/cache"));

    System.clearProperty(PropertyNames.BASE_IMAGE_CACHE);
    System.clearProperty(PropertyNames.APPLICATION_CACHE);
  }

  @Test
  public void testPluginConfigurationProcessor_warProjectBaseImage()
      throws InvalidImageReferenceException, MainClassInferenceException, InvalidAppRootException,
          InferredAuthRetrievalException, IOException, InvalidWorkingDirectoryException,
          InvalidContainerVolumeException {
    PluginConfigurationProcessor processor = createPluginConfigurationProcessor(true);

    Assert.assertEquals(
        ImageReference.parse("gcr.io/distroless/java/jetty").toString(),
        processor.getBaseImageReference().toString());
    Mockito.verifyNoMoreInteractions(logger);
  }

  @Test
  public void testEntrypoint()
      throws InvalidImageReferenceException, IOException, CacheDirectoryCreationException,
          MainClassInferenceException, InvalidAppRootException, InferredAuthRetrievalException,
          InvalidWorkingDirectoryException, InvalidContainerVolumeException {
    Mockito.when(rawConfiguration.getEntrypoint())
        .thenReturn(Optional.of(Arrays.asList("custom", "entrypoint")));

    PluginConfigurationProcessor processor = createPluginConfigurationProcessor(false);
    BuildConfiguration buildConfiguration =
        getBuildConfiguration(processor.getJibContainerBuilder());

    Assert.assertNotNull(buildConfiguration.getContainerConfiguration());
    Assert.assertEquals(
        Arrays.asList("custom", "entrypoint"),
        buildConfiguration.getContainerConfiguration().getEntrypoint());
    Mockito.verifyZeroInteractions(logger);
  }

  @Test
  public void testComputeEntrypoint_inheritKeyword()
      throws MainClassInferenceException, InvalidAppRootException {
    Mockito.when(rawConfiguration.getEntrypoint())
        .thenReturn(Optional.of(Collections.singletonList("INHERIT")));

    Assert.assertNull(
        PluginConfigurationProcessor.computeEntrypoint(rawConfiguration, false, projectProperties));
  }

  @Test
  public void testComputeEntrypoint_inheritKeywordInNonSingletonList()
      throws MainClassInferenceException, InvalidAppRootException {
    Mockito.when(rawConfiguration.getEntrypoint())
        .thenReturn(Optional.of(Arrays.asList("INHERIT", "")));

    Assert.assertNotNull(
        PluginConfigurationProcessor.computeEntrypoint(rawConfiguration, false, projectProperties));
  }

  @Test
  public void testEntrypoint_defaultWarPackaging()
      throws IOException, InvalidImageReferenceException, CacheDirectoryCreationException,
          MainClassInferenceException, InvalidAppRootException, InferredAuthRetrievalException,
          InvalidWorkingDirectoryException, InvalidContainerVolumeException {
    Mockito.when(rawConfiguration.getEntrypoint()).thenReturn(Optional.empty());

    PluginConfigurationProcessor processor = createPluginConfigurationProcessor(true);
    JibContainerBuilder jibContainerBuilder = processor.getJibContainerBuilder();
    BuildConfiguration buildConfiguration = getBuildConfiguration(jibContainerBuilder);

    Assert.assertNotNull(buildConfiguration.getContainerConfiguration());
    Assert.assertNull(buildConfiguration.getContainerConfiguration().getEntrypoint());
    Mockito.verifyZeroInteractions(logger);
  }

  @Test
  public void testEntrypoint_defaulNonWarPackaging()
      throws IOException, InvalidImageReferenceException, CacheDirectoryCreationException,
          MainClassInferenceException, InvalidAppRootException, InferredAuthRetrievalException,
          InvalidWorkingDirectoryException, InvalidContainerVolumeException {
    Mockito.when(rawConfiguration.getEntrypoint()).thenReturn(Optional.empty());

    PluginConfigurationProcessor processor = createPluginConfigurationProcessor(false);
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
          MainClassInferenceException, InvalidAppRootException, InferredAuthRetrievalException,
          InvalidWorkingDirectoryException, InvalidContainerVolumeException {
    Mockito.when(rawConfiguration.getUser()).thenReturn(Optional.of("customUser"));

    PluginConfigurationProcessor processor = createPluginConfigurationProcessor(false);
    BuildConfiguration buildConfiguration =
        getBuildConfiguration(processor.getJibContainerBuilder());

    Assert.assertNotNull(buildConfiguration.getContainerConfiguration());
    Assert.assertEquals("customUser", buildConfiguration.getContainerConfiguration().getUser());
  }

  @Test
  public void testUser_null()
      throws InvalidImageReferenceException, IOException, CacheDirectoryCreationException,
          MainClassInferenceException, InvalidAppRootException, InferredAuthRetrievalException,
          InvalidWorkingDirectoryException, InvalidContainerVolumeException {
    PluginConfigurationProcessor processor = createPluginConfigurationProcessor(false);
    BuildConfiguration buildConfiguration =
        getBuildConfiguration(processor.getJibContainerBuilder());

    Assert.assertNotNull(buildConfiguration.getContainerConfiguration());
    Assert.assertNull(buildConfiguration.getContainerConfiguration().getUser());
  }

  @Test
  public void testEntrypoint_warningOnJvmFlags()
      throws InvalidImageReferenceException, IOException, CacheDirectoryCreationException,
          MainClassInferenceException, InvalidAppRootException, InferredAuthRetrievalException,
          InvalidWorkingDirectoryException, InvalidContainerVolumeException {
    Mockito.when(rawConfiguration.getEntrypoint())
        .thenReturn(Optional.of(Arrays.asList("custom", "entrypoint")));
    Mockito.when(rawConfiguration.getJvmFlags()).thenReturn(Collections.singletonList("jvmFlag"));

    PluginConfigurationProcessor processor = createPluginConfigurationProcessor(false);
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
          MainClassInferenceException, InvalidAppRootException, InferredAuthRetrievalException,
          InvalidWorkingDirectoryException, InvalidContainerVolumeException {
    Mockito.when(rawConfiguration.getEntrypoint())
        .thenReturn(Optional.of(Arrays.asList("custom", "entrypoint")));
    Mockito.when(rawConfiguration.getMainClass()).thenReturn(Optional.of("java.util.Object"));

    PluginConfigurationProcessor processor = createPluginConfigurationProcessor(false);
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
          MainClassInferenceException, InvalidAppRootException, InferredAuthRetrievalException,
          InvalidWorkingDirectoryException, InvalidContainerVolumeException {
    Mockito.when(rawConfiguration.getAppRoot()).thenReturn("/my/app");

    PluginConfigurationProcessor processor = createPluginConfigurationProcessor(false);
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
          MainClassInferenceException, InvalidAppRootException, InferredAuthRetrievalException,
          InvalidWorkingDirectoryException, InvalidContainerVolumeException {
    PluginConfigurationProcessor processor = createPluginConfigurationProcessor(true);
    BuildConfiguration buildConfiguration =
        getBuildConfiguration(processor.getJibContainerBuilder());

    Assert.assertNotNull(buildConfiguration.getContainerConfiguration());
    Assert.assertNull(buildConfiguration.getContainerConfiguration().getEntrypoint());
  }

  @Test
  public void testGetAppRootChecked() throws InvalidAppRootException {
    Mockito.when(rawConfiguration.getAppRoot()).thenReturn("/some/root");

    Assert.assertEquals(
        AbsoluteUnixPath.get("/some/root"),
        PluginConfigurationProcessor.getAppRootChecked(rawConfiguration, false));
  }

  @Test
  public void testGetAppRootChecked_errorOnNonAbsolutePath() {
    Mockito.when(rawConfiguration.getAppRoot()).thenReturn("relative/path");

    try {
      PluginConfigurationProcessor.getAppRootChecked(rawConfiguration, false);
      Assert.fail();
    } catch (InvalidAppRootException ex) {
      Assert.assertEquals("relative/path", ex.getMessage());
    }
  }

  @Test
  public void testGetAppRootChecked_errorOnWindowsPath() {
    Mockito.when(rawConfiguration.getAppRoot()).thenReturn("\\windows\\path");

    try {
      PluginConfigurationProcessor.getAppRootChecked(rawConfiguration, false);
      Assert.fail();
    } catch (InvalidAppRootException ex) {
      Assert.assertEquals("\\windows\\path", ex.getMessage());
    }
  }

  @Test
  public void testGetAppRootChecked_errorOnWindowsPathWithDriveLetter() {
    Mockito.when(rawConfiguration.getAppRoot()).thenReturn("C:\\windows\\path");

    try {
      PluginConfigurationProcessor.getAppRootChecked(rawConfiguration, false);
      Assert.fail();
    } catch (InvalidAppRootException ex) {
      Assert.assertEquals("C:\\windows\\path", ex.getMessage());
    }
  }

  @Test
  public void testGetAppRootChecked_defaultNonWarProject() throws InvalidAppRootException {
    Mockito.when(rawConfiguration.getAppRoot()).thenReturn("");

    Assert.assertEquals(
        AbsoluteUnixPath.get("/app"),
        PluginConfigurationProcessor.getAppRootChecked(rawConfiguration, false));
  }

  @Test
  public void testGetAppRootChecked_defaultWarProject() throws InvalidAppRootException {
    Mockito.when(rawConfiguration.getAppRoot()).thenReturn("");

    Assert.assertEquals(
        AbsoluteUnixPath.get("/jetty/webapps/ROOT"),
        PluginConfigurationProcessor.getAppRootChecked(rawConfiguration, true));
  }

  @Test
  public void testGetWorkingDirectoryChecked() throws InvalidWorkingDirectoryException {
    Mockito.when(rawConfiguration.getWorkingDirectory()).thenReturn(Optional.of("/valid/path"));

    Optional<AbsoluteUnixPath> checkedPath =
        PluginConfigurationProcessor.getWorkingDirectoryChecked(rawConfiguration);
    Assert.assertTrue(checkedPath.isPresent());
    Assert.assertEquals(AbsoluteUnixPath.get("/valid/path"), checkedPath.get());
  }

  @Test
  public void testGetWorkingDirectoryChecked_undefined() throws InvalidWorkingDirectoryException {
    Mockito.when(rawConfiguration.getWorkingDirectory()).thenReturn(Optional.empty());
    Assert.assertEquals(
        Optional.empty(),
        PluginConfigurationProcessor.getWorkingDirectoryChecked(rawConfiguration));
  }

  @Test
  public void testGetWorkingDirectoryChecked_notAbsolute() {
    Mockito.when(rawConfiguration.getWorkingDirectory()).thenReturn(Optional.of("relative/path"));

    try {
      PluginConfigurationProcessor.getWorkingDirectoryChecked(rawConfiguration);
      Assert.fail();
    } catch (InvalidWorkingDirectoryException ex) {
      Assert.assertEquals("relative/path", ex.getMessage());
      Assert.assertEquals("relative/path", ex.getInvalidPathValue());
    }
  }

  @Test
  public void testGetBaseImage_defaultNonWarPackaging() {
    Assert.assertEquals(
        "gcr.io/distroless/java",
        PluginConfigurationProcessor.getBaseImage(rawConfiguration, false));
  }

  @Test
  public void testGetBaseImage_defaultWarProject() {
    Assert.assertEquals(
        "gcr.io/distroless/java/jetty",
        PluginConfigurationProcessor.getBaseImage(rawConfiguration, true));
  }

  @Test
  public void testGetBaseImage_nonDefault() {
    Mockito.when(rawConfiguration.getFromImage()).thenReturn(Optional.of("tomcat"));

    Assert.assertEquals(
        "tomcat", PluginConfigurationProcessor.getBaseImage(rawConfiguration, false));
    Assert.assertEquals(
        "tomcat", PluginConfigurationProcessor.getBaseImage(rawConfiguration, true));
  }

  @Test
  public void testGetValidVolumesList() throws InvalidContainerVolumeException {
    Mockito.when(rawConfiguration.getVolumes()).thenReturn(Collections.singletonList("/some/root"));

    Assert.assertEquals(
        ImmutableSet.of(AbsoluteUnixPath.get("/some/root")),
        PluginConfigurationProcessor.getVolumesSet(rawConfiguration));
  }

  @Test
  public void testGetInvalidVolumesList() {
    Mockito.when(rawConfiguration.getVolumes()).thenReturn(Collections.singletonList("`some/root"));

    try {
      PluginConfigurationProcessor.getVolumesSet(rawConfiguration);
      Assert.fail();
    } catch (InvalidContainerVolumeException ex) {
      Assert.assertEquals("`some/root", ex.getMessage());
      Assert.assertEquals("`some/root", ex.getInvalidVolume());
    }
  }

  private PluginConfigurationProcessor createPluginConfigurationProcessor(
      boolean doWarContainerization)
      throws InvalidImageReferenceException, MainClassInferenceException, InvalidAppRootException,
          InferredAuthRetrievalException, IOException, InvalidWorkingDirectoryException,
          InvalidContainerVolumeException {
    return PluginConfigurationProcessor.processCommonConfiguration(
        rawConfiguration,
        doWarContainerization,
        ignored -> Optional.empty(),
        projectProperties,
        containerizer,
        targetImageReference,
        false);
  }
}
