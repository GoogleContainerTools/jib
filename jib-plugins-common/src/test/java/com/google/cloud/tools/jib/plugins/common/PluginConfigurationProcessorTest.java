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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
import com.google.cloud.tools.jib.api.buildplan.ContainerBuildPlan;
import com.google.cloud.tools.jib.api.buildplan.FileEntriesLayer;
import com.google.cloud.tools.jib.api.buildplan.FileEntry;
import com.google.cloud.tools.jib.api.buildplan.FilePermissions;
import com.google.cloud.tools.jib.api.buildplan.ModificationTimeProvider;
import com.google.cloud.tools.jib.api.buildplan.Platform;
import com.google.cloud.tools.jib.configuration.ImageConfiguration;
import com.google.cloud.tools.jib.plugins.common.RawConfiguration.CredHelperConfiguration;
import com.google.cloud.tools.jib.plugins.common.RawConfiguration.ExtraDirectoriesConfiguration;
import com.google.cloud.tools.jib.plugins.common.RawConfiguration.PlatformConfiguration;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Resources;
import com.google.common.truth.Correspondence;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
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
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/** Tests for {@link PluginConfigurationProcessor}. */
@RunWith(JUnitParamsRunner.class)
public class PluginConfigurationProcessorTest {

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

  private static class TestExtraDirectoriesConfiguration implements ExtraDirectoriesConfiguration {

    private final Path from;

    private TestExtraDirectoriesConfiguration(Path from) {
      this.from = from;
    }

    @Override
    public Path getFrom() {
      return from;
    }

    @Override
    public String getInto() {
      return "/target/dir";
    }

    @Override
    public List<String> getIncludesList() {
      return Collections.emptyList();
    }

    @Override
    public List<String> getExcludesList() {
      return Collections.emptyList();
    }
  }

  private static final Correspondence<FileEntry, Path> SOURCE_FILE_OF =
      Correspondence.transforming(FileEntry::getSourceFile, "has sourceFile of");
  private static final Correspondence<FileEntry, String> EXTRACTION_PATH_OF =
      Correspondence.transforming(
          entry -> entry.getExtractionPath().toString(), "has extractionPath of");

  private static List<FileEntry> getLayerEntries(ContainerBuildPlan buildPlan, String layerName) {
    @SuppressWarnings("unchecked")
    List<FileEntriesLayer> layers = ((List<FileEntriesLayer>) buildPlan.getLayers());
    return layers.stream()
        .filter(layer -> layer.getName().equals(layerName))
        .findFirst()
        .get()
        .getEntries();
  }

  @Rule public final MockitoRule mockitoRule = MockitoJUnit.rule().silent();
  @Rule public final RestoreSystemProperties systemPropertyRestorer = new RestoreSystemProperties();
  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Mock(answer = Answers.RETURNS_SELF)
  private Containerizer containerizer;

  @Mock private RawConfiguration rawConfiguration;
  @Mock private ProjectProperties projectProperties;
  @Mock private InferredAuthProvider inferredAuthProvider;
  @Mock private AuthProperty authProperty;
  @Mock private Consumer<LogEvent> logger;
  @Mock private CredHelperConfiguration fromCredHelperConfig;
  @Mock private CredHelperConfiguration toCredHelperConfig;

  private Path appCacheDirectory;
  private final JibContainerBuilder jibContainerBuilder = Jib.fromScratch();

  @Before
  public void setUp() throws IOException, InvalidImageReferenceException, InferredAuthException {
    when(rawConfiguration.getFromAuth()).thenReturn(authProperty);
    when(rawConfiguration.getEntrypoint()).thenReturn(Optional.empty());
    when(rawConfiguration.getAppRoot()).thenReturn("/app");
    Mockito.<List<?>>when(rawConfiguration.getPlatforms())
        .thenReturn(Arrays.asList(new TestPlatformConfiguration("amd64", "linux")));
    when(rawConfiguration.getFilesModificationTime()).thenReturn("EPOCH_PLUS_SECOND");
    when(rawConfiguration.getCreationTime()).thenReturn("EPOCH");
    when(rawConfiguration.getContainerizingMode()).thenReturn("exploded");
    when(rawConfiguration.getFromCredHelperConfig()).thenReturn(fromCredHelperConfig);
    when(rawConfiguration.getToCredHelperConfig()).thenReturn(toCredHelperConfig);
    when(projectProperties.getMajorJavaVersion()).thenReturn(8);
    when(projectProperties.getToolName()).thenReturn("tool");
    when(projectProperties.getToolVersion()).thenReturn("tool-version");
    when(projectProperties.getMainClassFromJarPlugin()).thenReturn("java.lang.Object");
    when(projectProperties.createJibContainerBuilder(
            any(JavaContainerBuilder.class), any(ContainerizingMode.class)))
        .thenReturn(Jib.from("base"));
    when(projectProperties.isOffline()).thenReturn(false);
    when(projectProperties.getDependencies())
        .thenReturn(Arrays.asList(Paths.get("/repo/foo-1.jar"), Paths.get("/home/libs/bar-2.jar")));

    appCacheDirectory = temporaryFolder.newFolder("jib-cache").toPath();
    when(projectProperties.getDefaultCacheDirectory()).thenReturn(appCacheDirectory);

    when(inferredAuthProvider.inferAuth(any())).thenReturn(Optional.empty());
  }

  @Test
  public void testPluginConfigurationProcessor_defaults()
      throws InvalidImageReferenceException, IOException, MainClassInferenceException,
          InvalidAppRootException, InvalidWorkingDirectoryException, InvalidPlatformException,
          InvalidContainerVolumeException, IncompatibleBaseImageJavaVersionException,
          NumberFormatException, InvalidContainerizingModeException,
          InvalidFilesModificationTimeException, InvalidCreationTimeException {
    ContainerBuildPlan buildPlan = processCommonConfiguration();

    assertThat(buildPlan.getEntrypoint())
        .containsExactly(
            "java", "-cp", "/app/resources:/app/classes:/app/libs/*", "java.lang.Object")
        .inOrder();

    verify(containerizer).setBaseImageLayersCache(Containerizer.DEFAULT_BASE_CACHE_DIRECTORY);
    verify(containerizer).setApplicationLayersCache(appCacheDirectory);

    ArgumentMatcher<LogEvent> isLogWarn = logEvent -> logEvent.getLevel() == LogEvent.Level.WARN;
    verify(logger, never()).accept(argThat(isLogWarn));
  }

  @Test
  public void testPluginConfigurationProcessor_extraDirectory()
      throws URISyntaxException, InvalidContainerVolumeException, MainClassInferenceException,
          InvalidAppRootException, IOException, IncompatibleBaseImageJavaVersionException,
          InvalidWorkingDirectoryException, InvalidPlatformException,
          InvalidImageReferenceException, NumberFormatException, InvalidContainerizingModeException,
          InvalidFilesModificationTimeException, InvalidCreationTimeException {
    Path extraDirectory = Paths.get(Resources.getResource("core/layer").toURI());
    Mockito.<List<?>>when(rawConfiguration.getExtraDirectories())
        .thenReturn(Arrays.asList(new TestExtraDirectoriesConfiguration(extraDirectory)));
    when(rawConfiguration.getExtraDirectoryPermissions())
        .thenReturn(ImmutableMap.of("/target/dir/foo", FilePermissions.fromOctalString("123")));

    ContainerBuildPlan buildPlan = processCommonConfiguration();
    List<FileEntry> extraFiles = getLayerEntries(buildPlan, "extra files");

    assertThat(extraFiles)
        .comparingElementsUsing(SOURCE_FILE_OF)
        .containsExactly(
            extraDirectory.resolve("a"),
            extraDirectory.resolve("a/b"),
            extraDirectory.resolve("a/b/bar"),
            extraDirectory.resolve("c"),
            extraDirectory.resolve("c/cat"),
            extraDirectory.resolve("foo"));
    assertThat(extraFiles)
        .comparingElementsUsing(EXTRACTION_PATH_OF)
        .containsExactly(
            "/target/dir/a",
            "/target/dir/a/b",
            "/target/dir/a/b/bar",
            "/target/dir/c",
            "/target/dir/c/cat",
            "/target/dir/foo");

    Optional<FileEntry> fooEntry =
        extraFiles.stream()
            .filter(
                layerEntry ->
                    layerEntry.getExtractionPath().equals(AbsoluteUnixPath.get("/target/dir/foo")))
            .findFirst();
    assertThat(fooEntry).isPresent();
    assertThat(fooEntry.get().getPermissions().toOctalString()).isEqualTo("123");
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

    verify(containerizer).setBaseImageLayersCache(Paths.get("new/base/cache"));
    verify(containerizer).setApplicationLayersCache(Paths.get("/new/application/cache"));
  }

  @Test
  public void testAddJvmArgFilesLayer() throws IOException, InvalidAppRootException {
    String classpath = "/extra:/app/classes:/app/libs/dep.jar";
    String mainClass = "com.example.Main";
    PluginConfigurationProcessor.addJvmArgFilesLayer(
        rawConfiguration, projectProperties, jibContainerBuilder, classpath, mainClass);

    Path classpathFile = appCacheDirectory.resolve("jib-classpath-file");
    Path mainClassFile = appCacheDirectory.resolve("jib-main-class-file");
    String classpathRead = new String(Files.readAllBytes(classpathFile), StandardCharsets.UTF_8);
    String mainClassRead = new String(Files.readAllBytes(mainClassFile), StandardCharsets.UTF_8);
    assertThat(classpathRead).isEqualTo(classpath);
    assertThat(mainClassRead).isEqualTo(mainClass);

    List<FileEntry> layerEntries =
        getLayerEntries(jibContainerBuilder.toContainerBuildPlan(), "jvm arg files");
    assertThat(layerEntries)
        .comparingElementsUsing(SOURCE_FILE_OF)
        .containsExactly(
            appCacheDirectory.resolve("jib-classpath-file"),
            appCacheDirectory.resolve("jib-main-class-file"));
    assertThat(layerEntries)
        .comparingElementsUsing(EXTRACTION_PATH_OF)
        .containsExactly("/app/jib-classpath-file", "/app/jib-main-class-file");
  }

  @Test
  public void testWriteFileConservatively() throws IOException {
    Path file = temporaryFolder.getRoot().toPath().resolve("file.txt");

    PluginConfigurationProcessor.writeFileConservatively(file, "some content");

    String content = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    assertThat(content).isEqualTo("some content");
  }

  @Test
  public void testWriteFileConservatively_updatedContent() throws IOException {
    Path file = temporaryFolder.newFile().toPath();

    PluginConfigurationProcessor.writeFileConservatively(file, "some content");

    String content = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    assertThat(content).isEqualTo("some content");
  }

  @Test
  public void testWriteFileConservatively_noWriteIfUnchanged() throws IOException {
    Path file = temporaryFolder.newFile().toPath();
    Files.write(file, "some content".getBytes(StandardCharsets.UTF_8));
    FileTime fileTime = Files.getLastModifiedTime(file);

    PluginConfigurationProcessor.writeFileConservatively(file, "some content");

    String content = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    assertThat(content).isEqualTo("some content");
    assertThat(Files.getLastModifiedTime(file)).isEqualTo(fileTime);
  }

  @Test
  public void testEntrypoint()
      throws InvalidImageReferenceException, IOException, MainClassInferenceException,
          InvalidAppRootException, InvalidWorkingDirectoryException, InvalidPlatformException,
          InvalidContainerVolumeException, IncompatibleBaseImageJavaVersionException,
          NumberFormatException, InvalidContainerizingModeException,
          InvalidFilesModificationTimeException, InvalidCreationTimeException {
    when(rawConfiguration.getEntrypoint())
        .thenReturn(Optional.of(Arrays.asList("custom", "entrypoint")));

    ContainerBuildPlan buildPlan = processCommonConfiguration();

    assertThat(buildPlan.getEntrypoint()).containsExactly("custom", "entrypoint").inOrder();
    verifyNoInteractions(logger);
  }

  @Test
  public void testComputeEntrypoint_inheritKeyword()
      throws MainClassInferenceException, InvalidAppRootException, IOException,
          InvalidContainerizingModeException {
    when(rawConfiguration.getEntrypoint())
        .thenReturn(Optional.of(Collections.singletonList("INHERIT")));

    assertThat(
            PluginConfigurationProcessor.computeEntrypoint(
                rawConfiguration, projectProperties, jibContainerBuilder))
        .isNull();
  }

  @Test
  public void testComputeEntrypoint_inheritKeywordInNonSingletonList()
      throws MainClassInferenceException, InvalidAppRootException, IOException,
          InvalidContainerizingModeException {
    when(rawConfiguration.getEntrypoint()).thenReturn(Optional.of(Arrays.asList("INHERIT", "")));

    assertThat(
            PluginConfigurationProcessor.computeEntrypoint(
                rawConfiguration, projectProperties, jibContainerBuilder))
        .isNotNull();
  }

  @Test
  public void testComputeEntrypoint_default()
      throws MainClassInferenceException, InvalidAppRootException, IOException,
          InvalidContainerizingModeException {
    assertThat(
            PluginConfigurationProcessor.computeEntrypoint(
                rawConfiguration, projectProperties, jibContainerBuilder))
        .containsExactly(
            "java", "-cp", "/app/resources:/app/classes:/app/libs/*", "java.lang.Object")
        .inOrder();
  }

  @Test
  public void testComputeEntrypoint_packaged()
      throws MainClassInferenceException, InvalidAppRootException, IOException,
          InvalidContainerizingModeException {
    when(rawConfiguration.getContainerizingMode()).thenReturn("packaged");
    assertThat(
            PluginConfigurationProcessor.computeEntrypoint(
                rawConfiguration, projectProperties, jibContainerBuilder))
        .containsExactly("java", "-cp", "/app/classpath/*:/app/libs/*", "java.lang.Object")
        .inOrder();
  }

  @Test
  public void testComputeEntrypoint_expandClasspathDependencies()
      throws MainClassInferenceException, InvalidAppRootException, IOException,
          InvalidContainerizingModeException {
    when(rawConfiguration.getExpandClasspathDependencies()).thenReturn(true);
    assertThat(
            PluginConfigurationProcessor.computeEntrypoint(
                rawConfiguration, projectProperties, jibContainerBuilder))
        .containsExactly(
            "java",
            "-cp",
            "/app/resources:/app/classes:/app/libs/foo-1.jar:/app/libs/bar-2.jar",
            "java.lang.Object")
        .inOrder();
  }

  @Test
  public void testComputeEntrypoint_expandClasspathDependencies_sizeAddedForDuplicateJars()
      throws MainClassInferenceException, InvalidAppRootException, IOException,
          InvalidContainerizingModeException {
    Path libFoo13 = temporaryFolder.newFolder().toPath().resolve("foo-1.jar");
    Path libFoo45 = temporaryFolder.newFolder().toPath().resolve("foo-1.jar");
    Files.write(libFoo13, new byte[13]);
    Files.write(libFoo45, new byte[45]);

    when(rawConfiguration.getExpandClasspathDependencies()).thenReturn(true);
    when(projectProperties.getDependencies())
        .thenReturn(Arrays.asList(libFoo13, Paths.get("/home/libs/bar-2.jar"), libFoo45));

    assertThat(
            PluginConfigurationProcessor.computeEntrypoint(
                rawConfiguration, projectProperties, jibContainerBuilder))
        .containsExactly(
            "java",
            "-cp",
            "/app/resources:/app/classes:"
                + "/app/libs/foo-1-13.jar:/app/libs/bar-2.jar:/app/libs/foo-1-45.jar",
            "java.lang.Object")
        .inOrder();
  }

  @Test
  public void testEntrypoint_defaultWarPackaging()
      throws IOException, InvalidImageReferenceException, MainClassInferenceException,
          InvalidAppRootException, InvalidWorkingDirectoryException, InvalidPlatformException,
          InvalidContainerVolumeException, IncompatibleBaseImageJavaVersionException,
          NumberFormatException, InvalidContainerizingModeException,
          InvalidFilesModificationTimeException, InvalidCreationTimeException {
    when(projectProperties.isWarProject()).thenReturn(true);

    ContainerBuildPlan buildPlan = processCommonConfiguration();

    assertThat(buildPlan.getEntrypoint())
        .containsExactly("java", "-jar", "/usr/local/jetty/start.jar")
        .inOrder();
    verifyNoInteractions(logger);
  }

  @Test
  public void testEntrypoint_defaultNonWarPackaging()
      throws IOException, InvalidImageReferenceException, MainClassInferenceException,
          InvalidAppRootException, InvalidWorkingDirectoryException, InvalidPlatformException,
          InvalidContainerVolumeException, IncompatibleBaseImageJavaVersionException,
          NumberFormatException, InvalidContainerizingModeException,
          InvalidFilesModificationTimeException, InvalidCreationTimeException {
    when(projectProperties.isWarProject()).thenReturn(false);

    ContainerBuildPlan buildPlan = processCommonConfiguration();

    assertThat(buildPlan.getEntrypoint())
        .containsExactly(
            "java", "-cp", "/app/resources:/app/classes:/app/libs/*", "java.lang.Object")
        .inOrder();

    ArgumentMatcher<LogEvent> isLogWarn = logEvent -> logEvent.getLevel() == LogEvent.Level.WARN;
    verify(logger, never()).accept(argThat(isLogWarn));
  }

  @Test
  public void testEntrypoint_extraClasspathNonWarPackaging()
      throws IOException, InvalidImageReferenceException, MainClassInferenceException,
          InvalidAppRootException, InvalidWorkingDirectoryException, InvalidPlatformException,
          InvalidContainerVolumeException, IncompatibleBaseImageJavaVersionException,
          NumberFormatException, InvalidContainerizingModeException,
          InvalidFilesModificationTimeException, InvalidCreationTimeException {
    when(rawConfiguration.getExtraClasspath()).thenReturn(Collections.singletonList("/foo"));
    when(projectProperties.isWarProject()).thenReturn(false);

    ContainerBuildPlan buildPlan = processCommonConfiguration();

    assertThat(buildPlan.getEntrypoint())
        .containsExactly(
            "java", "-cp", "/foo:/app/resources:/app/classes:/app/libs/*", "java.lang.Object")
        .inOrder();

    ArgumentMatcher<LogEvent> isLogWarn = logEvent -> logEvent.getLevel() == LogEvent.Level.WARN;
    verify(logger, never()).accept(argThat(isLogWarn));
  }

  @Test
  public void testClasspathArgumentFile()
      throws NumberFormatException, InvalidImageReferenceException, MainClassInferenceException,
          InvalidAppRootException, IOException, InvalidWorkingDirectoryException,
          InvalidPlatformException, InvalidContainerVolumeException,
          IncompatibleBaseImageJavaVersionException, InvalidContainerizingModeException,
          InvalidFilesModificationTimeException, InvalidCreationTimeException {
    when(rawConfiguration.getExtraClasspath()).thenReturn(Collections.singletonList("/foo"));
    when(projectProperties.getMajorJavaVersion()).thenReturn(9);

    ContainerBuildPlan buildPlan = processCommonConfiguration();

    assertThat(buildPlan.getEntrypoint())
        .containsExactly("java", "-cp", "@/app/jib-classpath-file", "java.lang.Object")
        .inOrder();

    List<FileEntry> jvmArgFiles = getLayerEntries(buildPlan, "jvm arg files");
    assertThat(jvmArgFiles)
        .comparingElementsUsing(SOURCE_FILE_OF)
        .containsExactly(
            appCacheDirectory.resolve("jib-classpath-file"),
            appCacheDirectory.resolve("jib-main-class-file"));
    assertThat(jvmArgFiles)
        .comparingElementsUsing(EXTRACTION_PATH_OF)
        .containsExactly("/app/jib-classpath-file", "/app/jib-main-class-file");

    String classpath =
        new String(
            Files.readAllBytes(appCacheDirectory.resolve("jib-classpath-file")),
            StandardCharsets.UTF_8);
    assertThat(classpath)
        .isEqualTo("/foo:/app/resources:/app/classes:/app/libs/foo-1.jar:/app/libs/bar-2.jar");
    String mainClass =
        new String(
            Files.readAllBytes(appCacheDirectory.resolve("jib-main-class-file")),
            StandardCharsets.UTF_8);
    assertThat(mainClass).isEqualTo("java.lang.Object");
  }

  @Test
  public void testClasspathArgumentFile_mainClassInferenceFailureWithCustomEntrypoint()
      throws NumberFormatException, InvalidImageReferenceException, MainClassInferenceException,
          InvalidAppRootException, IOException, InvalidWorkingDirectoryException,
          InvalidPlatformException, InvalidContainerVolumeException,
          IncompatibleBaseImageJavaVersionException, InvalidContainerizingModeException,
          InvalidFilesModificationTimeException, InvalidCreationTimeException {
    when(rawConfiguration.getMainClass()).thenReturn(Optional.of("invalid main class"));
    when(rawConfiguration.getEntrypoint()).thenReturn(Optional.of(Arrays.asList("bash")));

    ContainerBuildPlan buildPlan = processCommonConfiguration();

    assertThat(buildPlan.getEntrypoint()).containsExactly("bash");

    List<FileEntry> jvmArgFiles = getLayerEntries(buildPlan, "jvm arg files");
    assertThat(jvmArgFiles)
        .comparingElementsUsing(SOURCE_FILE_OF)
        .containsExactly(
            appCacheDirectory.resolve("jib-classpath-file"),
            appCacheDirectory.resolve("jib-main-class-file"));
    assertThat(jvmArgFiles)
        .comparingElementsUsing(EXTRACTION_PATH_OF)
        .containsExactly("/app/jib-classpath-file", "/app/jib-main-class-file");

    String classpath =
        new String(
            Files.readAllBytes(appCacheDirectory.resolve("jib-classpath-file")),
            StandardCharsets.UTF_8);
    assertThat(classpath).isEqualTo("/app/resources:/app/classes:/app/libs/*");
    String mainClass =
        new String(
            Files.readAllBytes(appCacheDirectory.resolve("jib-main-class-file")),
            StandardCharsets.UTF_8);
    assertThat(mainClass).isEqualTo("could-not-infer-a-main-class");
  }

  @Test
  public void testUser()
      throws InvalidImageReferenceException, IOException, MainClassInferenceException,
          InvalidAppRootException, InvalidWorkingDirectoryException, InvalidPlatformException,
          InvalidContainerVolumeException, IncompatibleBaseImageJavaVersionException,
          NumberFormatException, InvalidContainerizingModeException,
          InvalidFilesModificationTimeException, InvalidCreationTimeException {
    when(rawConfiguration.getUser()).thenReturn(Optional.of("customUser"));

    ContainerBuildPlan buildPlan = processCommonConfiguration();
    assertThat(buildPlan.getUser()).isEqualTo("customUser");
  }

  @Test
  public void testUser_null()
      throws InvalidImageReferenceException, IOException, MainClassInferenceException,
          InvalidAppRootException, InvalidWorkingDirectoryException, InvalidPlatformException,
          InvalidContainerVolumeException, IncompatibleBaseImageJavaVersionException,
          NumberFormatException, InvalidContainerizingModeException,
          InvalidFilesModificationTimeException, InvalidCreationTimeException {
    ContainerBuildPlan buildPlan = processCommonConfiguration();
    assertThat(buildPlan.getUser()).isNull();
  }

  @Test
  public void testEntrypoint_warningOnJvmFlags()
      throws InvalidImageReferenceException, IOException, MainClassInferenceException,
          InvalidAppRootException, InvalidWorkingDirectoryException, InvalidPlatformException,
          InvalidContainerVolumeException, IncompatibleBaseImageJavaVersionException,
          NumberFormatException, InvalidContainerizingModeException,
          InvalidFilesModificationTimeException, InvalidCreationTimeException {
    when(rawConfiguration.getEntrypoint())
        .thenReturn(Optional.of(Arrays.asList("custom", "entrypoint")));
    when(rawConfiguration.getJvmFlags()).thenReturn(Collections.singletonList("jvmFlag"));

    ContainerBuildPlan buildPlan = processCommonConfiguration();

    assertThat(buildPlan.getEntrypoint()).containsExactly("custom", "entrypoint").inOrder();
    verify(projectProperties)
        .log(
            LogEvent.warn(
                "mainClass, extraClasspath, jvmFlags, and expandClasspathDependencies are ignored "
                    + "when entrypoint is specified"));
  }

  @Test
  public void testEntrypoint_warningOnMainclass()
      throws InvalidImageReferenceException, IOException, MainClassInferenceException,
          InvalidAppRootException, InvalidWorkingDirectoryException, InvalidPlatformException,
          InvalidContainerVolumeException, IncompatibleBaseImageJavaVersionException,
          NumberFormatException, InvalidContainerizingModeException,
          InvalidFilesModificationTimeException, InvalidCreationTimeException {
    when(rawConfiguration.getEntrypoint())
        .thenReturn(Optional.of(Arrays.asList("custom", "entrypoint")));
    when(rawConfiguration.getMainClass()).thenReturn(Optional.of("java.util.Object"));

    ContainerBuildPlan buildPlan = processCommonConfiguration();

    assertThat(buildPlan.getEntrypoint()).containsExactly("custom", "entrypoint").inOrder();
    verify(projectProperties)
        .log(
            LogEvent.warn(
                "mainClass, extraClasspath, jvmFlags, and expandClasspathDependencies are ignored "
                    + "when entrypoint is specified"));
  }

  @Test
  public void testEntrypoint_warningOnExpandClasspathDependencies()
      throws InvalidImageReferenceException, IOException, MainClassInferenceException,
          InvalidAppRootException, InvalidWorkingDirectoryException, InvalidPlatformException,
          InvalidContainerVolumeException, IncompatibleBaseImageJavaVersionException,
          NumberFormatException, InvalidContainerizingModeException,
          InvalidFilesModificationTimeException, InvalidCreationTimeException {
    when(rawConfiguration.getEntrypoint())
        .thenReturn(Optional.of(Arrays.asList("custom", "entrypoint")));
    when(rawConfiguration.getExpandClasspathDependencies()).thenReturn(true);

    ContainerBuildPlan buildPlan = processCommonConfiguration();

    assertThat(buildPlan.getEntrypoint()).containsExactly("custom", "entrypoint").inOrder();
    verify(projectProperties)
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
          InvalidContainerizingModeException {
    when(rawConfiguration.getMainClass()).thenReturn(Optional.of("java.util.Object"));
    when(projectProperties.isWarProject()).thenReturn(true);

    ContainerBuildPlan buildPlan = processCommonConfiguration();

    assertThat(buildPlan.getEntrypoint())
        .containsExactly("java", "-jar", "/usr/local/jetty/start.jar")
        .inOrder();
    verify(projectProperties)
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
          InvalidContainerizingModeException {
    when(rawConfiguration.getExpandClasspathDependencies()).thenReturn(true);
    when(projectProperties.isWarProject()).thenReturn(true);

    ContainerBuildPlan buildPlan = processCommonConfiguration();

    assertThat(buildPlan.getEntrypoint())
        .containsExactly("java", "-jar", "/usr/local/jetty/start.jar")
        .inOrder();
    verify(projectProperties)
        .log(
            LogEvent.warn(
                "mainClass, extraClasspath, jvmFlags, and expandClasspathDependencies "
                    + "are ignored for WAR projects"));
  }

  @Test
  public void testEntrypointClasspath_nonDefaultAppRoot()
      throws InvalidImageReferenceException, IOException, MainClassInferenceException,
          InvalidAppRootException, InvalidWorkingDirectoryException, InvalidPlatformException,
          InvalidContainerVolumeException, IncompatibleBaseImageJavaVersionException,
          NumberFormatException, InvalidContainerizingModeException,
          InvalidFilesModificationTimeException, InvalidCreationTimeException {
    when(rawConfiguration.getAppRoot()).thenReturn("/my/app");

    ContainerBuildPlan buildPlan = processCommonConfiguration();

    assertThat(buildPlan.getEntrypoint())
        .containsExactly(
            "java", "-cp", "/my/app/resources:/my/app/classes:/my/app/libs/*", "java.lang.Object")
        .inOrder();
  }

  @Test
  public void testWebAppEntrypoint_inheritedFromCustomBaseImage()
      throws InvalidImageReferenceException, IOException, MainClassInferenceException,
          InvalidAppRootException, InvalidWorkingDirectoryException, InvalidPlatformException,
          InvalidContainerVolumeException, IncompatibleBaseImageJavaVersionException,
          NumberFormatException, InvalidContainerizingModeException,
          InvalidFilesModificationTimeException, InvalidCreationTimeException {
    when(projectProperties.isWarProject()).thenReturn(true);
    when(rawConfiguration.getFromImage()).thenReturn(Optional.of("custom-base-image"));

    ContainerBuildPlan buildPlan = processCommonConfiguration();

    assertThat(buildPlan.getEntrypoint()).isNull();
  }

  @Test
  public void testGetAppRootChecked() throws InvalidAppRootException {
    when(rawConfiguration.getAppRoot()).thenReturn("/some/root");

    assertThat(PluginConfigurationProcessor.getAppRootChecked(rawConfiguration, projectProperties))
        .isEqualTo(AbsoluteUnixPath.get("/some/root"));
  }

  @Test
  public void testGetAppRootChecked_errorOnNonAbsolutePath() {
    when(rawConfiguration.getAppRoot()).thenReturn("relative/path");

    Exception exception =
        assertThrows(
            InvalidAppRootException.class,
            () ->
                PluginConfigurationProcessor.getAppRootChecked(
                    rawConfiguration, projectProperties));
    assertThat(exception).hasMessageThat().isEqualTo("relative/path");
  }

  @Test
  public void testGetAppRootChecked_errorOnWindowsPath() {
    when(rawConfiguration.getAppRoot()).thenReturn("\\windows\\path");

    Exception exception =
        assertThrows(
            InvalidAppRootException.class,
            () ->
                PluginConfigurationProcessor.getAppRootChecked(
                    rawConfiguration, projectProperties));
    assertThat(exception).hasMessageThat().isEqualTo("\\windows\\path");
  }

  @Test
  public void testGetAppRootChecked_errorOnWindowsPathWithDriveLetter() {
    when(rawConfiguration.getAppRoot()).thenReturn("C:\\windows\\path");

    Exception exception =
        assertThrows(
            InvalidAppRootException.class,
            () ->
                PluginConfigurationProcessor.getAppRootChecked(
                    rawConfiguration, projectProperties));
    assertThat(exception).hasMessageThat().isEqualTo("C:\\windows\\path");
  }

  @Test
  public void testGetAppRootChecked_defaultNonWarProject() throws InvalidAppRootException {
    when(rawConfiguration.getAppRoot()).thenReturn("");
    when(projectProperties.isWarProject()).thenReturn(false);

    assertThat(PluginConfigurationProcessor.getAppRootChecked(rawConfiguration, projectProperties))
        .isEqualTo(AbsoluteUnixPath.get("/app"));
  }

  @Test
  public void testGetAppRootChecked_defaultWarProject() throws InvalidAppRootException {
    when(rawConfiguration.getAppRoot()).thenReturn("");
    when(projectProperties.isWarProject()).thenReturn(true);

    assertThat(PluginConfigurationProcessor.getAppRootChecked(rawConfiguration, projectProperties))
        .isEqualTo(AbsoluteUnixPath.get("/var/lib/jetty/webapps/ROOT"));
  }

  @Test
  public void testGetContainerizingModeChecked_packagedWithWar() {
    when(rawConfiguration.getContainerizingMode()).thenReturn("packaged");
    when(projectProperties.isWarProject()).thenReturn(true);

    Exception exception =
        assertThrows(
            UnsupportedOperationException.class,
            () ->
                PluginConfigurationProcessor.getContainerizingModeChecked(
                    rawConfiguration, projectProperties));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo("packaged containerizing mode for WAR is not yet supported");
  }

  @Test
  public void testGetWorkingDirectoryChecked() throws InvalidWorkingDirectoryException {
    when(rawConfiguration.getWorkingDirectory()).thenReturn(Optional.of("/valid/path"));

    Optional<AbsoluteUnixPath> checkedPath =
        PluginConfigurationProcessor.getWorkingDirectoryChecked(rawConfiguration);
    assertThat(checkedPath).hasValue(AbsoluteUnixPath.get("/valid/path"));
  }

  @Test
  public void testGetWorkingDirectoryChecked_undefined() throws InvalidWorkingDirectoryException {
    when(rawConfiguration.getWorkingDirectory()).thenReturn(Optional.empty());
    assertThat(PluginConfigurationProcessor.getWorkingDirectoryChecked(rawConfiguration)).isEmpty();
  }

  @Test
  public void testGetWorkingDirectoryChecked_notAbsolute() {
    when(rawConfiguration.getWorkingDirectory()).thenReturn(Optional.of("relative/path"));

    InvalidWorkingDirectoryException exception =
        assertThrows(
            InvalidWorkingDirectoryException.class,
            () -> PluginConfigurationProcessor.getWorkingDirectoryChecked(rawConfiguration));
    assertThat(exception).hasMessageThat().isEqualTo("relative/path");
    assertThat(exception.getInvalidPathValue()).isEqualTo("relative/path");
  }

  @Test
  public void testGetDefaultBaseImage_nonWarPackaging()
      throws IncompatibleBaseImageJavaVersionException {
    when(projectProperties.isWarProject()).thenReturn(false);

    assertThat(PluginConfigurationProcessor.getDefaultBaseImage(projectProperties))
        .isEqualTo("eclipse-temurin:8-jre");
  }

  @Test
  public void testGetDefaultBaseImage_warProject()
      throws IncompatibleBaseImageJavaVersionException {
    when(projectProperties.isWarProject()).thenReturn(true);

    assertThat(PluginConfigurationProcessor.getDefaultBaseImage(projectProperties))
        .isEqualTo("jetty");
  }

  @Test
  @Parameters(
      value = {
        "6, eclipse-temurin:8-jre",
        "8, eclipse-temurin:8-jre",
        "9, eclipse-temurin:11-jre",
        "11, eclipse-temurin:11-jre",
        "13, eclipse-temurin:17-jre",
        "17, eclipse-temurin:17-jre"
      })
  public void testGetDefaultBaseImage_defaultJavaBaseImage(
      int javaVersion, String expectedBaseImage) throws IncompatibleBaseImageJavaVersionException {
    when(projectProperties.getMajorJavaVersion()).thenReturn(javaVersion);
    assertThat(PluginConfigurationProcessor.getDefaultBaseImage(projectProperties))
        .isEqualTo(expectedBaseImage);
  }

  @Test
  public void testGetDefaultBaseImage_projectHigherThanJava17() {
    when(projectProperties.getMajorJavaVersion()).thenReturn(20);

    IncompatibleBaseImageJavaVersionException exception =
        assertThrows(
            IncompatibleBaseImageJavaVersionException.class,
            () -> PluginConfigurationProcessor.getDefaultBaseImage(projectProperties));

    assertThat(exception.getBaseImageMajorJavaVersion()).isEqualTo(17);
    assertThat(exception.getProjectMajorJavaVersion()).isEqualTo(20);
  }

  @Test
  public void testGetJavaContainerBuilderWithBaseImage_dockerBase()
      throws IncompatibleBaseImageJavaVersionException, IOException, InvalidImageReferenceException,
          CacheDirectoryCreationException {
    when(rawConfiguration.getFromImage()).thenReturn(Optional.of("docker://ima.ge/name"));
    ImageConfiguration result = getCommonImageConfiguration();
    assertThat(result.getImage().toString()).isEqualTo("ima.ge/name");
    assertThat(result.getDockerClient()).isPresent();
    assertThat(result.getTarPath()).isEmpty();
  }

  @Test
  public void testGetJavaContainerBuilderWithBaseImage_tarBase()
      throws IncompatibleBaseImageJavaVersionException, IOException, InvalidImageReferenceException,
          CacheDirectoryCreationException {
    when(rawConfiguration.getFromImage()).thenReturn(Optional.of("tar:///path/to.tar"));
    ImageConfiguration result = getCommonImageConfiguration();
    assertThat(result.getTarPath()).hasValue(Paths.get("/path/to.tar"));
    assertThat(result.getDockerClient()).isEmpty();
  }

  @Test
  public void testGetJavaContainerBuilderWithBaseImage_registry()
      throws IncompatibleBaseImageJavaVersionException, InvalidImageReferenceException, IOException,
          CacheDirectoryCreationException {
    when(rawConfiguration.getFromImage()).thenReturn(Optional.of("ima.ge/name"));
    ImageConfiguration result = getCommonImageConfiguration();
    assertThat(result.getImage().toString()).isEqualTo("ima.ge/name");
    assertThat(result.getDockerClient()).isEmpty();
    assertThat(result.getTarPath()).isEmpty();
  }

  @Test
  public void testGetJavaContainerBuilderWithBaseImage_registryWithPrefix()
      throws IncompatibleBaseImageJavaVersionException, InvalidImageReferenceException, IOException,
          CacheDirectoryCreationException {
    when(rawConfiguration.getFromImage()).thenReturn(Optional.of("registry://ima.ge/name"));
    ImageConfiguration result = getCommonImageConfiguration();
    assertThat(result.getImage().toString()).isEqualTo("ima.ge/name");
    assertThat(result.getDockerClient()).isEmpty();
    assertThat(result.getTarPath()).isEmpty();
  }

  @Test
  @Parameters(
      value = {
        "adoptopenjdk:8, 8, 11",
        "adoptopenjdk:8-jre, 8, 11",
        "eclipse-temurin:8, 8, 11",
        "eclipse-temurin:8-jre, 8, 11",
        "adoptopenjdk:11, 11, 15",
        "adoptopenjdk:11-jre, 11, 15",
        "eclipse-temurin:11, 11, 15",
        "eclipse-temurin:11-jre, 11, 15",
        "eclipse-temurin:17, 17, 19",
        "eclipse-temurin:17-jre, 17, 19"
      })
  public void testGetJavaContainerBuilderWithBaseImage_incompatibleJavaBaseImage(
      String baseImage, int baseImageJavaVersion, int appJavaVersion) {
    when(projectProperties.getMajorJavaVersion()).thenReturn(appJavaVersion);

    when(rawConfiguration.getFromImage()).thenReturn(Optional.of(baseImage));
    IncompatibleBaseImageJavaVersionException exception =
        assertThrows(
            IncompatibleBaseImageJavaVersionException.class,
            () ->
                PluginConfigurationProcessor.getJavaContainerBuilderWithBaseImage(
                    rawConfiguration, projectProperties, inferredAuthProvider));
    assertThat(exception.getBaseImageMajorJavaVersion()).isEqualTo(baseImageJavaVersion);
    assertThat(exception.getProjectMajorJavaVersion()).isEqualTo(appJavaVersion);
  }

  // https://github.com/GoogleContainerTools/jib/issues/1995
  @Test
  public void testGetJavaContainerBuilderWithBaseImage_java12BaseImage()
      throws InvalidImageReferenceException, IOException, IncompatibleBaseImageJavaVersionException,
          CacheDirectoryCreationException {
    when(projectProperties.getMajorJavaVersion()).thenReturn(12);
    when(rawConfiguration.getFromImage()).thenReturn(Optional.of("regis.try/java12image"));
    ImageConfiguration imageConfiguration = getCommonImageConfiguration();
    assertThat(imageConfiguration.getImageRegistry()).isEqualTo("regis.try");
    assertThat(imageConfiguration.getImageRepository()).isEqualTo("java12image");
  }

  @Test
  public void testGetJavaContainerBuilderWithBaseImage_java19NoBaseImage() {
    when(projectProperties.getMajorJavaVersion()).thenReturn(19);
    when(rawConfiguration.getFromImage()).thenReturn(Optional.empty());
    IncompatibleBaseImageJavaVersionException exception =
        assertThrows(
            IncompatibleBaseImageJavaVersionException.class,
            () ->
                PluginConfigurationProcessor.getJavaContainerBuilderWithBaseImage(
                    rawConfiguration, projectProperties, inferredAuthProvider));
    assertThat(exception.getBaseImageMajorJavaVersion()).isEqualTo(17);
    assertThat(exception.getProjectMajorJavaVersion()).isEqualTo(19);
  }

  @Test
  public void testGetPlatformsSet() throws InvalidPlatformException {
    Mockito.<List<?>>when(rawConfiguration.getPlatforms())
        .thenReturn(Arrays.asList(new TestPlatformConfiguration("testArchitecture", "testOs")));

    assertThat(PluginConfigurationProcessor.getPlatformsSet(rawConfiguration))
        .containsExactly(new Platform("testArchitecture", "testOs"));
  }

  @Test
  public void testGetPlatformsSet_architectureMissing() {
    TestPlatformConfiguration platform = new TestPlatformConfiguration(null, "testOs");
    Mockito.<List<?>>when(rawConfiguration.getPlatforms()).thenReturn(Arrays.asList(platform));

    InvalidPlatformException exception =
        assertThrows(
            InvalidPlatformException.class,
            () -> PluginConfigurationProcessor.getPlatformsSet(rawConfiguration));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo("platform configuration is missing an architecture value");
    assertThat(exception.getInvalidPlatform()).isEqualTo("architecture=<missing>, os=testOs");
  }

  @Test
  public void testGetPlatformsSet_osMissing() {
    TestPlatformConfiguration platform = new TestPlatformConfiguration("testArchitecture", null);
    Mockito.<List<?>>when(rawConfiguration.getPlatforms()).thenReturn(Arrays.asList(platform));

    InvalidPlatformException exception =
        assertThrows(
            InvalidPlatformException.class,
            () -> PluginConfigurationProcessor.getPlatformsSet(rawConfiguration));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo("platform configuration is missing an OS value");
    assertThat(exception.getInvalidPlatform())
        .isEqualTo("architecture=testArchitecture, os=<missing>");
  }

  @Test
  public void testGetValidVolumesList() throws InvalidContainerVolumeException {
    when(rawConfiguration.getVolumes()).thenReturn(Collections.singletonList("/some/root"));

    assertThat(PluginConfigurationProcessor.getVolumesSet(rawConfiguration))
        .containsExactly(AbsoluteUnixPath.get("/some/root"));
  }

  @Test
  public void testGetInvalidVolumesList() {
    when(rawConfiguration.getVolumes()).thenReturn(Collections.singletonList("`some/root"));

    InvalidContainerVolumeException exception =
        assertThrows(
            InvalidContainerVolumeException.class,
            () -> PluginConfigurationProcessor.getVolumesSet(rawConfiguration));
    assertThat(exception).hasMessageThat().isEqualTo("`some/root");
    assertThat(exception.getInvalidVolume()).isEqualTo("`some/root");
  }

  @Test
  public void testCreateModificationTimeProvider_epochPlusSecond()
      throws InvalidFilesModificationTimeException {
    ModificationTimeProvider timeProvider =
        PluginConfigurationProcessor.createModificationTimeProvider("EPOCH_PLUS_SECOND");
    assertThat(timeProvider.get(Paths.get("foo"), AbsoluteUnixPath.get("/bar")))
        .isEqualTo(Instant.ofEpochSecond(1));
  }

  @Test
  public void testCreateModificationTimeProvider_isoDateTimeValue()
      throws InvalidFilesModificationTimeException {
    ModificationTimeProvider timeProvider =
        PluginConfigurationProcessor.createModificationTimeProvider("2011-12-03T10:15:30+09:00");
    Instant expected = DateTimeFormatter.ISO_DATE_TIME.parse("2011-12-03T01:15:30Z", Instant::from);
    assertThat(timeProvider.get(Paths.get("foo"), AbsoluteUnixPath.get("/bar")))
        .isEqualTo(expected);
  }

  @Test
  public void testCreateModificationTimeProvider_invalidValue() {
    InvalidFilesModificationTimeException exception =
        assertThrows(
            InvalidFilesModificationTimeException.class,
            () -> PluginConfigurationProcessor.createModificationTimeProvider("invalid format"));
    assertThat(exception).hasMessageThat().isEqualTo("invalid format");
    assertThat(exception.getInvalidFilesModificationTime()).isEqualTo("invalid format");
  }

  @Test
  public void testGetCreationTime_epoch() throws InvalidCreationTimeException {
    Instant time = PluginConfigurationProcessor.getCreationTime("EPOCH", projectProperties);
    assertThat(time).isEqualTo(Instant.EPOCH);
  }

  @Test
  public void testGetCreationTime_useCurrentTimestamp() throws InvalidCreationTimeException {
    Instant now = Instant.now().minusSeconds(2);
    Instant time =
        PluginConfigurationProcessor.getCreationTime("USE_CURRENT_TIMESTAMP", projectProperties);
    assertThat(time).isGreaterThan(now);
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
      assertThat(time).isEqualTo(expected);
    }
  }

  @Test
  public void testGetCreationTime_isoDateTimeValueTimeZoneRegionOnlyAllowedForMostStrict8601Mode() {
    List<String> invalidTimeStamps =
        ImmutableList.of(
            "2011-12-03T01:15:30+0900[Asia/Tokyo]", "2011-12-03T01:15:30+09[Asia/Tokyo]");
    for (String timeString : invalidTimeStamps) {
      // getCreationTime should fail if region specified when zone not in HH:MM mode.
      // This is the expected behavior, not specifically designed like this for any reason, feel
      // free to change this behavior and update the test
      assertThrows(
          InvalidCreationTimeException.class,
          () -> PluginConfigurationProcessor.getCreationTime(timeString, projectProperties));
    }
  }

  @Test
  public void testGetCreationTime_isoDateTimeValueRequiresTimeZone() {
    // getCreationTime should fail if timezone not specified.
    // this is the expected behavior, not specifically designed like this for any reason, feel
    // free to change this behavior and update the test
    assertThrows(
        InvalidCreationTimeException.class,
        () ->
            PluginConfigurationProcessor.getCreationTime("2011-12-03T01:15:30", projectProperties));
  }

  @Test
  public void testGetCreationTime_invalidValue() {
    InvalidCreationTimeException exception =
        assertThrows(
            InvalidCreationTimeException.class,
            () ->
                PluginConfigurationProcessor.getCreationTime("invalid format", projectProperties));
    assertThat(exception).hasMessageThat().isEqualTo("invalid format");
    assertThat(exception.getInvalidCreationTime()).isEqualTo("invalid format");
  }

  private ImageConfiguration getCommonImageConfiguration()
      throws IncompatibleBaseImageJavaVersionException, IOException, InvalidImageReferenceException,
          CacheDirectoryCreationException {
    JibContainerBuilder containerBuilder =
        PluginConfigurationProcessor.getJavaContainerBuilderWithBaseImage(
                rawConfiguration, projectProperties, inferredAuthProvider)
            .addClasses(temporaryFolder.getRoot().toPath())
            .toContainerBuilder();
    return JibContainerBuilderTestHelper.toBuildContext(
            containerBuilder, Containerizer.to(RegistryImage.named("ignored")))
        .getBaseImageConfiguration();
  }

  private ContainerBuildPlan processCommonConfiguration()
      throws InvalidImageReferenceException, MainClassInferenceException, InvalidAppRootException,
          IOException, InvalidWorkingDirectoryException, InvalidPlatformException,
          InvalidContainerVolumeException, IncompatibleBaseImageJavaVersionException,
          NumberFormatException, InvalidContainerizingModeException,
          InvalidFilesModificationTimeException, InvalidCreationTimeException {
    JibContainerBuilder containerBuilder =
        PluginConfigurationProcessor.processCommonConfiguration(
            rawConfiguration, ignored -> Optional.empty(), projectProperties, containerizer);
    return containerBuilder.toContainerBuildPlan();
  }

  @Test
  public void getAllFiles_expandsDirectories() throws IOException {
    File rootFile = temporaryFolder.newFile("file");
    File folder = temporaryFolder.newFolder("folder");
    File folderFile = temporaryFolder.newFile("folder/file2");
    assertThat(
            PluginConfigurationProcessor.getAllFiles(
                ImmutableSet.of(rootFile.toPath(), folder.toPath())))
        .containsExactly(rootFile.toPath().toAbsolutePath(), folderFile.toPath().toAbsolutePath());
  }

  @Test
  public void getAllFiles_doesntBreakForNonExistentFiles() throws IOException {
    Path testPath = Paths.get("/a/file/that/doesnt/exist");
    assertThat(Files.exists(testPath)).isFalse();
    assertThat(PluginConfigurationProcessor.getAllFiles(ImmutableSet.of(testPath))).isEmpty();
  }
}
