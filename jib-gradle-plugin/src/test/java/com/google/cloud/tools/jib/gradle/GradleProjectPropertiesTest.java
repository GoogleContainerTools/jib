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
import com.google.common.io.ByteStreams;
import com.google.common.io.Resources;
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
import java.util.List;
import java.util.StringJoiner;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.gradle.api.JavaVersion;
import org.gradle.api.Project;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.java.archives.internal.DefaultManifest;
import org.gradle.api.logging.Logger;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.bundling.War;
import org.gradle.jvm.tasks.Jar;
import org.gradle.testfixtures.ProjectBuilder;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/** Test for {@link GradleProjectProperties}. */
@RunWith(MockitoJUnitRunner.class)
public class GradleProjectPropertiesTest {

  private static final Instant SAMPLE_FILE_MODIFICATION_TIME = Instant.ofEpochSecond(32);

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
  @Mock private Logger mockLogger;

  private GradleProjectProperties gradleProjectProperties;
  private Project project;

  @Before
  public void setUp() throws URISyntaxException, IOException {
    Mockito.when(mockLogger.isDebugEnabled()).thenReturn(true);
    Mockito.when(mockLogger.isInfoEnabled()).thenReturn(true);
    Mockito.when(mockLogger.isWarnEnabled()).thenReturn(true);
    Mockito.when(mockLogger.isErrorEnabled()).thenReturn(true);

    Path projectDir = getResource("gradle/application");
    project =
        ProjectBuilder.builder()
            .withName("my-app")
            .withProjectDir(projectDir.toFile())
            .withGradleUserHomeDir(temporaryFolder.newFolder())
            .build();
    project.getPlugins().apply("java");

    DependencyHandler dependencies = project.getDependencies();
    dependencies.add(
        "compile",
        project.files(
            "dependencies/library.jarC.jar",
            "dependencies/libraryB.jar",
            "dependencies/libraryA.jar",
            "dependencies/dependency-1.0.0.jar",
            "dependencies/more/dependency-1.0.0.jar",
            "dependencies/another/one/dependency-1.0.0.jar",
            "dependencies/dependencyX-1.0.0-SNAPSHOT.jar"));

    // We can't commit an empty directory in Git, so create (if not exist).
    Path emptyDirectory = getResource("gradle/webapp").resolve("WEB-INF/classes/empty_dir");
    Files.createDirectories(emptyDirectory);

    gradleProjectProperties =
        new GradleProjectProperties(
            project, mockLogger, mockTempDirectoryProvider, mockExtensionLoader);
  }

  @Test
  public void testGetMainClassFromJar_success() {
    Jar jar = project.getTasks().withType(Jar.class).getByName("jar");
    jar.setManifest(
        new DefaultManifest(null).attributes(ImmutableMap.of("Main-Class", "some.main.class")));

    Assert.assertEquals("some.main.class", gradleProjectProperties.getMainClassFromJarPlugin());
  }

  @Test
  public void testGetMainClassFromJar_missing() {
    Assert.assertNull(gradleProjectProperties.getMainClassFromJarPlugin());
  }

  @Test
  public void testIsWarProject() {
    project.getPlugins().apply("war");
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
    JavaPluginConvention convention =
        project.getConvention().findPlugin(JavaPluginConvention.class);

    convention.setTargetCompatibility(JavaVersion.VERSION_1_3);
    Assert.assertEquals(3, gradleProjectProperties.getMajorJavaVersion());

    convention.setTargetCompatibility(JavaVersion.VERSION_11);
    Assert.assertEquals(11, gradleProjectProperties.getMajorJavaVersion());

    convention.setTargetCompatibility(JavaVersion.VERSION_1_9);
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
            applicationDirectory.resolve("build/resources/main/resourceA"),
            applicationDirectory.resolve("build/resources/main/resourceB"),
            applicationDirectory.resolve("build/resources/main/world")),
        layers.resourcesLayerEntries.get(0).getEntries());
    assertSourcePathsUnordered(
        ImmutableList.of(
            applicationDirectory.resolve("build/classes/java/main/HelloWorld.class"),
            applicationDirectory.resolve("build/classes/java/main/some.class")),
        layers.classesLayerEntries.get(0).getEntries());

    assertModificationTime(SAMPLE_FILE_MODIFICATION_TIME, layers.snapshotsLayerEntries);
    assertModificationTime(SAMPLE_FILE_MODIFICATION_TIME, layers.dependenciesLayerEntries);
    assertModificationTime(SAMPLE_FILE_MODIFICATION_TIME, layers.resourcesLayerEntries);
    assertModificationTime(SAMPLE_FILE_MODIFICATION_TIME, layers.classesLayerEntries);
  }

  @Test
  public void testCreateContainerBuilder_noClassesFiles()
      throws InvalidImageReferenceException, IOException {
    Project project =
        ProjectBuilder.builder()
            .withProjectDir(temporaryFolder.newFolder())
            .withGradleUserHomeDir(temporaryFolder.newFolder())
            .build();
    project.getPlugins().apply("java");

    gradleProjectProperties =
        new GradleProjectProperties(
            project, mockLogger, mockTempDirectoryProvider, mockExtensionLoader);

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
    project.getPlugins().apply("war");
    War war = project.getTasks().withType(War.class).getByName("war");
    war.getDestinationDirectory().set(new File("/war/file"));

    Assert.assertEquals("/war/file/my-app.war", gradleProjectProperties.getWarFilePath());
  }

  @Test
  public void testGetWarFilePath_bootWar() {
    project.getPlugins().apply("war");
    War war = project.getTasks().withType(War.class).getByName("war");
    war.getDestinationDirectory().set(new File("/war"));

    project.getPlugins().apply("org.springframework.boot");
    War bootWar = project.getTasks().withType(War.class).getByName("bootWar");
    bootWar.getDestinationDirectory().set(new File("/boot/war"));

    Assert.assertEquals("/boot/war/my-app.war", gradleProjectProperties.getWarFilePath());
  }

  @Test
  public void testGetWarFilePath_bootWarDisabled() {
    project.getPlugins().apply("war");
    War war = project.getTasks().withType(War.class).getByName("war");
    war.getDestinationDirectory().set(new File("/war"));

    project.getPlugins().apply("org.springframework.boot");
    project.getTasks().getByName("bootWar").setEnabled(false);

    Assert.assertEquals("/war/my-app.war", gradleProjectProperties.getWarFilePath());
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
    File warOutputDir = temporaryFolder.newFolder("output");
    zipUpDirectory(webAppDirectory, warOutputDir.toPath().resolve("my-app.war"));

    project.getPlugins().apply("war");
    War war = project.getTasks().withType(War.class).getByName("war");
    war.getDestinationDirectory().set(warOutputDir);

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
