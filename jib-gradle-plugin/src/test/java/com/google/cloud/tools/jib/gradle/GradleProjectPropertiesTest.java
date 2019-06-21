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

import com.google.cloud.tools.jib.api.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.CacheDirectoryCreationException;
import com.google.cloud.tools.jib.api.Containerizer;
import com.google.cloud.tools.jib.api.FilePermissions;
import com.google.cloud.tools.jib.api.InvalidImageReferenceException;
import com.google.cloud.tools.jib.api.JavaContainerBuilder;
import com.google.cloud.tools.jib.api.JavaContainerBuilder.LayerType;
import com.google.cloud.tools.jib.api.JibContainerBuilder;
import com.google.cloud.tools.jib.api.JibContainerBuilderTestHelper;
import com.google.cloud.tools.jib.api.LayerConfiguration;
import com.google.cloud.tools.jib.api.LayerEntry;
import com.google.cloud.tools.jib.api.RegistryImage;
import com.google.cloud.tools.jib.configuration.BuildConfiguration;
import com.google.cloud.tools.jib.plugins.common.ContainerizingMode;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Resources;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.gradle.StartParameter;
import org.gradle.api.JavaVersion;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.AbstractFileCollection;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.java.archives.Manifest;
import org.gradle.api.java.archives.internal.DefaultManifest;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.configuration.ConsoleOutput;
import org.gradle.api.plugins.Convention;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.plugins.PluginContainer;
import org.gradle.api.plugins.WarPlugin;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.SourceSetOutput;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.api.tasks.bundling.War;
import org.gradle.jvm.tasks.Jar;
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

  private static final ContainerizingMode DEFAULT_CONTAINERIZING_MODE = ContainerizingMode.EXPLODED;

  /** Implementation of {@link FileCollection} that just holds a set of {@link File}s. */
  private static class TestFileCollection extends AbstractFileCollection {

    private final Set<File> files;

    private TestFileCollection(Set<Path> files) {
      this.files = files.stream().map(Path::toFile).collect(Collectors.toSet());
    }

    @Override
    public String getDisplayName() {
      return null;
    }

    @Override
    public Set<File> getFiles() {
      return files;
    }

    @Override
    public TaskDependency getBuildDependencies() {
      return task -> Collections.emptySet();
    }
  }

  /** Helper for reading back layers in a {@code BuildConfiguration}. */
  private static class ContainerBuilderLayers {

    private final List<LayerConfiguration> resourcesLayerEntries;
    private final List<LayerConfiguration> classesLayerEntries;
    private final List<LayerConfiguration> dependenciesLayerEntries;
    private final List<LayerConfiguration> snapshotsLayerEntries;

    private ContainerBuilderLayers(BuildConfiguration configuration) {
      resourcesLayerEntries =
          getLayerConfigurationsByName(configuration, LayerType.RESOURCES.getName());
      classesLayerEntries =
          getLayerConfigurationsByName(configuration, LayerType.CLASSES.getName());
      dependenciesLayerEntries =
          getLayerConfigurationsByName(configuration, LayerType.DEPENDENCIES.getName());
      snapshotsLayerEntries =
          getLayerConfigurationsByName(configuration, LayerType.SNAPSHOT_DEPENDENCIES.getName());
    }
  }

  private static List<LayerConfiguration> getLayerConfigurationsByName(
      BuildConfiguration buildConfiguration, String name) {
    return buildConfiguration
        .getLayerConfigurations()
        .stream()
        .filter(layer -> layer.getName().equals(name))
        .collect(Collectors.toList());
  }

  private static <T> void assertLayerEntriesUnordered(
      List<T> expectedPaths, List<LayerEntry> entries, Function<LayerEntry, T> fieldSelector) {
    List<T> expected = expectedPaths.stream().sorted().collect(Collectors.toList());
    List<T> actual = entries.stream().map(fieldSelector).sorted().collect(Collectors.toList());
    Assert.assertEquals(expected, actual);
  }

  private static void assertSourcePathsUnordered(
      List<Path> expectedPaths, List<LayerEntry> entries) {
    assertLayerEntriesUnordered(expectedPaths, entries, LayerEntry::getSourceFile);
  }

  private static void assertExtractionPathsUnordered(
      List<String> expectedPaths, List<LayerEntry> entries) {
    assertLayerEntriesUnordered(
        expectedPaths, entries, layerEntry -> layerEntry.getExtractionPath().toString());
  }

  private static Path getResource(String path) throws URISyntaxException {
    return Paths.get(Resources.getResource(path).toURI());
  }

  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Mock private FileResolver mockFileResolver;
  @Mock private Jar mockJar;
  @Mock private Project mockProject;
  @Mock private Convention mockConvention;
  @Mock private TaskContainer mockTaskContainer;
  @Mock private PluginContainer mockPluginContainer;
  @Mock private Logger mockLogger;
  @Mock private Gradle mockGradle;
  @Mock private StartParameter mockStartParameter;
  @Mock private JavaPluginConvention mockJavaPluginConvention;
  @Mock private SourceSetContainer mockSourceSetContainer;
  @Mock private SourceSet mockMainSourceSet;
  @Mock private SourceSetOutput mockMainSourceSetOutput;
  @Mock private War war;

  private Manifest manifest;
  private GradleProjectProperties gradleProjectProperties;

  @Before
  public void setup() throws URISyntaxException, IOException {
    manifest = new DefaultManifest(mockFileResolver);
    Mockito.when(mockProject.getConvention()).thenReturn(mockConvention);
    Mockito.when(mockConvention.getPlugin(JavaPluginConvention.class))
        .thenReturn(mockJavaPluginConvention);
    Mockito.when(mockJavaPluginConvention.getSourceSets()).thenReturn(mockSourceSetContainer);
    Mockito.when(mockProject.getTasks()).thenReturn(mockTaskContainer);
    Mockito.when(mockProject.getPlugins()).thenReturn(mockPluginContainer);
    Mockito.when(mockJar.getManifest()).thenReturn(manifest);
    Mockito.when(mockProject.getGradle()).thenReturn(mockGradle);
    Mockito.when(mockGradle.getStartParameter()).thenReturn(mockStartParameter);
    Mockito.when(mockStartParameter.getConsoleOutput()).thenReturn(ConsoleOutput.Auto);

    // mocking to complete ignore project dependency resolution
    Mockito.when(mockProject.getConfigurations())
        .thenReturn(Mockito.mock(ConfigurationContainer.class, Mockito.RETURNS_DEEP_STUBS));
    Mockito.when(
            mockProject
                .getConfigurations()
                .getByName(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME)
                .getResolvedConfiguration()
                .getResolvedArtifacts())
        .thenReturn(ImmutableSet.of());
    ConfigurableFileCollection emptyFileCollection = Mockito.mock(ConfigurableFileCollection.class);
    Mockito.when(emptyFileCollection.getFiles()).thenReturn(ImmutableSet.of());
    Mockito.when(mockProject.files(ImmutableList.of())).thenReturn(emptyFileCollection);
    // done mocking project dependency resolution

    Set<Path> classesFiles = ImmutableSet.of(getResource("gradle/application/classes"));
    FileCollection classesFileCollection = new TestFileCollection(classesFiles);
    Path resourcesOutputDir = getResource("gradle/application/resources");

    Set<Path> allFiles = new HashSet<>(classesFiles);
    allFiles.add(resourcesOutputDir);
    allFiles.add(getResource("gradle/application/dependencies/library.jarC.jar"));
    allFiles.add(getResource("gradle/application/dependencies/libraryB.jar"));
    allFiles.add(getResource("gradle/application/dependencies/libraryA.jar"));
    allFiles.add(getResource("gradle/application/dependencies/dependency-1.0.0.jar"));
    allFiles.add(getResource("gradle/application/dependencies/more/dependency-1.0.0.jar"));
    allFiles.add(getResource("gradle/application/dependencies/another/one/dependency-1.0.0.jar"));
    allFiles.add(getResource("gradle/application/dependencies/dependencyX-1.0.0-SNAPSHOT.jar"));
    FileCollection runtimeFileCollection = new TestFileCollection(allFiles);

    Mockito.when(mockSourceSetContainer.getByName("main")).thenReturn(mockMainSourceSet);
    Mockito.when(mockMainSourceSet.getOutput()).thenReturn(mockMainSourceSetOutput);
    Mockito.when(mockMainSourceSetOutput.getClassesDirs()).thenReturn(classesFileCollection);
    Mockito.when(mockMainSourceSetOutput.getResourcesDir()).thenReturn(resourcesOutputDir.toFile());
    Mockito.when(mockMainSourceSet.getRuntimeClasspath()).thenReturn(runtimeFileCollection);

    // We can't commit an empty directory in Git, so create (if not exist).
    Path emptyDirectory =
        getResource("gradle/webapp").resolve("jib-exploded-war/WEB-INF/classes/empty_dir");
    Files.createDirectories(emptyDirectory);

    gradleProjectProperties = new GradleProjectProperties(mockProject, mockLogger);
  }

  @Test
  public void testGetMainClassFromJar_success() {
    manifest.attributes(ImmutableMap.of("Main-Class", "some.main.class"));
    Mockito.when(mockTaskContainer.findByName("jar")).thenReturn(mockJar);
    Assert.assertEquals("some.main.class", gradleProjectProperties.getMainClassFromJar());
  }

  @Test
  public void testGetMainClassFromJar_missing() {
    Mockito.when(mockTaskContainer.findByName("jar")).thenReturn(null);
    Assert.assertNull(gradleProjectProperties.getMainClassFromJar());
  }

  @Test
  public void testIsWarProject() throws URISyntaxException {
    setUpWarProject(getResource("gradle/webapp"));
    Assert.assertTrue(gradleProjectProperties.isWarProject());
  }

  @Test
  public void testGetWar_warProject() throws URISyntaxException {
    setUpWarProject(getResource("gradle/webapp"));
    Assert.assertNotNull(TaskCommon.getWarTask(mockProject));
  }

  @Test
  public void testGetWar_noWarPlugin() throws URISyntaxException {
    setUpWarProject(getResource("gradle/webapp"));
    Mockito.when(mockPluginContainer.hasPlugin(WarPlugin.class)).thenReturn(false);

    Assert.assertNull(TaskCommon.getWarTask(mockProject));
  }

  @Test
  public void testGetWar_noWarTask() {
    Assert.assertNull(TaskCommon.getWarTask(mockProject));
  }

  @Test
  public void testConvertPermissionsMap() {
    Assert.assertEquals(
        ImmutableMap.of(
            AbsoluteUnixPath.get("/test/folder/file1"),
            FilePermissions.fromOctalString("123"),
            AbsoluteUnixPath.get("/test/file2"),
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
      throws URISyntaxException, IOException, InvalidImageReferenceException,
          CacheDirectoryCreationException {
    BuildConfiguration configuration = setupBuildConfiguration("/app");
    ContainerBuilderLayers layers = new ContainerBuilderLayers(configuration);

    Path applicationDirectory = getResource("gradle/application");
    assertSourcePathsUnordered(
        ImmutableList.of(
            applicationDirectory.resolve("dependencies/dependencyX-1.0.0-SNAPSHOT.jar")),
        layers.snapshotsLayerEntries.get(0).getLayerEntries());
    assertSourcePathsUnordered(
        ImmutableList.of(
            applicationDirectory.resolve("dependencies/dependency-1.0.0.jar"),
            applicationDirectory.resolve("dependencies/more/dependency-1.0.0.jar"),
            applicationDirectory.resolve("dependencies/another/one/dependency-1.0.0.jar"),
            applicationDirectory.resolve("dependencies/libraryA.jar"),
            applicationDirectory.resolve("dependencies/libraryB.jar"),
            applicationDirectory.resolve("dependencies/library.jarC.jar")),
        layers.dependenciesLayerEntries.get(0).getLayerEntries());
    assertSourcePathsUnordered(
        ImmutableList.of(
            applicationDirectory.resolve("resources/resourceA"),
            applicationDirectory.resolve("resources/resourceB"),
            applicationDirectory.resolve("resources/world")),
        layers.resourcesLayerEntries.get(0).getLayerEntries());
    assertSourcePathsUnordered(
        ImmutableList.of(
            applicationDirectory.resolve("classes/HelloWorld.class"),
            applicationDirectory.resolve("classes/some.class")),
        layers.classesLayerEntries.get(0).getLayerEntries());
  }

  @Test
  public void testCreateContainerBuilder_noClassesFiles() throws InvalidImageReferenceException {
    Path nonexistentFile = Paths.get("/nonexistent/file");
    Mockito.when(mockMainSourceSetOutput.getClassesDirs())
        .thenReturn(new TestFileCollection(ImmutableSet.of(nonexistentFile)));
    gradleProjectProperties.createContainerBuilder(
        RegistryImage.named("base"),
        AbsoluteUnixPath.get("/anything"),
        DEFAULT_CONTAINERIZING_MODE);
    Mockito.verify(mockLogger).warn("No classes files were found - did you compile your project?");
  }

  @Test
  public void testCreateContainerBuilder_nonDefaultAppRoot()
      throws IOException, InvalidImageReferenceException, CacheDirectoryCreationException {
    BuildConfiguration configuration = setupBuildConfiguration("/my/app");
    ContainerBuilderLayers layers = new ContainerBuilderLayers(configuration);

    assertExtractionPathsUnordered(
        Arrays.asList(
            "/my/app/libs/dependency-1.0.0-770.jar",
            "/my/app/libs/dependency-1.0.0-200.jar",
            "/my/app/libs/dependency-1.0.0-480.jar",
            "/my/app/libs/libraryA.jar",
            "/my/app/libs/libraryB.jar",
            "/my/app/libs/library.jarC.jar"),
        layers.dependenciesLayerEntries.get(0).getLayerEntries());
    assertExtractionPathsUnordered(
        Collections.singletonList("/my/app/libs/dependencyX-1.0.0-SNAPSHOT.jar"),
        layers.snapshotsLayerEntries.get(0).getLayerEntries());
    assertExtractionPathsUnordered(
        Arrays.asList(
            "/my/app/resources/resourceA",
            "/my/app/resources/resourceB",
            "/my/app/resources/world"),
        layers.resourcesLayerEntries.get(0).getLayerEntries());
    assertExtractionPathsUnordered(
        Arrays.asList("/my/app/classes/HelloWorld.class", "/my/app/classes/some.class"),
        layers.classesLayerEntries.get(0).getLayerEntries());
  }

  @Test
  public void testCreateContainerBuilder_defaultAppRoot()
      throws IOException, InvalidImageReferenceException, CacheDirectoryCreationException {
    BuildConfiguration configuration =
        setupBuildConfiguration(JavaContainerBuilder.DEFAULT_APP_ROOT);
    ContainerBuilderLayers layers = new ContainerBuilderLayers(configuration);
    assertExtractionPathsUnordered(
        Arrays.asList(
            "/app/libs/dependency-1.0.0-770.jar",
            "/app/libs/dependency-1.0.0-200.jar",
            "/app/libs/dependency-1.0.0-480.jar",
            "/app/libs/libraryA.jar",
            "/app/libs/libraryB.jar",
            "/app/libs/library.jarC.jar"),
        layers.dependenciesLayerEntries.get(0).getLayerEntries());
    assertExtractionPathsUnordered(
        Collections.singletonList("/app/libs/dependencyX-1.0.0-SNAPSHOT.jar"),
        layers.snapshotsLayerEntries.get(0).getLayerEntries());
    assertExtractionPathsUnordered(
        Arrays.asList(
            "/app/resources/resourceA", "/app/resources/resourceB", "/app/resources/world"),
        layers.resourcesLayerEntries.get(0).getLayerEntries());
    assertExtractionPathsUnordered(
        Arrays.asList("/app/classes/HelloWorld.class", "/app/classes/some.class"),
        layers.classesLayerEntries.get(0).getLayerEntries());
  }

  @Test
  public void testCreateContainerBuilder_war()
      throws URISyntaxException, IOException, InvalidImageReferenceException,
          CacheDirectoryCreationException {
    Path webAppDirectory = getResource("gradle/webapp");
    setUpWarProject(webAppDirectory);

    BuildConfiguration configuration = setupBuildConfiguration("/my/app");
    ContainerBuilderLayers layers = new ContainerBuilderLayers(configuration);
    assertSourcePathsUnordered(
        ImmutableList.of(
            webAppDirectory.resolve("jib-exploded-war/WEB-INF/lib/dependency-1.0.0.jar")),
        layers.dependenciesLayerEntries.get(0).getLayerEntries());
    assertSourcePathsUnordered(
        ImmutableList.of(
            webAppDirectory.resolve("jib-exploded-war/WEB-INF/lib/dependencyX-1.0.0-SNAPSHOT.jar")),
        layers.snapshotsLayerEntries.get(0).getLayerEntries());
    assertSourcePathsUnordered(
        ImmutableList.of(
            webAppDirectory.resolve("jib-exploded-war/META-INF"),
            webAppDirectory.resolve("jib-exploded-war/META-INF/context.xml"),
            webAppDirectory.resolve("jib-exploded-war/Test.jsp"),
            webAppDirectory.resolve("jib-exploded-war/WEB-INF"),
            webAppDirectory.resolve("jib-exploded-war/WEB-INF/classes"),
            webAppDirectory.resolve("jib-exploded-war/WEB-INF/classes/empty_dir"),
            webAppDirectory.resolve("jib-exploded-war/WEB-INF/classes/package"),
            webAppDirectory.resolve("jib-exploded-war/WEB-INF/classes/package/test.properties"),
            webAppDirectory.resolve("jib-exploded-war/WEB-INF/lib"),
            webAppDirectory.resolve("jib-exploded-war/WEB-INF/web.xml")),
        layers.resourcesLayerEntries.get(0).getLayerEntries());
    assertSourcePathsUnordered(
        ImmutableList.of(
            webAppDirectory.resolve("jib-exploded-war/WEB-INF/classes/HelloWorld.class"),
            webAppDirectory.resolve("jib-exploded-war/WEB-INF/classes/empty_dir"),
            webAppDirectory.resolve("jib-exploded-war/WEB-INF/classes/package"),
            webAppDirectory.resolve("jib-exploded-war/WEB-INF/classes/package/Other.class")),
        layers.classesLayerEntries.get(0).getLayerEntries());

    assertExtractionPathsUnordered(
        Collections.singletonList("/my/app/WEB-INF/lib/dependency-1.0.0.jar"),
        layers.dependenciesLayerEntries.get(0).getLayerEntries());
    assertExtractionPathsUnordered(
        Collections.singletonList("/my/app/WEB-INF/lib/dependencyX-1.0.0-SNAPSHOT.jar"),
        layers.snapshotsLayerEntries.get(0).getLayerEntries());
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
        layers.resourcesLayerEntries.get(0).getLayerEntries());
    assertExtractionPathsUnordered(
        Arrays.asList(
            "/my/app/WEB-INF/classes/HelloWorld.class",
            "/my/app/WEB-INF/classes/empty_dir",
            "/my/app/WEB-INF/classes/package",
            "/my/app/WEB-INF/classes/package/Other.class"),
        layers.classesLayerEntries.get(0).getLayerEntries());
  }

  @Test
  public void testCreateContainerBuilder_defaultWebAppRoot()
      throws URISyntaxException, IOException, InvalidImageReferenceException,
          CacheDirectoryCreationException {
    setUpWarProject(getResource("gradle/webapp"));

    BuildConfiguration configuration =
        setupBuildConfiguration(JavaContainerBuilder.DEFAULT_WEB_APP_ROOT);
    ContainerBuilderLayers layers = new ContainerBuilderLayers(configuration);
    assertExtractionPathsUnordered(
        Collections.singletonList("/jetty/webapps/ROOT/WEB-INF/lib/dependency-1.0.0.jar"),
        layers.dependenciesLayerEntries.get(0).getLayerEntries());
    assertExtractionPathsUnordered(
        Collections.singletonList("/jetty/webapps/ROOT/WEB-INF/lib/dependencyX-1.0.0-SNAPSHOT.jar"),
        layers.snapshotsLayerEntries.get(0).getLayerEntries());
    assertExtractionPathsUnordered(
        Arrays.asList(
            "/jetty/webapps/ROOT/META-INF",
            "/jetty/webapps/ROOT/META-INF/context.xml",
            "/jetty/webapps/ROOT/Test.jsp",
            "/jetty/webapps/ROOT/WEB-INF",
            "/jetty/webapps/ROOT/WEB-INF/classes",
            "/jetty/webapps/ROOT/WEB-INF/classes/empty_dir",
            "/jetty/webapps/ROOT/WEB-INF/classes/package",
            "/jetty/webapps/ROOT/WEB-INF/classes/package/test.properties",
            "/jetty/webapps/ROOT/WEB-INF/lib",
            "/jetty/webapps/ROOT/WEB-INF/web.xml"),
        layers.resourcesLayerEntries.get(0).getLayerEntries());
    assertExtractionPathsUnordered(
        Arrays.asList(
            "/jetty/webapps/ROOT/WEB-INF/classes/HelloWorld.class",
            "/jetty/webapps/ROOT/WEB-INF/classes/empty_dir",
            "/jetty/webapps/ROOT/WEB-INF/classes/package",
            "/jetty/webapps/ROOT/WEB-INF/classes/package/Other.class"),
        layers.classesLayerEntries.get(0).getLayerEntries());
  }

  @Test
  public void testCreateContainerBuilder_noErrorIfWebInfClassesDoesNotExist()
      throws IOException, InvalidImageReferenceException, CacheDirectoryCreationException {
    temporaryFolder.newFolder("jib-exploded-war", "WEB-INF", "lib");
    setUpWarProject(temporaryFolder.getRoot().toPath());
    setupBuildConfiguration(JavaContainerBuilder.DEFAULT_WEB_APP_ROOT); // should pass
  }

  @Test
  public void testCreateContainerBuilder_noErrorIfWebInfLibDoesNotExist()
      throws IOException, InvalidImageReferenceException, CacheDirectoryCreationException {
    temporaryFolder.newFolder("jib-exploded-war", "WEB-INF", "classes");
    setUpWarProject(temporaryFolder.getRoot().toPath());
    setupBuildConfiguration(JavaContainerBuilder.DEFAULT_WEB_APP_ROOT); // should pass
  }

  @Test
  public void testCreateContainerBuilder_noErrorIfWebInfDoesNotExist()
      throws IOException, InvalidImageReferenceException, CacheDirectoryCreationException {
    temporaryFolder.newFolder("jib-exploded-war");
    setUpWarProject(temporaryFolder.getRoot().toPath());
    setupBuildConfiguration(JavaContainerBuilder.DEFAULT_WEB_APP_ROOT); // should pass
  }

  private BuildConfiguration setupBuildConfiguration(String appRoot)
      throws InvalidImageReferenceException, IOException, CacheDirectoryCreationException {
    JibContainerBuilder jibContainerBuilder =
        new GradleProjectProperties(mockProject, mockLogger)
            .createContainerBuilder(
                RegistryImage.named("base"),
                AbsoluteUnixPath.get(appRoot),
                DEFAULT_CONTAINERIZING_MODE);
    return JibContainerBuilderTestHelper.toBuildConfiguration(
        jibContainerBuilder,
        Containerizer.to(RegistryImage.named("to"))
            .setExecutorService(MoreExecutors.newDirectExecutorService()));
  }

  private void setUpWarProject(Path webAppDirectory) {
    Mockito.when(mockProject.getBuildDir()).thenReturn(webAppDirectory.toFile());
    Mockito.when(mockTaskContainer.findByName("war")).thenReturn(war);
    Mockito.when(mockPluginContainer.hasPlugin(WarPlugin.class)).thenReturn(true);
  }
}
