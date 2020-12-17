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

import com.google.cloud.tools.jib.api.CacheDirectoryCreationException;
import com.google.cloud.tools.jib.api.Containerizer;
import com.google.cloud.tools.jib.api.InvalidImageReferenceException;
import com.google.cloud.tools.jib.api.JavaContainerBuilder;
import com.google.cloud.tools.jib.api.Jib;
import com.google.cloud.tools.jib.api.JibContainerBuilder;
import com.google.cloud.tools.jib.api.JibContainerBuilderTestHelper;
import com.google.cloud.tools.jib.api.LogEvent;
import com.google.cloud.tools.jib.api.RegistryImage;
import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.buildplan.FileEntry;
import com.google.cloud.tools.jib.api.buildplan.FilePermissions;
import com.google.cloud.tools.jib.api.buildplan.ModificationTimeProvider;
import com.google.cloud.tools.jib.api.buildplan.Platform;
import com.google.cloud.tools.jib.configuration.BuildContext;
import com.google.cloud.tools.jib.configuration.ImageConfiguration;
import com.google.cloud.tools.jib.plugins.common.RawConfiguration.PlatformConfiguration;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Resources;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.RestoreSystemProperties;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests for {@link PluginConfigurationProcessor}. */
@RunWith(MockitoJUnitRunner.class)
public class PluginConfigurationProcessorTest {

  private static BuildContext getBuildContext(JibContainerBuilder jibContainerBuilder)
      throws InvalidImageReferenceException, CacheDirectoryCreationException {
    return JibContainerBuilderTestHelper.toBuildContext(
        jibContainerBuilder, Containerizer.to(RegistryImage.named("ignored")));
  }

  private static <T> void assertLayerEntriesUnordered(
      List<T> expectedPaths, List<FileEntry> entries, Function<FileEntry, T> fieldSelector) {
    List<T> expected = expectedPaths.stream().sorted().collect(Collectors.toList());
    List<T> actual = entries.stream().map(fieldSelector).sorted().collect(Collectors.toList());
    Assert.assertEquals(expected, actual);
  }

  private static void assertSourcePathsUnordered(
      List<Path> expectedPaths, List<FileEntry> entries) {
    assertLayerEntriesUnordered(expectedPaths, entries, FileEntry::getSourceFile);
  }

  private static void assertExtractionPathsUnordered(
      List<String> expectedPaths, List<FileEntry> entries) {
    assertLayerEntriesUnordered(
        expectedPaths, entries, layerEntry -> layerEntry.getExtractionPath().toString());
  }

  @Rule public final RestoreSystemProperties systemPropertyRestorer = new RestoreSystemProperties();
  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Mock(answer = Answers.RETURNS_SELF)
  private Containerizer containerizer;

  @Mock private RawConfiguration rawConfiguration;
  @Mock private ProjectProperties projectProperties;
  @Mock private InferredAuthProvider inferredAuthProvider;
  @Mock private AuthProperty authProperty;
  @Mock private Consumer<LogEvent> logger;

  private static class TestPlatformConfiguration implements PlatformConfiguration {
    @Nullable private final String os;
    @Nullable private final String architecture;

    private TestPlatformConfiguration(@Nullable String architecture, @Nullable String os) {
      this.architecture = architecture;
      this.os = os;
    }

    @Override
    public Optional<String> getOsName() {
      return Optional.ofNullable(os);
    }

    @Override
    public Optional<String> getArchitectureName() {
      return Optional.ofNullable(architecture);
    }
  }

  @Before
  public void setUp() throws IOException, InvalidImageReferenceException, InferredAuthException {
    Mockito.when(rawConfiguration.getFromAuth()).thenReturn(authProperty);
    Mockito.when(rawConfiguration.getEntrypoint()).thenReturn(Optional.empty());
    Mockito.when(rawConfiguration.getAppRoot()).thenReturn("/app");
    Mockito.<List<?>>when(rawConfiguration.getPlatforms())
        .thenReturn(Arrays.asList(new TestPlatformConfiguration("amd64", "linux")));
    Mockito.when(rawConfiguration.getFilesModificationTime()).thenReturn("EPOCH_PLUS_SECOND");
    Mockito.when(rawConfiguration.getCreationTime()).thenReturn("EPOCH");
    Mockito.when(rawConfiguration.getContainerizingMode()).thenReturn("exploded");
    Mockito.when(projectProperties.getToolName()).thenReturn("tool");
    Mockito.when(projectProperties.getToolVersion()).thenReturn("tool-version");
    Mockito.when(projectProperties.getMainClassFromJarPlugin()).thenReturn("java.lang.Object");
    Mockito.when(projectProperties.getDefaultCacheDirectory()).thenReturn(Paths.get("cache"));
    Mockito.when(
            projectProperties.createJibContainerBuilder(
                Mockito.any(JavaContainerBuilder.class), Mockito.any(ContainerizingMode.class)))
        .thenReturn(Jib.from("base"));
    Mockito.when(projectProperties.isOffline()).thenReturn(false);
    Mockito.when(projectProperties.getDependencies())
        .thenReturn(Arrays.asList(Paths.get("/repo/foo-1.jar"), Paths.get("/home/libs/bar-2.jar")));

    Mockito.when(inferredAuthProvider.inferAuth(Mockito.any())).thenReturn(Optional.empty());
  }

  @Test
  public void testPluginConfigurationProcessor_defaults()
      throws InvalidImageReferenceException, IOException, CacheDirectoryCreationException,
          MainClassInferenceException, InvalidAppRootException, InvalidWorkingDirectoryException,
          InvalidPlatformException, InvalidContainerVolumeException,
          IncompatibleBaseImageJavaVersionException, NumberFormatException,
          InvalidContainerizingModeException, InvalidFilesModificationTimeException,
          InvalidCreationTimeException {
    BuildContext buildContext = getBuildContext(processCommonConfiguration());

    Assert.assertEquals(
        Arrays.asList("java", "-cp", "/app/resources:/app/classes:/app/libs/*", "java.lang.Object"),
        buildContext.getContainerConfiguration().getEntrypoint());

    Mockito.verify(containerizer)
        .setBaseImageLayersCache(Containerizer.DEFAULT_BASE_CACHE_DIRECTORY);
    Mockito.verify(containerizer).setApplicationLayersCache(Paths.get("cache"));

    ArgumentMatcher<LogEvent> isLogWarn = logEvent -> logEvent.getLevel() == LogEvent.Level.WARN;
    Mockito.verify(logger, Mockito.never()).accept(Mockito.argThat(isLogWarn));
  }

  @Test
  public void testPluginConfigurationProcessor_extraDirectory()
      throws URISyntaxException, InvalidContainerVolumeException, MainClassInferenceException,
          InvalidAppRootException, IOException, IncompatibleBaseImageJavaVersionException,
          InvalidWorkingDirectoryException, InvalidPlatformException,
          InvalidImageReferenceException, CacheDirectoryCreationException, NumberFormatException,
          InvalidContainerizingModeException, InvalidFilesModificationTimeException,
          InvalidCreationTimeException {
    Path extraDirectory = Paths.get(Resources.getResource("core/layer").toURI());
    Mockito.when(rawConfiguration.getExtraDirectories())
        .thenReturn(ImmutableMap.of(extraDirectory, AbsoluteUnixPath.get("/target/dir")));
    Mockito.when(rawConfiguration.getExtraDirectoryPermissions())
        .thenReturn(ImmutableMap.of("/target/dir/foo", FilePermissions.fromOctalString("123")));

    BuildContext buildContext = getBuildContext(processCommonConfiguration());
    List<FileEntry> extraFiles =
        buildContext
            .getLayerConfigurations()
            .stream()
            .filter(layer -> layer.getName().equals("extra files"))
            .collect(Collectors.toList())
            .get(0)
            .getEntries();

    assertSourcePathsUnordered(
        Arrays.asList(
            extraDirectory.resolve("a"),
            extraDirectory.resolve("a/b"),
            extraDirectory.resolve("a/b/bar"),
            extraDirectory.resolve("c"),
            extraDirectory.resolve("c/cat"),
            extraDirectory.resolve("foo")),
        extraFiles);
    assertExtractionPathsUnordered(
        Arrays.asList(
            "/target/dir/a",
            "/target/dir/a/b",
            "/target/dir/a/b/bar",
            "/target/dir/c",
            "/target/dir/c/cat",
            "/target/dir/foo"),
        extraFiles);

    Optional<FileEntry> fooEntry =
        extraFiles
            .stream()
            .filter(
                layerEntry ->
                    layerEntry.getExtractionPath().equals(AbsoluteUnixPath.get("/target/dir/foo")))
            .findFirst();
    Assert.assertTrue(fooEntry.isPresent());
    Assert.assertEquals("123", fooEntry.get().getPermissions().toOctalString());
  }

  @Test
  public void testPluginConfigurationProcessor_cacheDirectorySystemProperties()
      throws InvalidContainerVolumeException, MainClassInferenceException, InvalidAppRootException,
          IOException, InvalidWorkingDirectoryException, InvalidPlatformException,
          InvalidImageReferenceException, IncompatibleBaseImageJavaVersionException,
          NumberFormatException, InvalidContainerizingModeException,
          InvalidFilesModificationTimeException, InvalidCreationTimeException {
    System.setProperty(PropertyNames.BASE_IMAGE_CACHE, "new/base/cache");
    System.setProperty(PropertyNames.APPLICATION_CACHE, "/new/application/cache");

    processCommonConfiguration();

    Mockito.verify(containerizer).setBaseImageLayersCache(Paths.get("new/base/cache"));
    Mockito.verify(containerizer).setApplicationLayersCache(Paths.get("/new/application/cache"));
  }

  @Test
  public void testEntrypoint()
      throws InvalidImageReferenceException, IOException, CacheDirectoryCreationException,
          MainClassInferenceException, InvalidAppRootException, InvalidWorkingDirectoryException,
          InvalidPlatformException, InvalidContainerVolumeException,
          IncompatibleBaseImageJavaVersionException, NumberFormatException,
          InvalidContainerizingModeException, InvalidFilesModificationTimeException,
          InvalidCreationTimeException {
    Mockito.when(rawConfiguration.getEntrypoint())
        .thenReturn(Optional.of(Arrays.asList("custom", "entrypoint")));

    BuildContext buildContext = getBuildContext(processCommonConfiguration());

    Assert.assertEquals(
        Arrays.asList("custom", "entrypoint"),
        buildContext.getContainerConfiguration().getEntrypoint());
    Mockito.verifyNoInteractions(logger);
  }

  @Test
  public void testComputeEntrypoint_inheritKeyword()
      throws MainClassInferenceException, InvalidAppRootException, IOException,
          InvalidContainerizingModeException {
    Mockito.when(rawConfiguration.getEntrypoint())
        .thenReturn(Optional.of(Collections.singletonList("INHERIT")));

    Assert.assertNull(
        PluginConfigurationProcessor.computeEntrypoint(rawConfiguration, projectProperties));
  }

  @Test
  public void testComputeEntrypoint_inheritKeywordInNonSingletonList()
      throws MainClassInferenceException, InvalidAppRootException, IOException,
          InvalidContainerizingModeException {
    Mockito.when(rawConfiguration.getEntrypoint())
        .thenReturn(Optional.of(Arrays.asList("INHERIT", "")));

    Assert.assertNotNull(
        PluginConfigurationProcessor.computeEntrypoint(rawConfiguration, projectProperties));
  }

  @Test
  public void testComputeEntrypoint_default()
      throws MainClassInferenceException, InvalidAppRootException, IOException,
          InvalidContainerizingModeException {
    Assert.assertEquals(
        Arrays.asList("java", "-cp", "/app/resources:/app/classes:/app/libs/*", "java.lang.Object"),
        PluginConfigurationProcessor.computeEntrypoint(rawConfiguration, projectProperties));
  }

  @Test
  public void testComputeEntrypoint_packaged()
      throws MainClassInferenceException, InvalidAppRootException, IOException,
          InvalidContainerizingModeException {
    Mockito.when(rawConfiguration.getContainerizingMode()).thenReturn("packaged");
    Assert.assertEquals(
        Arrays.asList("java", "-cp", "/app/classpath/*:/app/libs/*", "java.lang.Object"),
        PluginConfigurationProcessor.computeEntrypoint(rawConfiguration, projectProperties));
  }

  @Test
  public void testComputeEntrypoint_expandClasspathDependencies()
      throws MainClassInferenceException, InvalidAppRootException, IOException,
          InvalidContainerizingModeException {
    Mockito.when(rawConfiguration.getExpandClasspathDependencies()).thenReturn(true);
    Assert.assertEquals(
        Arrays.asList(
            "java",
            "-cp",
            "/app/resources:/app/classes:/app/libs/foo-1.jar:/app/libs/bar-2.jar",
            "java.lang.Object"),
        PluginConfigurationProcessor.computeEntrypoint(rawConfiguration, projectProperties));
  }

  @Test
  public void testEntrypoint_defaultWarPackaging()
      throws IOException, InvalidImageReferenceException, CacheDirectoryCreationException,
          MainClassInferenceException, InvalidAppRootException, InvalidWorkingDirectoryException,
          InvalidPlatformException, InvalidContainerVolumeException,
          IncompatibleBaseImageJavaVersionException, NumberFormatException,
          InvalidContainerizingModeException, InvalidFilesModificationTimeException,
          InvalidCreationTimeException {
    Mockito.when(rawConfiguration.getEntrypoint()).thenReturn(Optional.empty());
    Mockito.when(projectProperties.isWarProject()).thenReturn(true);

    BuildContext buildContext = getBuildContext(processCommonConfiguration());

    Assert.assertNull(buildContext.getContainerConfiguration().getEntrypoint());
    Mockito.verifyNoInteractions(logger);
  }

  @Test
  public void testEntrypoint_defaultNonWarPackaging()
      throws IOException, InvalidImageReferenceException, CacheDirectoryCreationException,
          MainClassInferenceException, InvalidAppRootException, InvalidWorkingDirectoryException,
          InvalidPlatformException, InvalidContainerVolumeException,
          IncompatibleBaseImageJavaVersionException, NumberFormatException,
          InvalidContainerizingModeException, InvalidFilesModificationTimeException,
          InvalidCreationTimeException {
    Mockito.when(rawConfiguration.getEntrypoint()).thenReturn(Optional.empty());
    Mockito.when(projectProperties.isWarProject()).thenReturn(false);

    BuildContext buildContext = getBuildContext(processCommonConfiguration());

    Assert.assertEquals(
        Arrays.asList("java", "-cp", "/app/resources:/app/classes:/app/libs/*", "java.lang.Object"),
        buildContext.getContainerConfiguration().getEntrypoint());

    ArgumentMatcher<LogEvent> isLogWarn = logEvent -> logEvent.getLevel() == LogEvent.Level.WARN;
    Mockito.verify(logger, Mockito.never()).accept(Mockito.argThat(isLogWarn));
  }

  @Test
  public void testEntrypoint_extraClasspathNonWarPackaging()
      throws IOException, InvalidImageReferenceException, CacheDirectoryCreationException,
          MainClassInferenceException, InvalidAppRootException, InvalidWorkingDirectoryException,
          InvalidPlatformException, InvalidContainerVolumeException,
          IncompatibleBaseImageJavaVersionException, NumberFormatException,
          InvalidContainerizingModeException, InvalidFilesModificationTimeException,
          InvalidCreationTimeException {
    Mockito.when(rawConfiguration.getEntrypoint()).thenReturn(Optional.empty());
    Mockito.when(rawConfiguration.getExtraClasspath())
        .thenReturn(Collections.singletonList("/foo"));
    Mockito.when(projectProperties.isWarProject()).thenReturn(false);

    BuildContext buildContext = getBuildContext(processCommonConfiguration());

    Assert.assertEquals(
        Arrays.asList(
            "java", "-cp", "/foo:/app/resources:/app/classes:/app/libs/*", "java.lang.Object"),
        buildContext.getContainerConfiguration().getEntrypoint());

    ArgumentMatcher<LogEvent> isLogWarn = logEvent -> logEvent.getLevel() == LogEvent.Level.WARN;
    Mockito.verify(logger, Mockito.never()).accept(Mockito.argThat(isLogWarn));
  }

  @Test
  public void testUser()
      throws InvalidImageReferenceException, IOException, CacheDirectoryCreationException,
          MainClassInferenceException, InvalidAppRootException, InvalidWorkingDirectoryException,
          InvalidPlatformException, InvalidContainerVolumeException,
          IncompatibleBaseImageJavaVersionException, NumberFormatException,
          InvalidContainerizingModeException, InvalidFilesModificationTimeException,
          InvalidCreationTimeException {
    Mockito.when(rawConfiguration.getUser()).thenReturn(Optional.of("customUser"));

    BuildContext buildContext = getBuildContext(processCommonConfiguration());

    Assert.assertEquals("customUser", buildContext.getContainerConfiguration().getUser());
  }

  @Test
  public void testUser_null()
      throws InvalidImageReferenceException, IOException, CacheDirectoryCreationException,
          MainClassInferenceException, InvalidAppRootException, InvalidWorkingDirectoryException,
          InvalidPlatformException, InvalidContainerVolumeException,
          IncompatibleBaseImageJavaVersionException, NumberFormatException,
          InvalidContainerizingModeException, InvalidFilesModificationTimeException,
          InvalidCreationTimeException {
    BuildContext buildContext = getBuildContext(processCommonConfiguration());

    Assert.assertNull(buildContext.getContainerConfiguration().getUser());
  }

  @Test
  public void testEntrypoint_warningOnJvmFlags()
      throws InvalidImageReferenceException, IOException, CacheDirectoryCreationException,
          MainClassInferenceException, InvalidAppRootException, InvalidWorkingDirectoryException,
          InvalidPlatformException, InvalidContainerVolumeException,
          IncompatibleBaseImageJavaVersionException, NumberFormatException,
          InvalidContainerizingModeException, InvalidFilesModificationTimeException,
          InvalidCreationTimeException {
    Mockito.when(rawConfiguration.getEntrypoint())
        .thenReturn(Optional.of(Arrays.asList("custom", "entrypoint")));
    Mockito.when(rawConfiguration.getJvmFlags()).thenReturn(Collections.singletonList("jvmFlag"));

    BuildContext buildContext = getBuildContext(processCommonConfiguration());

    Assert.assertEquals(
        Arrays.asList("custom", "entrypoint"),
        buildContext.getContainerConfiguration().getEntrypoint());
    Mockito.verify(projectProperties)
        .log(
            LogEvent.warn(
                "mainClass, extraClasspath, jvmFlags, and expandClasspathDependencies are ignored "
                    + "when entrypoint is specified"));
  }

  @Test
  public void testEntrypoint_warningOnMainclass()
      throws InvalidImageReferenceException, IOException, CacheDirectoryCreationException,
          MainClassInferenceException, InvalidAppRootException, InvalidWorkingDirectoryException,
          InvalidPlatformException, InvalidContainerVolumeException,
          IncompatibleBaseImageJavaVersionException, NumberFormatException,
          InvalidContainerizingModeException, InvalidFilesModificationTimeException,
          InvalidCreationTimeException {
    Mockito.when(rawConfiguration.getEntrypoint())
        .thenReturn(Optional.of(Arrays.asList("custom", "entrypoint")));
    Mockito.when(rawConfiguration.getMainClass()).thenReturn(Optional.of("java.util.Object"));

    BuildContext buildContext = getBuildContext(processCommonConfiguration());

    Assert.assertEquals(
        Arrays.asList("custom", "entrypoint"),
        buildContext.getContainerConfiguration().getEntrypoint());
    Mockito.verify(projectProperties)
        .log(
            LogEvent.warn(
                "mainClass, extraClasspath, jvmFlags, and expandClasspathDependencies are ignored "
                    + "when entrypoint is specified"));
  }

  @Test
  public void testEntrypoint_warningExpandClasspathDependencies()
      throws InvalidImageReferenceException, IOException, CacheDirectoryCreationException,
          MainClassInferenceException, InvalidAppRootException, InvalidWorkingDirectoryException,
          InvalidPlatformException, InvalidContainerVolumeException,
          IncompatibleBaseImageJavaVersionException, NumberFormatException,
          InvalidContainerizingModeException, InvalidFilesModificationTimeException,
          InvalidCreationTimeException {
    Mockito.when(rawConfiguration.getEntrypoint())
        .thenReturn(Optional.of(Arrays.asList("custom", "entrypoint")));
    Mockito.when(rawConfiguration.getExpandClasspathDependencies()).thenReturn(true);

    BuildContext buildContext = getBuildContext(processCommonConfiguration());

    Assert.assertEquals(
        Arrays.asList("custom", "entrypoint"),
        buildContext.getContainerConfiguration().getEntrypoint());
    Mockito.verify(projectProperties)
        .log(
            LogEvent.warn(
                "mainClass, extraClasspath, jvmFlags, and expandClasspathDependencies are ignored "
                    + "when entrypoint is specified"));
  }

  @Test
  public void testEntrypoint_warningOnMainclassForWar()
      throws IOException, InvalidCreationTimeException, InvalidImageReferenceException,
          IncompatibleBaseImageJavaVersionException, InvalidPlatformException,
          InvalidContainerVolumeException, MainClassInferenceException, InvalidAppRootException,
          InvalidWorkingDirectoryException, InvalidFilesModificationTimeException,
          InvalidContainerizingModeException, CacheDirectoryCreationException {
    Mockito.when(rawConfiguration.getMainClass()).thenReturn(Optional.of("java.util.Object"));
    Mockito.when(projectProperties.isWarProject()).thenReturn(true);

    BuildContext buildContext = getBuildContext(processCommonConfiguration());

    Assert.assertNull(buildContext.getContainerConfiguration().getEntrypoint());
    Mockito.verify(projectProperties)
        .log(
            LogEvent.warn(
                "mainClass, extraClasspath, jvmFlags, and expandClasspathDependencies "
                    + "are ignored for WAR projects"));
  }

  @Test
  public void testEntrypoint_warningOnExpandClasspathDependenciesForWar()
      throws IOException, InvalidCreationTimeException, InvalidImageReferenceException,
          IncompatibleBaseImageJavaVersionException, InvalidPlatformException,
          InvalidContainerVolumeException, MainClassInferenceException, InvalidAppRootException,
          InvalidWorkingDirectoryException, InvalidFilesModificationTimeException,
          InvalidContainerizingModeException, CacheDirectoryCreationException {
    Mockito.when(rawConfiguration.getExpandClasspathDependencies()).thenReturn(true);
    Mockito.when(projectProperties.isWarProject()).thenReturn(true);

    BuildContext buildContext = getBuildContext(processCommonConfiguration());

    Assert.assertNull(buildContext.getContainerConfiguration().getEntrypoint());
    Mockito.verify(projectProperties)
        .log(
            LogEvent.warn(
                "mainClass, extraClasspath, jvmFlags, and expandClasspathDependencies "
                    + "are ignored for WAR projects"));
  }

  @Test
  public void testEntrypointClasspath_nonDefaultAppRoot()
      throws InvalidImageReferenceException, IOException, CacheDirectoryCreationException,
          MainClassInferenceException, InvalidAppRootException, InvalidWorkingDirectoryException,
          InvalidPlatformException, InvalidContainerVolumeException,
          IncompatibleBaseImageJavaVersionException, NumberFormatException,
          InvalidContainerizingModeException, InvalidFilesModificationTimeException,
          InvalidCreationTimeException {
    Mockito.when(rawConfiguration.getAppRoot()).thenReturn("/my/app");

    BuildContext buildContext = getBuildContext(processCommonConfiguration());

    Assert.assertEquals(
        Arrays.asList(
            "java", "-cp", "/my/app/resources:/my/app/classes:/my/app/libs/*", "java.lang.Object"),
        buildContext.getContainerConfiguration().getEntrypoint());
  }

  @Test
  public void testWebAppEntrypoint_inheritedFromBaseImage()
      throws InvalidImageReferenceException, IOException, CacheDirectoryCreationException,
          MainClassInferenceException, InvalidAppRootException, InvalidWorkingDirectoryException,
          InvalidPlatformException, InvalidContainerVolumeException,
          IncompatibleBaseImageJavaVersionException, NumberFormatException,
          InvalidContainerizingModeException, InvalidFilesModificationTimeException,
          InvalidCreationTimeException {
    Mockito.when(projectProperties.isWarProject()).thenReturn(true);

    BuildContext buildContext = getBuildContext(processCommonConfiguration());

    Assert.assertNull(buildContext.getContainerConfiguration().getEntrypoint());
  }

  @Test
  public void testGetAppRootChecked() throws InvalidAppRootException {
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
    } catch (InvalidAppRootException ex) {
      Assert.assertEquals("relative/path", ex.getMessage());
    }
  }

  @Test
  public void testGetAppRootChecked_errorOnWindowsPath() {
    Mockito.when(rawConfiguration.getAppRoot()).thenReturn("\\windows\\path");

    try {
      PluginConfigurationProcessor.getAppRootChecked(rawConfiguration, projectProperties);
      Assert.fail();
    } catch (InvalidAppRootException ex) {
      Assert.assertEquals("\\windows\\path", ex.getMessage());
    }
  }

  @Test
  public void testGetAppRootChecked_errorOnWindowsPathWithDriveLetter() {
    Mockito.when(rawConfiguration.getAppRoot()).thenReturn("C:\\windows\\path");

    try {
      PluginConfigurationProcessor.getAppRootChecked(rawConfiguration, projectProperties);
      Assert.fail();
    } catch (InvalidAppRootException ex) {
      Assert.assertEquals("C:\\windows\\path", ex.getMessage());
    }
  }

  @Test
  public void testGetAppRootChecked_defaultNonWarProject() throws InvalidAppRootException {
    Mockito.when(rawConfiguration.getAppRoot()).thenReturn("");
    Mockito.when(projectProperties.isWarProject()).thenReturn(false);

    Assert.assertEquals(
        AbsoluteUnixPath.get("/app"),
        PluginConfigurationProcessor.getAppRootChecked(rawConfiguration, projectProperties));
  }

  @Test
  public void testGetAppRootChecked_defaultWarProject() throws InvalidAppRootException {
    Mockito.when(rawConfiguration.getAppRoot()).thenReturn("");
    Mockito.when(projectProperties.isWarProject()).thenReturn(true);

    Assert.assertEquals(
        AbsoluteUnixPath.get("/jetty/webapps/ROOT"),
        PluginConfigurationProcessor.getAppRootChecked(rawConfiguration, projectProperties));
  }

  @Test
  public void testGetContainerizingModeChecked_packagedWithWar()
      throws InvalidContainerizingModeException {
    Mockito.when(rawConfiguration.getContainerizingMode()).thenReturn("packaged");
    Mockito.when(projectProperties.isWarProject()).thenReturn(true);

    try {
      PluginConfigurationProcessor.getContainerizingModeChecked(
          rawConfiguration, projectProperties);
      Assert.fail();
    } catch (UnsupportedOperationException ex) {
      Assert.assertEquals(
          "packaged containerizing mode for WAR is not yet supported", ex.getMessage());
    }
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
  public void testGetDefaultBaseImage_nonWarPackaging()
      throws IncompatibleBaseImageJavaVersionException {
    Mockito.when(projectProperties.isWarProject()).thenReturn(false);

    Assert.assertEquals(
        "gcr.io/distroless/java:8",
        PluginConfigurationProcessor.getDefaultBaseImage(projectProperties));
  }

  @Test
  public void testGetDefaultBaseImage_warProject()
      throws IncompatibleBaseImageJavaVersionException {
    Mockito.when(projectProperties.isWarProject()).thenReturn(true);

    Assert.assertEquals(
        "gcr.io/distroless/java/jetty:java8",
        PluginConfigurationProcessor.getDefaultBaseImage(projectProperties));
  }

  @Test
  public void testGetDefaultBaseImage_chooseJava8Distroless()
      throws IncompatibleBaseImageJavaVersionException {
    Mockito.when(projectProperties.getMajorJavaVersion()).thenReturn(6);
    Assert.assertEquals(
        "gcr.io/distroless/java:8",
        PluginConfigurationProcessor.getDefaultBaseImage(projectProperties));

    Mockito.when(projectProperties.getMajorJavaVersion()).thenReturn(7);
    Assert.assertEquals(
        "gcr.io/distroless/java:8",
        PluginConfigurationProcessor.getDefaultBaseImage(projectProperties));

    Mockito.when(projectProperties.getMajorJavaVersion()).thenReturn(8);
    Assert.assertEquals(
        "gcr.io/distroless/java:8",
        PluginConfigurationProcessor.getDefaultBaseImage(projectProperties));
  }

  @Test
  public void testGetDefaultBaseImage_chooseJava11Distroless()
      throws IncompatibleBaseImageJavaVersionException {
    Mockito.when(projectProperties.getMajorJavaVersion()).thenReturn(9);
    Assert.assertEquals(
        "gcr.io/distroless/java:11",
        PluginConfigurationProcessor.getDefaultBaseImage(projectProperties));

    Mockito.when(projectProperties.getMajorJavaVersion()).thenReturn(10);
    Assert.assertEquals(
        "gcr.io/distroless/java:11",
        PluginConfigurationProcessor.getDefaultBaseImage(projectProperties));

    Mockito.when(projectProperties.getMajorJavaVersion()).thenReturn(11);
    Assert.assertEquals(
        "gcr.io/distroless/java:11",
        PluginConfigurationProcessor.getDefaultBaseImage(projectProperties));
  }

  @Test
  public void testGetDefaultBaseImage_chooseJava8JettyDistroless()
      throws IncompatibleBaseImageJavaVersionException {
    Mockito.when(projectProperties.getMajorJavaVersion()).thenReturn(6);
    Mockito.when(projectProperties.isWarProject()).thenReturn(true);
    Assert.assertEquals(
        "gcr.io/distroless/java/jetty:java8",
        PluginConfigurationProcessor.getDefaultBaseImage(projectProperties));

    Mockito.when(projectProperties.getMajorJavaVersion()).thenReturn(7);
    Assert.assertEquals(
        "gcr.io/distroless/java/jetty:java8",
        PluginConfigurationProcessor.getDefaultBaseImage(projectProperties));

    Mockito.when(projectProperties.getMajorJavaVersion()).thenReturn(8);
    Assert.assertEquals(
        "gcr.io/distroless/java/jetty:java8",
        PluginConfigurationProcessor.getDefaultBaseImage(projectProperties));
  }

  @Test
  public void testGetDefaultBaseImage_chooseJava11JettyDistroless()
      throws IncompatibleBaseImageJavaVersionException {
    Mockito.when(projectProperties.getMajorJavaVersion()).thenReturn(9);
    Mockito.when(projectProperties.isWarProject()).thenReturn(true);
    Assert.assertEquals(
        "gcr.io/distroless/java/jetty:java11",
        PluginConfigurationProcessor.getDefaultBaseImage(projectProperties));

    Mockito.when(projectProperties.getMajorJavaVersion()).thenReturn(10);
    Assert.assertEquals(
        "gcr.io/distroless/java/jetty:java11",
        PluginConfigurationProcessor.getDefaultBaseImage(projectProperties));

    Mockito.when(projectProperties.getMajorJavaVersion()).thenReturn(11);
    Assert.assertEquals(
        "gcr.io/distroless/java/jetty:java11",
        PluginConfigurationProcessor.getDefaultBaseImage(projectProperties));
  }

  @Test
  public void testGetDefaultBaseImage_projectHigherThanJava11() {
    Mockito.when(projectProperties.getMajorJavaVersion()).thenReturn(12);

    try {
      PluginConfigurationProcessor.getDefaultBaseImage(projectProperties);
      Assert.fail();
    } catch (IncompatibleBaseImageJavaVersionException ex) {
      Assert.assertEquals(11, ex.getBaseImageMajorJavaVersion());
      Assert.assertEquals(12, ex.getProjectMajorJavaVersion());
    }
  }

  @Test
  public void testGetJavaContainerBuilderWithBaseImage_dockerBase()
      throws IncompatibleBaseImageJavaVersionException, IOException, InvalidImageReferenceException,
          CacheDirectoryCreationException {
    Mockito.when(rawConfiguration.getFromImage()).thenReturn(Optional.of("docker://ima.ge/name"));
    ImageConfiguration result = getCommonImageConfiguration();
    Assert.assertEquals("ima.ge/name", result.getImage().toString());
    Assert.assertTrue(result.getDockerClient().isPresent());
    Assert.assertFalse(result.getTarPath().isPresent());
  }

  @Test
  public void testGetJavaContainerBuilderWithBaseImage_tarBase()
      throws IncompatibleBaseImageJavaVersionException, IOException, InvalidImageReferenceException,
          CacheDirectoryCreationException {
    Mockito.when(rawConfiguration.getFromImage()).thenReturn(Optional.of("tar:///path/to.tar"));
    ImageConfiguration result = getCommonImageConfiguration();
    Assert.assertEquals(Paths.get("/path/to.tar"), result.getTarPath().get());
    Assert.assertFalse(result.getDockerClient().isPresent());
  }

  @Test
  public void testGetJavaContainerBuilderWithBaseImage_registry()
      throws IncompatibleBaseImageJavaVersionException, InvalidImageReferenceException, IOException,
          CacheDirectoryCreationException {
    Mockito.when(rawConfiguration.getFromImage()).thenReturn(Optional.of("ima.ge/name"));
    ImageConfiguration result = getCommonImageConfiguration();
    Assert.assertEquals("ima.ge/name", result.getImage().toString());
    Assert.assertFalse(result.getDockerClient().isPresent());
    Assert.assertFalse(result.getTarPath().isPresent());
  }

  @Test
  public void testGetJavaContainerBuilderWithBaseImage_registryWithPrefix()
      throws IncompatibleBaseImageJavaVersionException, InvalidImageReferenceException, IOException,
          CacheDirectoryCreationException {
    Mockito.when(rawConfiguration.getFromImage()).thenReturn(Optional.of("registry://ima.ge/name"));
    ImageConfiguration result = getCommonImageConfiguration();
    Assert.assertEquals("ima.ge/name", result.getImage().toString());
    Assert.assertFalse(result.getDockerClient().isPresent());
    Assert.assertFalse(result.getTarPath().isPresent());
  }

  @Test
  public void testGetJavaContainerBuilderWithBaseImage_incompatibleJava8BaseImage()
      throws InvalidImageReferenceException, FileNotFoundException {
    Mockito.when(projectProperties.getMajorJavaVersion()).thenReturn(11);

    Mockito.when(rawConfiguration.getFromImage())
        .thenReturn(Optional.of("gcr.io/distroless/java:8"));
    try {
      PluginConfigurationProcessor.getJavaContainerBuilderWithBaseImage(
          rawConfiguration, projectProperties, inferredAuthProvider);
      Assert.fail();
    } catch (IncompatibleBaseImageJavaVersionException ex) {
      Assert.assertEquals(8, ex.getBaseImageMajorJavaVersion());
      Assert.assertEquals(11, ex.getProjectMajorJavaVersion());
    }

    Mockito.when(rawConfiguration.getFromImage())
        .thenReturn(Optional.of("gcr.io/distroless/java:latest"));
    try {
      PluginConfigurationProcessor.getJavaContainerBuilderWithBaseImage(
          rawConfiguration, projectProperties, inferredAuthProvider);
      Assert.fail();
    } catch (IncompatibleBaseImageJavaVersionException ex) {
      Assert.assertEquals(8, ex.getBaseImageMajorJavaVersion());
      Assert.assertEquals(11, ex.getProjectMajorJavaVersion());
    }
  }

  @Test
  public void testGetJavaContainerBuilderWithBaseImage_incompatibleJava11BaseImage()
      throws InvalidImageReferenceException, FileNotFoundException {
    Mockito.when(projectProperties.getMajorJavaVersion()).thenReturn(15);

    Mockito.when(rawConfiguration.getFromImage())
        .thenReturn(Optional.of("gcr.io/distroless/java:11"));
    try {
      PluginConfigurationProcessor.getJavaContainerBuilderWithBaseImage(
          rawConfiguration, projectProperties, inferredAuthProvider);
      Assert.fail();
    } catch (IncompatibleBaseImageJavaVersionException ex) {
      Assert.assertEquals(11, ex.getBaseImageMajorJavaVersion());
      Assert.assertEquals(15, ex.getProjectMajorJavaVersion());
    }
  }

  @Test
  public void testGetJavaContainerBuilderWithBaseImage_incompatibleJava8JettyBaseImage()
      throws InvalidImageReferenceException, FileNotFoundException {
    Mockito.when(projectProperties.getMajorJavaVersion()).thenReturn(11);

    Mockito.when(rawConfiguration.getFromImage())
        .thenReturn(Optional.of("gcr.io/distroless/java/jetty:java8"));
    try {
      PluginConfigurationProcessor.getJavaContainerBuilderWithBaseImage(
          rawConfiguration, projectProperties, inferredAuthProvider);
      Assert.fail();
    } catch (IncompatibleBaseImageJavaVersionException ex) {
      Assert.assertEquals(8, ex.getBaseImageMajorJavaVersion());
      Assert.assertEquals(11, ex.getProjectMajorJavaVersion());
    }
  }

  @Test
  public void testGetJavaContainerBuilderWithBaseImage_incompatibleJava11JettyBaseImage()
      throws InvalidImageReferenceException, FileNotFoundException {
    Mockito.when(projectProperties.getMajorJavaVersion()).thenReturn(15);

    Mockito.when(rawConfiguration.getFromImage())
        .thenReturn(Optional.of("gcr.io/distroless/java/jetty:java11"));
    try {
      PluginConfigurationProcessor.getJavaContainerBuilderWithBaseImage(
          rawConfiguration, projectProperties, inferredAuthProvider);
      Assert.fail();
    } catch (IncompatibleBaseImageJavaVersionException ex) {
      Assert.assertEquals(11, ex.getBaseImageMajorJavaVersion());
      Assert.assertEquals(15, ex.getProjectMajorJavaVersion());
    }
  }

  // https://github.com/GoogleContainerTools/jib/issues/1995
  @Test
  public void testGetJavaContainerBuilderWithBaseImage_java12BaseImage()
      throws InvalidImageReferenceException, IOException, IncompatibleBaseImageJavaVersionException,
          CacheDirectoryCreationException {
    Mockito.when(projectProperties.getMajorJavaVersion()).thenReturn(12);
    Mockito.when(rawConfiguration.getFromImage()).thenReturn(Optional.of("regis.try/java12image"));
    ImageConfiguration imageConfiguration = getCommonImageConfiguration();
    Assert.assertEquals("regis.try", imageConfiguration.getImageRegistry());
    Assert.assertEquals("java12image", imageConfiguration.getImageRepository());
  }

  @Test
  public void testGetJavaContainerBuilderWithBaseImage_java12NoBaseImage()
      throws InvalidImageReferenceException, IOException {
    Mockito.when(projectProperties.getMajorJavaVersion()).thenReturn(12);
    Mockito.when(rawConfiguration.getFromImage()).thenReturn(Optional.empty());
    try {
      PluginConfigurationProcessor.getJavaContainerBuilderWithBaseImage(
          rawConfiguration, projectProperties, inferredAuthProvider);
      Assert.fail();
    } catch (IncompatibleBaseImageJavaVersionException ex) {
      Assert.assertEquals(11, ex.getBaseImageMajorJavaVersion());
      Assert.assertEquals(12, ex.getProjectMajorJavaVersion());
    }
  }

  @Test
  public void testGetPlatformsSet() throws InvalidPlatformException {
    Mockito.<List<?>>when(rawConfiguration.getPlatforms())
        .thenReturn(Arrays.asList(new TestPlatformConfiguration("testArchitecture", "testOs")));

    Assert.assertEquals(
        ImmutableSet.of(new Platform("testArchitecture", "testOs")),
        PluginConfigurationProcessor.getPlatformsSet(rawConfiguration));
  }

  @Test
  public void testGetPlatformsSet_architectureMissing() {
    TestPlatformConfiguration platform = new TestPlatformConfiguration(null, "testOs");
    Mockito.<List<?>>when(rawConfiguration.getPlatforms()).thenReturn(Arrays.asList(platform));

    try {
      PluginConfigurationProcessor.getPlatformsSet(rawConfiguration);
      Assert.fail();
    } catch (InvalidPlatformException ex) {
      Assert.assertEquals(
          "platform configuration is missing an architecture value", ex.getMessage());
      Assert.assertEquals("architecture=<missing>, os=testOs", ex.getInvalidPlatform());
    }
  }

  @Test
  public void testGetPlatformsSet_osMissing() {
    TestPlatformConfiguration platform = new TestPlatformConfiguration("testArchitecture", null);
    Mockito.<List<?>>when(rawConfiguration.getPlatforms()).thenReturn(Arrays.asList(platform));

    try {
      PluginConfigurationProcessor.getPlatformsSet(rawConfiguration);
      Assert.fail();
    } catch (InvalidPlatformException ex) {
      Assert.assertEquals("platform configuration is missing an OS value", ex.getMessage());
      Assert.assertEquals("architecture=testArchitecture, os=<missing>", ex.getInvalidPlatform());
    }
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

  @Test
  public void testCreateModificationTimeProvider_epochPlusSecond()
      throws InvalidFilesModificationTimeException {
    ModificationTimeProvider timeProvider =
        PluginConfigurationProcessor.createModificationTimeProvider("EPOCH_PLUS_SECOND");
    Assert.assertEquals(
        Instant.ofEpochSecond(1), timeProvider.get(Paths.get("foo"), AbsoluteUnixPath.get("/bar")));
  }

  @Test
  public void testCreateModificationTimeProvider_isoDateTimeValue()
      throws InvalidFilesModificationTimeException {
    ModificationTimeProvider timeProvider =
        PluginConfigurationProcessor.createModificationTimeProvider("2011-12-03T10:15:30+09:00");
    Instant expected = DateTimeFormatter.ISO_DATE_TIME.parse("2011-12-03T01:15:30Z", Instant::from);
    Assert.assertEquals(expected, timeProvider.get(Paths.get("foo"), AbsoluteUnixPath.get("/bar")));
  }

  @Test
  public void testCreateModificationTimeProvider_invalidValue() {
    try {
      ModificationTimeProvider timeProvider =
          PluginConfigurationProcessor.createModificationTimeProvider("invalid format");
      timeProvider.get(Paths.get("foo"), AbsoluteUnixPath.get("/bar"));
      Assert.fail();
    } catch (InvalidFilesModificationTimeException ex) {
      Assert.assertEquals("invalid format", ex.getMessage());
      Assert.assertEquals("invalid format", ex.getInvalidFilesModificationTime());
    }
  }

  @Test
  public void testGetCreationTime_epoch() throws InvalidCreationTimeException {
    Instant time = PluginConfigurationProcessor.getCreationTime("EPOCH", projectProperties);
    Assert.assertEquals(Instant.EPOCH, time);
  }

  @Test
  public void testGetCreationTime_useCurrentTimestamp() throws InvalidCreationTimeException {
    Instant now = Instant.now().minusSeconds(2);
    Instant time =
        PluginConfigurationProcessor.getCreationTime("USE_CURRENT_TIMESTAMP", projectProperties);
    Assert.assertTrue(time.isAfter(now));
  }

  @Test
  public void testGetCreationTime_isoDateTimeValue() throws InvalidCreationTimeException {
    Instant expected = DateTimeFormatter.ISO_DATE_TIME.parse("2011-12-03T01:15:30Z", Instant::from);
    List<String> validTimeStamps =
        ImmutableList.of(
            "2011-12-03T10:15:30+09:00",
            "2011-12-03T10:15:30+09:00[Asia/Tokyo]",
            "2011-12-02T16:15:30-09:00",
            "2011-12-03T10:15:30+0900",
            "2011-12-02T16:15:30-0900",
            "2011-12-03T10:15:30+09",
            "2011-12-02T16:15:30-09",
            "2011-12-03T01:15:30Z");
    for (String timeString : validTimeStamps) {
      Instant time = PluginConfigurationProcessor.getCreationTime(timeString, projectProperties);
      Assert.assertEquals("for " + timeString, expected, time);
    }
  }

  @Test
  public void testGetCreationTime_isoDateTimeValueTimeZoneRegionOnlyAllowedForMostStrict8601Mode() {
    List<String> invalidTimeStamps =
        ImmutableList.of(
            "2011-12-03T01:15:30+0900[Asia/Tokyo]", "2011-12-03T01:15:30+09[Asia/Tokyo]");
    for (String timeString : invalidTimeStamps) {
      try {
        PluginConfigurationProcessor.getCreationTime(timeString, projectProperties);
        // this is the expected behavior, not specifically designed like this for any reason, feel
        // free to change this behavior and update the test
        Assert.fail(
            "creationTime should fail if region specified when zone not in HH:MM mode - "
                + timeString);
      } catch (InvalidCreationTimeException ex) {
        // pass
      }
    }
  }

  @Test
  public void testGetCreationTime_isoDateTimeValueRequiresTimeZone() {
    try {
      PluginConfigurationProcessor.getCreationTime("2011-12-03T01:15:30", projectProperties);
      // this is the expected behavior, not specifically designed like this for any reason, feel
      // free to change this behavior and update the test
      Assert.fail("getCreationTime should fail if timezone not specified");
    } catch (InvalidCreationTimeException ex) {
      // pass
    }
  }

  @Test
  public void testGetCreationTime_invalidValue() {
    try {
      PluginConfigurationProcessor.getCreationTime("invalid format", projectProperties);
      Assert.fail();
    } catch (InvalidCreationTimeException ex) {
      Assert.assertEquals("invalid format", ex.getMessage());
      Assert.assertEquals("invalid format", ex.getInvalidCreationTime());
    }
  }

  private ImageConfiguration getCommonImageConfiguration()
      throws IncompatibleBaseImageJavaVersionException, IOException, InvalidImageReferenceException,
          CacheDirectoryCreationException {
    return getBuildContext(
            PluginConfigurationProcessor.getJavaContainerBuilderWithBaseImage(
                    rawConfiguration, projectProperties, inferredAuthProvider)
                .addClasses(temporaryFolder.getRoot().toPath())
                .toContainerBuilder())
        .getBaseImageConfiguration();
  }

  private JibContainerBuilder processCommonConfiguration()
      throws InvalidImageReferenceException, MainClassInferenceException, InvalidAppRootException,
          IOException, InvalidWorkingDirectoryException, InvalidPlatformException,
          InvalidContainerVolumeException, IncompatibleBaseImageJavaVersionException,
          NumberFormatException, InvalidContainerizingModeException,
          InvalidFilesModificationTimeException, InvalidCreationTimeException {
    return PluginConfigurationProcessor.processCommonConfiguration(
        rawConfiguration, ignored -> Optional.empty(), projectProperties, containerizer);
  }

  @Test
  public void getAllFiles_expandsDirectories() throws IOException {
    File rootFile = temporaryFolder.newFile("file");
    File folder = temporaryFolder.newFolder("folder");
    File folderFile = temporaryFolder.newFile("folder/file2");
    Assert.assertEquals(
        ImmutableSet.of(rootFile.toPath().toAbsolutePath(), folderFile.toPath().toAbsolutePath()),
        PluginConfigurationProcessor.getAllFiles(
            ImmutableSet.of(rootFile.toPath(), folder.toPath())));
  }

  @Test
  public void getAllFiles_doesntBreakForNonExistentFiles() throws IOException {
    Path testPath = Paths.get("/a/file/that/doesnt/exist");
    Assert.assertFalse(Files.exists(testPath));
    Assert.assertEquals(
        ImmutableSet.of(), PluginConfigurationProcessor.getAllFiles(ImmutableSet.of(testPath)));
  }
}
