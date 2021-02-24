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

package com.google.cloud.tools.jib.gradle;

import com.google.cloud.tools.jib.api.CacheDirectoryCreationException;
import com.google.cloud.tools.jib.api.Containerizer;
import com.google.cloud.tools.jib.api.InvalidImageReferenceException;
import com.google.cloud.tools.jib.api.JavaContainerBuilder;
import com.google.cloud.tools.jib.api.JavaContainerBuilder.LayerType;
import com.google.cloud.tools.jib.api.JibContainerBuilder;
import com.google.cloud.tools.jib.api.JibContainerBuilderTestHelper;
import com.google.cloud.tools.jib.api.RegistryImage;
import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.buildplan.FileEntriesLayer;
import com.google.cloud.tools.jib.api.buildplan.FileEntry;
import com.google.cloud.tools.jib.api.buildplan.FilePermissions;
import com.google.cloud.tools.jib.configuration.BuildContext;
import com.google.cloud.tools.jib.filesystem.DirectoryWalker;
import com.google.cloud.tools.jib.filesystem.TempDirectoryProvider;
import com.google.cloud.tools.jib.gradle.extension.JibGradlePluginExtension;
import com.google.cloud.tools.jib.plugins.common.ContainerizingMode;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.ByteStreams;
import com.google.common.io.Resources;
import groovy.lang.Closure;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.gradle.api.Action;
import org.gradle.api.JavaVersion;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.ResolvedConfiguration;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.internal.file.AbstractFileCollection;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.java.archives.internal.DefaultManifest;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.configuration.ConsoleOutput;
import org.gradle.api.plugins.Convention;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.plugins.WarPlugin;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.SourceSetOutput;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.util.PatternFilterable;
import org.gradle.jvm.tasks.Jar;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/** Test for {@link GradleProjectProperties}. */
@RunWith(MockitoJUnitRunner.class)
public class GradleProjectPropertiesTest {

  private static final Instant SAMPLE_FILE_MODIFICATION_TIME = Instant.ofEpochSecond(32);

  /** Implementation of {@link FileCollection} that just holds a set of {@link File}s. */
  private static class TestFileCollection extends AbstractFileCollection {

    private final Set<File> files = new LinkedHashSet<>();

    private TestFileCollection(Set<Path> files) {
      for (Path file : files) {
        this.files.add(file.toFile());
      }
    }

    @Override
    public String getDisplayName() {
      return null;
    }

    @Override
    public Set<File> getFiles() {
      return files;
    }
  }

  /** Helper for reading back layers in a {@link BuildContext}. */
  private static class ContainerBuilderLayers {

    private final List<FileEntriesLayer> resourcesLayerEntries;
    private final List<FileEntriesLayer> classesLayerEntries;
    private final List<FileEntriesLayer> dependenciesLayerEntries;
    private final List<FileEntriesLayer> snapshotsLayerEntries;

    private ContainerBuilderLayers(BuildContext buildContext) {
      resourcesLayerEntries =
          getLayerConfigurationsByName(buildContext, LayerType.RESOURCES.getName());
      classesLayerEntries = getLayerConfigurationsByName(buildContext, LayerType.CLASSES.getName());
      dependenciesLayerEntries =
          getLayerConfigurationsByName(buildContext, LayerType.DEPENDENCIES.getName());
      snapshotsLayerEntries =
          getLayerConfigurationsByName(buildContext, LayerType.SNAPSHOT_DEPENDENCIES.getName());
    }
  }

  private static List<FileEntriesLayer> getLayerConfigurationsByName(
      BuildContext buildContext, String name) {
    return buildContext
        .getLayerConfigurations()
        .stream()
        .filter(layer -> layer.getName().equals(name))
        .collect(Collectors.toList());
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

  private static void assertModificationTime(Instant instant, List<FileEntriesLayer> layers) {
    for (FileEntriesLayer layer : layers) {
      for (FileEntry entry : layer.getEntries()) {
        String message = "wrong time: " + entry.getSourceFile() + "-->" + entry.getExtractionPath();
        Assert.assertEquals(message, instant, entry.getModificationTime());
      }
    }
  }

  private static Path getResource(String path) throws URISyntaxException {
    return Paths.get(Resources.getResource(path).toURI());
  }

  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Mock private TempDirectoryProvider mockTempDirectoryProvider;
  @Mock private Supplier<List<JibGradlePluginExtension<?>>> mockExtensionLoader;
  @Mock private FileResolver mockFileResolver;
  @Mock private Convention mockConvention;
  @Mock private ConfigurationContainer mockConfigurationContainer;
  @Mock private Configuration mockConfiguration;
  @Mock private ResolvedConfiguration mockResolvedConfiguration;
  @Mock private TaskContainer mockTaskContainer;
  @Mock private Logger mockLogger;
  @Mock private JavaPluginConvention mockJavaPluginConvention;
  @Mock private SourceSetContainer mockSourceSetContainer;
  @Mock private SourceSet mockMainSourceSet;
  @Mock private SourceSetOutput mockMainSourceSetOutput;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private Project mockProject;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private TaskProvider<Task> mockWarTaskProvider;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private TaskProvider<Task> mockBootWarTaskProvider;

  private GradleProjectProperties gradleProjectProperties;

  @Before
  public void setUp() throws URISyntaxException, IOException {
    Mockito.when(mockLogger.isDebugEnabled()).thenReturn(true);
    Mockito.when(mockLogger.isInfoEnabled()).thenReturn(true);
    Mockito.when(mockLogger.isWarnEnabled()).thenReturn(true);
    Mockito.when(mockLogger.isErrorEnabled()).thenReturn(true);

    Mockito.when(mockProject.getConvention()).thenReturn(mockConvention);
    Mockito.when(mockConvention.getPlugin(JavaPluginConvention.class))
        .thenReturn(mockJavaPluginConvention);
    Mockito.when(mockJavaPluginConvention.getSourceSets()).thenReturn(mockSourceSetContainer);
    Mockito.when(mockProject.getConfigurations()).thenReturn(mockConfigurationContainer);
    Mockito.when(mockProject.getTasks()).thenReturn(mockTaskContainer);
    Mockito.when(mockProject.getGradle().getStartParameter().getConsoleOutput())
        .thenReturn(ConsoleOutput.Plain);

    // mocking to complete ignore project dependency resolution
    Mockito.when(mockResolvedConfiguration.getResolvedArtifacts()).thenReturn(ImmutableSet.of());
    ConfigurableFileCollection emptyFileCollection = Mockito.mock(ConfigurableFileCollection.class);
    Mockito.when(emptyFileCollection.getFiles()).thenReturn(ImmutableSet.of());
    Mockito.when(mockProject.files(ImmutableList.of())).thenReturn(emptyFileCollection);
    // done mocking project dependency resolution

    Set<Path> classesFiles = ImmutableSet.of(getResource("gradle/application/classes"));
    FileCollection classesFileCollection = new TestFileCollection(classesFiles);
    Path resourcesOutputDir = getResource("gradle/application/resources");

    Set<Path> allFiles = new LinkedHashSet<>(classesFiles);
    allFiles.add(resourcesOutputDir);
    allFiles.add(getResource("gradle/application/dependencies/library.jarC.jar"));
    allFiles.add(getResource("gradle/application/dependencies/libraryB.jar"));
    allFiles.add(getResource("gradle/application/dependencies/libraryA.jar"));
    allFiles.add(getResource("gradle/application/dependencies/dependency-1.0.0.jar"));
    allFiles.add(getResource("gradle/application/dependencies/more/dependency-1.0.0.jar"));
    allFiles.add(getResource("gradle/application/dependencies/another/one/dependency-1.0.0.jar"));
    allFiles.add(getResource("gradle/application/dependencies/dependencyX-1.0.0-SNAPSHOT.jar"));

    Mockito.when(mockSourceSetContainer.getByName("main")).thenReturn(mockMainSourceSet);
    Mockito.when(mockMainSourceSet.getOutput()).thenReturn(mockMainSourceSetOutput);
    Mockito.when(mockMainSourceSetOutput.getClassesDirs()).thenReturn(classesFileCollection);
    Mockito.when(mockMainSourceSetOutput.getResourcesDir()).thenReturn(resourcesOutputDir.toFile());
    Mockito.when(
            mockConfigurationContainer.getByName(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME))
        .thenReturn(mockConfiguration);
    Mockito.when(mockConfiguration.getResolvedConfiguration())
        .thenReturn(mockResolvedConfiguration);
    // TODO: Some how fix this
    // Mockito.when(mockConfiguration.forEach()).thenReturn(something with allFiles);
    // We can't commit an empty directory in Git, so create (if not exist).
    Path emptyDirectory = getResource("gradle/webapp").resolve("WEB-INF/classes/empty_dir");
    Files.createDirectories(emptyDirectory);

    gradleProjectProperties =
        new GradleProjectProperties(
            mockProject,
            mockLogger,
            mockTempDirectoryProvider,
            mockExtensionLoader,
            JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME);
  }

  @Test
  public void testGetMainClassFromJar_success() {
    DefaultManifest manifest = new DefaultManifest(mockFileResolver);
    manifest.attributes(ImmutableMap.of("Main-Class", "some.main.class"));
    Jar mockJar = Mockito.mock(Jar.class);
    Mockito.when(mockJar.getManifest()).thenReturn(manifest);
    Mockito.when(mockTaskContainer.findByName("jar")).thenReturn(mockJar);
    Assert.assertEquals("some.main.class", gradleProjectProperties.getMainClassFromJarPlugin());
  }

  @Test
  public void testGetMainClassFromJar_missing() {
    Mockito.when(mockTaskContainer.findByName("jar")).thenReturn(null);
    Assert.assertNull(gradleProjectProperties.getMainClassFromJarPlugin());
  }

  @Test
  public void testIsWarProject() {
    Mockito.when(mockProject.getPlugins().hasPlugin(WarPlugin.class)).thenReturn(true);
    Assert.assertTrue(gradleProjectProperties.isWarProject());
  }

  @Test
  public void testConvertPermissionsMap() {
    Assert.assertEquals(
        ImmutableMap.of(
            "/test/folder/file1",
            FilePermissions.fromOctalString("123"),
            "/test/file2",
            FilePermissions.fromOctalString("456")),
        TaskCommon.convertPermissionsMap(
            ImmutableMap.of("/test/folder/file1", "123", "/test/file2", "456")));

    try {
      TaskCommon.convertPermissionsMap(ImmutableMap.of("a path", "not valid permission"));
      Assert.fail();
    } catch (IllegalArgumentException ignored) {
      // pass
    }
  }

  @Test
  public void testGetMajorJavaVersion() {
    Mockito.when(mockConvention.findPlugin(JavaPluginConvention.class))
        .thenReturn(mockJavaPluginConvention);

    Mockito.when(mockJavaPluginConvention.getTargetCompatibility())
        .thenReturn(JavaVersion.VERSION_1_3);
    Assert.assertEquals(3, gradleProjectProperties.getMajorJavaVersion());

    Mockito.when(mockJavaPluginConvention.getTargetCompatibility())
        .thenReturn(JavaVersion.VERSION_11);
    Assert.assertEquals(11, gradleProjectProperties.getMajorJavaVersion());

    Mockito.when(mockJavaPluginConvention.getTargetCompatibility())
        .thenReturn(JavaVersion.VERSION_1_9);
    Assert.assertEquals(9, gradleProjectProperties.getMajorJavaVersion());
  }

  @Test
  public void testGetMajorJavaVersion_jvm8() {
    Assume.assumeThat(JavaVersion.current(), CoreMatchers.is(JavaVersion.VERSION_1_8));

    Assert.assertEquals(8, gradleProjectProperties.getMajorJavaVersion());
  }

  @Test
  public void testGetMajorJavaVersion_jvm11() {
    Assume.assumeThat(JavaVersion.current(), CoreMatchers.is(JavaVersion.VERSION_11));

    Assert.assertEquals(11, gradleProjectProperties.getMajorJavaVersion());
  }

  @Test
  public void testCreateContainerBuilder_correctFiles()
      throws URISyntaxException, InvalidImageReferenceException, CacheDirectoryCreationException {
    BuildContext buildContext = setupBuildContext("/app");
    ContainerBuilderLayers layers = new ContainerBuilderLayers(buildContext);

    Path applicationDirectory = getResource("gradle/application");
    assertSourcePathsUnordered(
        ImmutableList.of(
            applicationDirectory.resolve("dependencies/dependencyX-1.0.0-SNAPSHOT.jar")),
        layers.snapshotsLayerEntries.get(0).getEntries());
    assertSourcePathsUnordered(
        ImmutableList.of(
            applicationDirectory.resolve("dependencies/dependency-1.0.0.jar"),
            applicationDirectory.resolve("dependencies/more/dependency-1.0.0.jar"),
            applicationDirectory.resolve("dependencies/another/one/dependency-1.0.0.jar"),
            applicationDirectory.resolve("dependencies/libraryA.jar"),
            applicationDirectory.resolve("dependencies/libraryB.jar"),
            applicationDirectory.resolve("dependencies/library.jarC.jar")),
        layers.dependenciesLayerEntries.get(0).getEntries());
    assertSourcePathsUnordered(
        ImmutableList.of(
            applicationDirectory.resolve("resources/resourceA"),
            applicationDirectory.resolve("resources/resourceB"),
            applicationDirectory.resolve("resources/world")),
        layers.resourcesLayerEntries.get(0).getEntries());
    assertSourcePathsUnordered(
        ImmutableList.of(
            applicationDirectory.resolve("classes/HelloWorld.class"),
            applicationDirectory.resolve("classes/some.class")),
        layers.classesLayerEntries.get(0).getEntries());

    assertModificationTime(SAMPLE_FILE_MODIFICATION_TIME, layers.snapshotsLayerEntries);
    assertModificationTime(SAMPLE_FILE_MODIFICATION_TIME, layers.dependenciesLayerEntries);
    assertModificationTime(SAMPLE_FILE_MODIFICATION_TIME, layers.resourcesLayerEntries);
    assertModificationTime(SAMPLE_FILE_MODIFICATION_TIME, layers.classesLayerEntries);
  }

  @Test
  public void testCreateContainerBuilder_noClassesFiles() throws InvalidImageReferenceException {
    Path nonexistentFile = Paths.get("/nonexistent/file");
    Mockito.when(mockMainSourceSetOutput.getClassesDirs())
        .thenReturn(new TestFileCollection(ImmutableSet.of(nonexistentFile)));
    gradleProjectProperties.createJibContainerBuilder(
        JavaContainerBuilder.from(RegistryImage.named("base")), ContainerizingMode.EXPLODED);
    gradleProjectProperties.waitForLoggingThread();
    Mockito.verify(mockLogger).warn("No classes files were found - did you compile your project?");
  }

  @Test
  public void testCreateContainerBuilder_nonDefaultAppRoot()
      throws InvalidImageReferenceException, CacheDirectoryCreationException {
    BuildContext buildContext = setupBuildContext("/my/app");
    ContainerBuilderLayers layers = new ContainerBuilderLayers(buildContext);

    assertExtractionPathsUnordered(
        Arrays.asList(
            "/my/app/libs/dependency-1.0.0-770.jar",
            "/my/app/libs/dependency-1.0.0-200.jar",
            "/my/app/libs/dependency-1.0.0-480.jar",
            "/my/app/libs/libraryA.jar",
            "/my/app/libs/libraryB.jar",
            "/my/app/libs/library.jarC.jar"),
        layers.dependenciesLayerEntries.get(0).getEntries());
    assertExtractionPathsUnordered(
        Collections.singletonList("/my/app/libs/dependencyX-1.0.0-SNAPSHOT.jar"),
        layers.snapshotsLayerEntries.get(0).getEntries());
    assertExtractionPathsUnordered(
        Arrays.asList(
            "/my/app/resources/resourceA",
            "/my/app/resources/resourceB",
            "/my/app/resources/world"),
        layers.resourcesLayerEntries.get(0).getEntries());
    assertExtractionPathsUnordered(
        Arrays.asList("/my/app/classes/HelloWorld.class", "/my/app/classes/some.class"),
        layers.classesLayerEntries.get(0).getEntries());
  }

  @Test
  public void testCreateContainerBuilder_defaultAppRoot()
      throws InvalidImageReferenceException, CacheDirectoryCreationException {
    BuildContext buildContext = setupBuildContext(JavaContainerBuilder.DEFAULT_APP_ROOT);
    ContainerBuilderLayers layers = new ContainerBuilderLayers(buildContext);
    assertExtractionPathsUnordered(
        Arrays.asList(
            "/app/libs/dependency-1.0.0-770.jar",
            "/app/libs/dependency-1.0.0-200.jar",
            "/app/libs/dependency-1.0.0-480.jar",
            "/app/libs/libraryA.jar",
            "/app/libs/libraryB.jar",
            "/app/libs/library.jarC.jar"),
        layers.dependenciesLayerEntries.get(0).getEntries());
    assertExtractionPathsUnordered(
        Collections.singletonList("/app/libs/dependencyX-1.0.0-SNAPSHOT.jar"),
        layers.snapshotsLayerEntries.get(0).getEntries());
    assertExtractionPathsUnordered(
        Arrays.asList(
            "/app/resources/resourceA", "/app/resources/resourceB", "/app/resources/world"),
        layers.resourcesLayerEntries.get(0).getEntries());
    assertExtractionPathsUnordered(
        Arrays.asList("/app/classes/HelloWorld.class", "/app/classes/some.class"),
        layers.classesLayerEntries.get(0).getEntries());
  }

  @Test
  public void testCreateContainerBuilder_war()
      throws URISyntaxException, IOException, InvalidImageReferenceException,
          CacheDirectoryCreationException {
    Path webAppDirectory = getResource("gradle/webapp");
    Path unzipTarget = setUpWarProject(webAppDirectory);

    BuildContext buildContext = setupBuildContext("/my/app");
    ContainerBuilderLayers layers = new ContainerBuilderLayers(buildContext);
    assertSourcePathsUnordered(
        ImmutableList.of(unzipTarget.resolve("WEB-INF/lib/dependency-1.0.0.jar")),
        layers.dependenciesLayerEntries.get(0).getEntries());
    assertSourcePathsUnordered(
        ImmutableList.of(unzipTarget.resolve("WEB-INF/lib/dependencyX-1.0.0-SNAPSHOT.jar")),
        layers.snapshotsLayerEntries.get(0).getEntries());
    assertSourcePathsUnordered(
        ImmutableList.of(
            unzipTarget.resolve("META-INF"),
            unzipTarget.resolve("META-INF/context.xml"),
            unzipTarget.resolve("Test.jsp"),
            unzipTarget.resolve("WEB-INF"),
            unzipTarget.resolve("WEB-INF/classes"),
            unzipTarget.resolve("WEB-INF/classes/empty_dir"),
            unzipTarget.resolve("WEB-INF/classes/package"),
            unzipTarget.resolve("WEB-INF/classes/package/test.properties"),
            unzipTarget.resolve("WEB-INF/lib"),
            unzipTarget.resolve("WEB-INF/web.xml")),
        layers.resourcesLayerEntries.get(0).getEntries());
    assertSourcePathsUnordered(
        ImmutableList.of(
            unzipTarget.resolve("WEB-INF/classes/HelloWorld.class"),
            unzipTarget.resolve("WEB-INF/classes/empty_dir"),
            unzipTarget.resolve("WEB-INF/classes/package"),
            unzipTarget.resolve("WEB-INF/classes/package/Other.class")),
        layers.classesLayerEntries.get(0).getEntries());

    assertExtractionPathsUnordered(
        Collections.singletonList("/my/app/WEB-INF/lib/dependency-1.0.0.jar"),
        layers.dependenciesLayerEntries.get(0).getEntries());
    assertExtractionPathsUnordered(
        Collections.singletonList("/my/app/WEB-INF/lib/dependencyX-1.0.0-SNAPSHOT.jar"),
        layers.snapshotsLayerEntries.get(0).getEntries());
    assertExtractionPathsUnordered(
        Arrays.asList(
            "/my/app/META-INF",
            "/my/app/META-INF/context.xml",
            "/my/app/Test.jsp",
            "/my/app/WEB-INF",
            "/my/app/WEB-INF/classes",
            "/my/app/WEB-INF/classes/empty_dir",
            "/my/app/WEB-INF/classes/package",
            "/my/app/WEB-INF/classes/package/test.properties",
            "/my/app/WEB-INF/lib",
            "/my/app/WEB-INF/web.xml"),
        layers.resourcesLayerEntries.get(0).getEntries());
    assertExtractionPathsUnordered(
        Arrays.asList(
            "/my/app/WEB-INF/classes/HelloWorld.class",
            "/my/app/WEB-INF/classes/empty_dir",
            "/my/app/WEB-INF/classes/package",
            "/my/app/WEB-INF/classes/package/Other.class"),
        layers.classesLayerEntries.get(0).getEntries());
  }

  @Test
  public void testCreateContainerBuilder_defaultWebAppRoot()
      throws URISyntaxException, IOException, InvalidImageReferenceException,
          CacheDirectoryCreationException {
    Path unzipTarget = setUpWarProject(getResource("gradle/webapp"));

    BuildContext buildContext = setupBuildContext(JavaContainerBuilder.DEFAULT_WEB_APP_ROOT);
    ContainerBuilderLayers layers = new ContainerBuilderLayers(buildContext);
    assertSourcePathsUnordered(
        ImmutableList.of(unzipTarget.resolve("WEB-INF/lib/dependency-1.0.0.jar")),
        layers.dependenciesLayerEntries.get(0).getEntries());
    assertSourcePathsUnordered(
        ImmutableList.of(unzipTarget.resolve("WEB-INF/lib/dependencyX-1.0.0-SNAPSHOT.jar")),
        layers.snapshotsLayerEntries.get(0).getEntries());
    assertSourcePathsUnordered(
        ImmutableList.of(
            unzipTarget.resolve("META-INF"),
            unzipTarget.resolve("META-INF/context.xml"),
            unzipTarget.resolve("Test.jsp"),
            unzipTarget.resolve("WEB-INF"),
            unzipTarget.resolve("WEB-INF/classes"),
            unzipTarget.resolve("WEB-INF/classes/empty_dir"),
            unzipTarget.resolve("WEB-INF/classes/package"),
            unzipTarget.resolve("WEB-INF/classes/package/test.properties"),
            unzipTarget.resolve("WEB-INF/lib"),
            unzipTarget.resolve("WEB-INF/web.xml")),
        layers.resourcesLayerEntries.get(0).getEntries());
    assertSourcePathsUnordered(
        Arrays.asList(
            unzipTarget.resolve("WEB-INF/classes/HelloWorld.class"),
            unzipTarget.resolve("WEB-INF/classes/empty_dir"),
            unzipTarget.resolve("WEB-INF/classes/package"),
            unzipTarget.resolve("WEB-INF/classes/package/Other.class")),
        layers.classesLayerEntries.get(0).getEntries());
  }

  @Test
  public void testCreateContainerBuilder_noErrorIfWebInfClassesDoesNotExist()
      throws IOException, InvalidImageReferenceException, CacheDirectoryCreationException {
    temporaryFolder.newFolder("WEB-INF", "lib");
    setUpWarProject(temporaryFolder.getRoot().toPath());
    setupBuildContext("/anything"); // should pass
  }

  @Test
  public void testCreateContainerBuilder_noErrorIfWebInfLibDoesNotExist()
      throws IOException, InvalidImageReferenceException, CacheDirectoryCreationException {
    temporaryFolder.newFolder("WEB-INF", "classes");
    setUpWarProject(temporaryFolder.getRoot().toPath());
    setupBuildContext("/anything"); // should pass
  }

  @Test
  public void testCreateContainerBuilder_noErrorIfWebInfDoesNotExist()
      throws IOException, InvalidImageReferenceException, CacheDirectoryCreationException {
    setUpWarProject(temporaryFolder.getRoot().toPath());
    setupBuildContext("/anything"); // should pass
  }

  @Test
  public void testGetWarFilePath() {
    Mockito.when(mockProject.getPlugins().hasPlugin(WarPlugin.class)).thenReturn(true);
    Mockito.when(mockTaskContainer.named("war")).thenReturn(mockWarTaskProvider);
    Mockito.when(mockWarTaskProvider.get().getOutputs().getFiles().getAsPath())
        .thenReturn("/war/file/here.war");

    Assert.assertEquals("/war/file/here.war", gradleProjectProperties.getWarFilePath());
  }

  @Test
  public void testGetWarFilePath_bootWar() {
    Mockito.when(mockProject.getPlugins().hasPlugin("org.springframework.boot")).thenReturn(true);
    Mockito.when(mockTaskContainer.named("bootWar")).thenReturn(mockBootWarTaskProvider);
    Mockito.when(mockBootWarTaskProvider.get().getEnabled()).thenReturn(true);
    Mockito.when(mockBootWarTaskProvider.get().getOutputs().getFiles().getAsPath())
        .thenReturn("/boot/war/file.war");

    Assert.assertEquals("/boot/war/file.war", gradleProjectProperties.getWarFilePath());
  }

  @Test
  public void testGetWarFilePath_bootWarDisabled() {
    Mockito.when(mockProject.getPlugins().hasPlugin("org.springframework.boot")).thenReturn(true);
    Mockito.when(mockTaskContainer.named("bootWar")).thenReturn(mockBootWarTaskProvider);
    Mockito.when(mockBootWarTaskProvider.get().getOutputs().getFiles().getAsPath())
        .thenReturn("boot.war");

    Mockito.when(mockProject.getPlugins().hasPlugin(WarPlugin.class)).thenReturn(true);
    Mockito.when(mockTaskContainer.named("war")).thenReturn(mockWarTaskProvider);
    Mockito.when(mockWarTaskProvider.get().getOutputs().getFiles().getAsPath())
        .thenReturn("war.war");

    Assert.assertEquals("war.war", gradleProjectProperties.getWarFilePath());
  }

  @Test
  public void testGetDependencies() throws URISyntaxException {
    Assert.assertEquals(
        Arrays.asList(
            getResource("gradle/application/dependencies/library.jarC.jar"),
            getResource("gradle/application/dependencies/libraryB.jar"),
            getResource("gradle/application/dependencies/libraryA.jar"),
            getResource("gradle/application/dependencies/dependency-1.0.0.jar"),
            getResource("gradle/application/dependencies/more/dependency-1.0.0.jar"),
            getResource("gradle/application/dependencies/another/one/dependency-1.0.0.jar"),
            getResource("gradle/application/dependencies/dependencyX-1.0.0-SNAPSHOT.jar")),
        gradleProjectProperties.getDependencies());
  }

  private BuildContext setupBuildContext(String appRoot)
      throws InvalidImageReferenceException, CacheDirectoryCreationException {
    JavaContainerBuilder javaContainerBuilder =
        JavaContainerBuilder.from(RegistryImage.named("base"))
            .setAppRoot(AbsoluteUnixPath.get(appRoot))
            .setModificationTimeProvider((ignored1, ignored2) -> SAMPLE_FILE_MODIFICATION_TIME);
    JibContainerBuilder jibContainerBuilder =
        gradleProjectProperties.createJibContainerBuilder(
            javaContainerBuilder, ContainerizingMode.EXPLODED);
    return JibContainerBuilderTestHelper.toBuildContext(
        jibContainerBuilder, Containerizer.to(RegistryImage.named("to")));
  }

  private Path setUpWarProject(Path webAppDirectory) throws IOException {
    Path targetZip =
        zipUpDirectory(webAppDirectory, temporaryFolder.getRoot().toPath().resolve("my-app.war"));

    Mockito.when(mockProject.getPlugins().hasPlugin(WarPlugin.class)).thenReturn(true);
    Mockito.when(mockTaskContainer.named("war")).thenReturn(mockWarTaskProvider);
    Mockito.when(mockWarTaskProvider.get().getOutputs().getFiles().getAsPath())
        .thenReturn(targetZip.toString());

    // Make "GradleProjectProperties" use this folder to explode the WAR into.
    Path unzipTarget = temporaryFolder.newFolder("exploded").toPath();
    Mockito.when(mockTempDirectoryProvider.newDirectory()).thenReturn(unzipTarget);
    return unzipTarget;
  }

  private static Path zipUpDirectory(Path sourceRoot, Path targetZip) throws IOException {
    try (ZipOutputStream zipOut = new ZipOutputStream(Files.newOutputStream(targetZip))) {
      for (Path source : new DirectoryWalker(sourceRoot).filterRoot().walk()) {

        StringJoiner pathJoiner = new StringJoiner("/", "", "");
        sourceRoot.relativize(source).forEach(element -> pathJoiner.add(element.toString()));
        String zipEntryPath =
            Files.isDirectory(source) ? pathJoiner.toString() + '/' : pathJoiner.toString();

        ZipEntry entry = new ZipEntry(zipEntryPath);
        zipOut.putNextEntry(entry);
        if (!Files.isDirectory(source)) {
          try (InputStream in = Files.newInputStream(source)) {
            ByteStreams.copy(in, zipOut);
          }
        }
        zipOut.closeEntry();
      }
    }
    return targetZip;
  }
}
