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

package com.google.cloud.tools.jib.maven;

import com.google.cloud.tools.jib.api.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.CacheDirectoryCreationException;
import com.google.cloud.tools.jib.api.Containerizer;
import com.google.cloud.tools.jib.api.InvalidImageReferenceException;
import com.google.cloud.tools.jib.api.JavaContainerBuilder.LayerType;
import com.google.cloud.tools.jib.api.JibContainerBuilder;
import com.google.cloud.tools.jib.api.JibContainerBuilderTestHelper;
import com.google.cloud.tools.jib.api.LayerConfiguration;
import com.google.cloud.tools.jib.api.LayerEntry;
import com.google.cloud.tools.jib.api.RegistryImage;
import com.google.cloud.tools.jib.configuration.BuildConfiguration;
import com.google.cloud.tools.jib.plugins.common.ContainerizingMode;
import com.google.common.collect.ImmutableList;
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
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Build;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/** Test for {@link MavenProjectProperties}. */
@RunWith(MockitoJUnitRunner.class)
public class MavenProjectPropertiesTest {

  private static final ContainerizingMode DEFAULT_CONTAINERIZING_MODE = ContainerizingMode.EXPLODED;

  /** Helper for reading back layers in a {@code BuildConfiguration}. */
  private static class ContainerBuilderLayers {

    private final List<LayerConfiguration> resourcesLayers;
    private final List<LayerConfiguration> classesLayers;
    private final List<LayerConfiguration> dependenciesLayers;
    private final List<LayerConfiguration> snapshotsLayers;
    private final List<LayerConfiguration> extraFilesLayers;

    private ContainerBuilderLayers(BuildConfiguration configuration) {
      resourcesLayers = getLayerConfigurationsByName(configuration, LayerType.RESOURCES.getName());
      classesLayers = getLayerConfigurationsByName(configuration, LayerType.CLASSES.getName());
      dependenciesLayers =
          getLayerConfigurationsByName(configuration, LayerType.DEPENDENCIES.getName());
      snapshotsLayers =
          getLayerConfigurationsByName(configuration, LayerType.SNAPSHOT_DEPENDENCIES.getName());
      extraFilesLayers =
          getLayerConfigurationsByName(configuration, LayerType.EXTRA_FILES.getName());
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

  private static void assertNonDefaultAppRoot(BuildConfiguration configuration) {
    ContainerBuilderLayers layers = new ContainerBuilderLayers(configuration);
    assertExtractionPathsUnordered(
        Arrays.asList(
            "/my/app/libs/dependency-1.0.0-770.jar",
            "/my/app/libs/dependency-1.0.0-200.jar",
            "/my/app/libs/dependency-1.0.0-480.jar",
            "/my/app/libs/libraryA.jar",
            "/my/app/libs/libraryB.jar",
            "/my/app/libs/library.jarC.jar"),
        layers.dependenciesLayers.get(0).getLayerEntries());
    assertExtractionPathsUnordered(
        Collections.singletonList("/my/app/libs/dependencyX-1.0.0-SNAPSHOT.jar"),
        layers.snapshotsLayers.get(0).getLayerEntries());
    assertExtractionPathsUnordered(
        Arrays.asList(
            "/my/app/resources/directory",
            "/my/app/resources/directory/somefile",
            "/my/app/resources/package",
            "/my/app/resources/resourceA",
            "/my/app/resources/resourceB",
            "/my/app/resources/world"),
        layers.resourcesLayers.get(0).getLayerEntries());
    assertExtractionPathsUnordered(
        Arrays.asList(
            "/my/app/classes/HelloWorld.class",
            "/my/app/classes/directory",
            "/my/app/classes/package",
            "/my/app/classes/package/some.class",
            "/my/app/classes/some.class"),
        layers.classesLayers.get(0).getLayerEntries());
  }

  private static Path getResource(String path) throws URISyntaxException {
    return Paths.get(Resources.getResource(path).toURI());
  }

  @Rule public final TestRepository testRepository = new TestRepository();
  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Mock private Build mockBuild;
  @Mock private MavenProject mockMavenProject;
  @Mock private MavenSession mockMavenSession;
  @Mock private MavenExecutionRequest mockMavenRequest;
  @Mock private Properties mockMavenProperties;
  @Mock private Plugin mockJarPlugin;
  @Mock private Plugin mockCompilerPlugin;
  @Mock private Log mockLog;

  private Xpp3Dom jarPluginConfiguration;
  private Xpp3Dom archive;
  private Xpp3Dom manifest;
  private Xpp3Dom jarPluginMainClass;

  @Mock private Xpp3Dom compilerPluginConfiguration;
  @Mock private Xpp3Dom compilerTarget;
  @Mock private Xpp3Dom compilerRelease;

  private MavenProjectProperties mavenProjectProperties;

  @Before
  public void setUp() throws IOException, URISyntaxException {
    Mockito.when(mockMavenSession.getRequest()).thenReturn(mockMavenRequest);
    mavenProjectProperties =
        new MavenProjectProperties(mockMavenProject, mockMavenSession, mockLog);
    jarPluginConfiguration = new Xpp3Dom("");
    archive = new Xpp3Dom("archive");
    manifest = new Xpp3Dom("manifest");
    jarPluginMainClass = new Xpp3Dom("mainClass");

    Path outputPath = getResource("maven/application/output");
    Path dependenciesPath = getResource("maven/application/dependencies");

    Mockito.when(mockMavenProject.getBuild()).thenReturn(mockBuild);
    Mockito.when(mockBuild.getOutputDirectory()).thenReturn(outputPath.toString());

    Set<Artifact> artifacts =
        ImmutableSet.of(
            makeArtifact(dependenciesPath.resolve("library.jarC.jar")),
            makeArtifact(dependenciesPath.resolve("libraryB.jar")),
            makeArtifact(dependenciesPath.resolve("libraryA.jar")),
            makeArtifact(dependenciesPath.resolve("more/dependency-1.0.0.jar")),
            makeArtifact(dependenciesPath.resolve("another/one/dependency-1.0.0.jar")),
            // Maven reads and populates "Artifacts" with its own processing, so read some from a
            // repository
            testRepository.findArtifact("com.test", "dependency", "1.0.0"),
            testRepository.findArtifact("com.test", "dependencyX", "1.0.0-SNAPSHOT"));
    Mockito.when(mockMavenProject.getArtifacts()).thenReturn(artifacts);

    Path emptyDirectory =
        getResource("maven/webapp").resolve("final-name/WEB-INF/classes/empty_dir");
    Files.createDirectories(emptyDirectory);

    Mockito.when(mockMavenProject.getProperties()).thenReturn(mockMavenProperties);
  }

  private Artifact makeArtifact(Path path) {
    Artifact artifact = Mockito.mock(Artifact.class);
    Mockito.when(artifact.getFile()).thenReturn(path.toFile());
    return artifact;
  }

  @Test
  public void testGetMainClassFromJar_success() {
    Mockito.when(mockMavenProject.getPlugin("org.apache.maven.plugins:maven-jar-plugin"))
        .thenReturn(mockJarPlugin);
    Mockito.when(mockJarPlugin.getConfiguration()).thenReturn(jarPluginConfiguration);
    jarPluginConfiguration.addChild(archive);
    archive.addChild(manifest);
    manifest.addChild(jarPluginMainClass);
    jarPluginMainClass.setValue("some.main.class");

    Assert.assertEquals("some.main.class", mavenProjectProperties.getMainClassFromJar());
  }

  @Test
  public void testGetMainClassFromJar_missingMainClass() {
    Mockito.when(mockMavenProject.getPlugin("org.apache.maven.plugins:maven-jar-plugin"))
        .thenReturn(mockJarPlugin);
    Mockito.when(mockJarPlugin.getConfiguration()).thenReturn(jarPluginConfiguration);
    jarPluginConfiguration.addChild(archive);
    archive.addChild(manifest);

    Assert.assertNull(mavenProjectProperties.getMainClassFromJar());
  }

  @Test
  public void testGetMainClassFromJar_missingManifest() {
    Mockito.when(mockMavenProject.getPlugin("org.apache.maven.plugins:maven-jar-plugin"))
        .thenReturn(mockJarPlugin);
    Mockito.when(mockJarPlugin.getConfiguration()).thenReturn(jarPluginConfiguration);
    jarPluginConfiguration.addChild(archive);

    Assert.assertNull(mavenProjectProperties.getMainClassFromJar());
  }

  @Test
  public void testGetMainClassFromJar_missingArchive() {
    Mockito.when(mockMavenProject.getPlugin("org.apache.maven.plugins:maven-jar-plugin"))
        .thenReturn(mockJarPlugin);
    Mockito.when(mockJarPlugin.getConfiguration()).thenReturn(jarPluginConfiguration);

    Assert.assertNull(mavenProjectProperties.getMainClassFromJar());
  }

  @Test
  public void testGetMainClassFromJar_missingConfiguration() {
    Mockito.when(mockMavenProject.getPlugin("org.apache.maven.plugins:maven-jar-plugin"))
        .thenReturn(mockJarPlugin);

    Assert.assertNull(mavenProjectProperties.getMainClassFromJar());
  }

  @Test
  public void testGetMainClassFromJar_missingPlugin() {
    Assert.assertNull(mavenProjectProperties.getMainClassFromJar());
  }

  @Test
  public void testIsWarProject() {
    Assert.assertFalse(mavenProjectProperties.isWarProject());
  }

  @Test
  public void testGetVersionFromString() {
    Assert.assertEquals(8, MavenProjectProperties.getVersionFromString("1.8"));
    Assert.assertEquals(8, MavenProjectProperties.getVersionFromString("1.8.0_123"));
    Assert.assertEquals(11, MavenProjectProperties.getVersionFromString("11"));
    Assert.assertEquals(11, MavenProjectProperties.getVersionFromString("11.0.1"));

    Assert.assertEquals(0, MavenProjectProperties.getVersionFromString("asdfasdf"));
    Assert.assertEquals(0, MavenProjectProperties.getVersionFromString(""));
    Assert.assertEquals(0, MavenProjectProperties.getVersionFromString("11abc"));
    Assert.assertEquals(0, MavenProjectProperties.getVersionFromString("1.abc"));
  }

  @Test
  public void testGetMajorJavaVersion_undefinedDefaultsTo6() {
    Assert.assertEquals(6, mavenProjectProperties.getMajorJavaVersion());
  }

  @Test
  public void testGetMajorJavaVersion_targetProperty() {
    Mockito.when(mockMavenProperties.getProperty("maven.compiler.target")).thenReturn("1.8");
    Assert.assertEquals(8, mavenProjectProperties.getMajorJavaVersion());

    Mockito.when(mockMavenProperties.getProperty("maven.compiler.target")).thenReturn("1.7");
    Assert.assertEquals(7, mavenProjectProperties.getMajorJavaVersion());

    Mockito.when(mockMavenProperties.getProperty("maven.compiler.target")).thenReturn("11");
    Assert.assertEquals(11, mavenProjectProperties.getMajorJavaVersion());
  }

  @Test
  public void testValidateBaseImageVersion_releaseProperty() {
    Mockito.when(mockMavenProperties.getProperty("maven.compiler.release")).thenReturn("1.8");
    Assert.assertEquals(8, mavenProjectProperties.getMajorJavaVersion());

    Mockito.when(mockMavenProperties.getProperty("maven.compiler.release")).thenReturn("1.7");
    Assert.assertEquals(7, mavenProjectProperties.getMajorJavaVersion());

    Mockito.when(mockMavenProperties.getProperty("maven.compiler.release")).thenReturn("9");
    Assert.assertEquals(9, mavenProjectProperties.getMajorJavaVersion());
  }

  @Test
  public void testValidateBaseImageVersion_compilerPluginTarget() {
    Mockito.when(mockMavenProject.getPlugin("org.apache.maven.plugins:maven-compiler-plugin"))
        .thenReturn(mockCompilerPlugin);
    Mockito.when(mockCompilerPlugin.getConfiguration()).thenReturn(compilerPluginConfiguration);
    Mockito.when(compilerPluginConfiguration.getChild("target")).thenReturn(compilerTarget);

    Mockito.when(compilerTarget.getValue()).thenReturn("1.8");
    Assert.assertEquals(8, mavenProjectProperties.getMajorJavaVersion());

    Mockito.when(compilerTarget.getValue()).thenReturn("1.6");
    Assert.assertEquals(6, mavenProjectProperties.getMajorJavaVersion());

    Mockito.when(compilerTarget.getValue()).thenReturn("13");
    Assert.assertEquals(13, mavenProjectProperties.getMajorJavaVersion());
  }

  @Test
  public void testValidateBaseImageVersion_compilerPluginRelease() {
    Mockito.when(mockMavenProject.getPlugin("org.apache.maven.plugins:maven-compiler-plugin"))
        .thenReturn(mockCompilerPlugin);
    Mockito.when(mockCompilerPlugin.getConfiguration()).thenReturn(compilerPluginConfiguration);
    Mockito.when(compilerPluginConfiguration.getChild("release")).thenReturn(compilerRelease);

    Mockito.when(compilerRelease.getValue()).thenReturn("1.8");
    Assert.assertEquals(8, mavenProjectProperties.getMajorJavaVersion());

    Mockito.when(compilerRelease.getValue()).thenReturn("10");
    Assert.assertEquals(10, mavenProjectProperties.getMajorJavaVersion());

    Mockito.when(compilerRelease.getValue()).thenReturn("13");
    Assert.assertEquals(13, mavenProjectProperties.getMajorJavaVersion());
  }

  @Test
  public void isProgressFooterEnabled() {
    Mockito.when(mockMavenRequest.isInteractiveMode()).thenReturn(false);
    Assert.assertFalse(MavenProjectProperties.isProgressFooterEnabled(mockMavenSession));
  }

  @Test
  public void testCreateContainerBuilder_correctFiles()
      throws URISyntaxException, IOException, InvalidImageReferenceException,
          CacheDirectoryCreationException {
    BuildConfiguration configuration = setupBuildConfiguration("/app", DEFAULT_CONTAINERIZING_MODE);
    ContainerBuilderLayers layers = new ContainerBuilderLayers(configuration);

    Path dependenciesPath = getResource("maven/application/dependencies");
    Path applicationDirectory = getResource("maven/application");
    assertSourcePathsUnordered(
        ImmutableList.of(
            testRepository.artifactPathOnDisk("com.test", "dependency", "1.0.0"),
            dependenciesPath.resolve("more/dependency-1.0.0.jar"),
            dependenciesPath.resolve("another/one/dependency-1.0.0.jar"),
            dependenciesPath.resolve("libraryA.jar"),
            dependenciesPath.resolve("libraryB.jar"),
            dependenciesPath.resolve("library.jarC.jar")),
        layers.dependenciesLayers.get(0).getLayerEntries());
    assertSourcePathsUnordered(
        ImmutableList.of(
            testRepository.artifactPathOnDisk("com.test", "dependencyX", "1.0.0-SNAPSHOT")),
        layers.snapshotsLayers.get(0).getLayerEntries());
    assertSourcePathsUnordered(
        ImmutableList.of(
            applicationDirectory.resolve("output/directory"),
            applicationDirectory.resolve("output/directory/somefile"),
            applicationDirectory.resolve("output/package"),
            applicationDirectory.resolve("output/resourceA"),
            applicationDirectory.resolve("output/resourceB"),
            applicationDirectory.resolve("output/world")),
        layers.resourcesLayers.get(0).getLayerEntries());
    assertSourcePathsUnordered(
        ImmutableList.of(
            applicationDirectory.resolve("output/HelloWorld.class"),
            applicationDirectory.resolve("output/directory"),
            applicationDirectory.resolve("output/package"),
            applicationDirectory.resolve("output/package/some.class"),
            applicationDirectory.resolve("output/some.class")),
        layers.classesLayers.get(0).getLayerEntries());
  }

  @Test
  public void testCreateContainerBuilder_nonDefaultAppRoot()
      throws IOException, InvalidImageReferenceException, CacheDirectoryCreationException {
    BuildConfiguration configuration =
        setupBuildConfiguration("/my/app", DEFAULT_CONTAINERIZING_MODE);
    assertNonDefaultAppRoot(configuration);
  }

  @Test
  public void testCreateContainerBuilder_packagedMode()
      throws InvalidImageReferenceException, IOException, CacheDirectoryCreationException,
          URISyntaxException {
    Path jar = temporaryFolder.newFile("final-name.jar").toPath();
    Mockito.when(mockBuild.getDirectory()).thenReturn(temporaryFolder.getRoot().toString());
    Mockito.when(mockBuild.getFinalName()).thenReturn("final-name");

    BuildConfiguration configuration =
        setupBuildConfiguration("/app-root", ContainerizingMode.PACKAGED);

    ContainerBuilderLayers layers = new ContainerBuilderLayers(configuration);
    Assert.assertEquals(1, layers.dependenciesLayers.size());
    Assert.assertEquals(1, layers.snapshotsLayers.size());
    Assert.assertEquals(0, layers.resourcesLayers.size());
    Assert.assertEquals(0, layers.classesLayers.size());
    Assert.assertEquals(1, layers.extraFilesLayers.size());

    Path dependenciesPath = getResource("maven/application/dependencies");
    assertSourcePathsUnordered(
        Arrays.asList(
            testRepository.artifactPathOnDisk("com.test", "dependency", "1.0.0"),
            dependenciesPath.resolve("more/dependency-1.0.0.jar"),
            dependenciesPath.resolve("another/one/dependency-1.0.0.jar"),
            dependenciesPath.resolve("libraryA.jar"),
            dependenciesPath.resolve("libraryB.jar"),
            dependenciesPath.resolve("library.jarC.jar")),
        layers.dependenciesLayers.get(0).getLayerEntries());
    assertSourcePathsUnordered(
        Arrays.asList(
            testRepository.artifactPathOnDisk("com.test", "dependencyX", "1.0.0-SNAPSHOT")),
        layers.snapshotsLayers.get(0).getLayerEntries());
    assertSourcePathsUnordered(
        Arrays.asList(jar), layers.extraFilesLayers.get(0).getLayerEntries());

    assertExtractionPathsUnordered(
        Arrays.asList(
            "/app-root/libs/dependency-1.0.0-200.jar",
            "/app-root/libs/dependency-1.0.0-480.jar",
            "/app-root/libs/dependency-1.0.0-770.jar",
            "/app-root/libs/library.jarC.jar",
            "/app-root/libs/libraryA.jar",
            "/app-root/libs/libraryB.jar"),
        layers.dependenciesLayers.get(0).getLayerEntries());
    assertExtractionPathsUnordered(
        Arrays.asList("/app-root/libs/dependencyX-1.0.0-SNAPSHOT.jar"),
        layers.snapshotsLayers.get(0).getLayerEntries());
    assertExtractionPathsUnordered(
        Arrays.asList("/app-root/classpath/final-name.jar"),
        layers.extraFilesLayers.get(0).getLayerEntries());
  }

  @Test
  public void testCreateContainerBuilder_warNonDefaultAppRoot()
      throws URISyntaxException, IOException, InvalidImageReferenceException,
          CacheDirectoryCreationException {
    Path outputPath = getResource("maven/webapp");
    Mockito.when(mockMavenProject.getPackaging()).thenReturn("war");
    Mockito.when(mockBuild.getDirectory()).thenReturn(outputPath.toString());
    Mockito.when(mockBuild.getFinalName()).thenReturn("final-name");

    BuildConfiguration configuration =
        setupBuildConfiguration("/my/app", DEFAULT_CONTAINERIZING_MODE);
    ContainerBuilderLayers layers = new ContainerBuilderLayers(configuration);
    assertSourcePathsUnordered(
        ImmutableList.of(outputPath.resolve("final-name/WEB-INF/lib/dependency-1.0.0.jar")),
        layers.dependenciesLayers.get(0).getLayerEntries());
    assertSourcePathsUnordered(
        ImmutableList.of(
            outputPath.resolve("final-name/WEB-INF/lib/dependencyX-1.0.0-SNAPSHOT.jar")),
        layers.snapshotsLayers.get(0).getLayerEntries());
    assertSourcePathsUnordered(
        ImmutableList.of(
            outputPath.resolve("final-name/META-INF"),
            outputPath.resolve("final-name/META-INF/context.xml"),
            outputPath.resolve("final-name/Test.jsp"),
            outputPath.resolve("final-name/WEB-INF"),
            outputPath.resolve("final-name/WEB-INF/classes"),
            outputPath.resolve("final-name/WEB-INF/classes/empty_dir"),
            outputPath.resolve("final-name/WEB-INF/classes/package"),
            outputPath.resolve("final-name/WEB-INF/classes/package/test.properties"),
            outputPath.resolve("final-name/WEB-INF/lib"),
            outputPath.resolve("final-name/WEB-INF/web.xml")),
        layers.resourcesLayers.get(0).getLayerEntries());
    assertSourcePathsUnordered(
        ImmutableList.of(
            outputPath.resolve("final-name/WEB-INF/classes/HelloWorld.class"),
            outputPath.resolve("final-name/WEB-INF/classes/empty_dir"),
            outputPath.resolve("final-name/WEB-INF/classes/package"),
            outputPath.resolve("final-name/WEB-INF/classes/package/Other.class")),
        layers.classesLayers.get(0).getLayerEntries());

    assertExtractionPathsUnordered(
        Collections.singletonList("/my/app/WEB-INF/lib/dependency-1.0.0.jar"),
        layers.dependenciesLayers.get(0).getLayerEntries());
    assertExtractionPathsUnordered(
        Collections.singletonList("/my/app/WEB-INF/lib/dependencyX-1.0.0-SNAPSHOT.jar"),
        layers.snapshotsLayers.get(0).getLayerEntries());
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
        layers.resourcesLayers.get(0).getLayerEntries());
    assertExtractionPathsUnordered(
        Arrays.asList(
            "/my/app/WEB-INF/classes/HelloWorld.class",
            "/my/app/WEB-INF/classes/empty_dir",
            "/my/app/WEB-INF/classes/package",
            "/my/app/WEB-INF/classes/package/Other.class"),
        layers.classesLayers.get(0).getLayerEntries());
  }

  @Test
  public void testCreateContainerBuilder_jarNonDefaultAppRoot()
      throws IOException, InvalidImageReferenceException, CacheDirectoryCreationException {
    // Test when the default packaging is set
    Mockito.when(mockMavenProject.getPackaging()).thenReturn("jar");
    BuildConfiguration configuration =
        setupBuildConfiguration("/my/app", DEFAULT_CONTAINERIZING_MODE);
    assertNonDefaultAppRoot(configuration);
  }

  @Test
  public void testCreateContainerBuilder_noErrorIfWebInfDoesNotExist()
      throws IOException, InvalidImageReferenceException, CacheDirectoryCreationException {
    temporaryFolder.newFolder("final-name");
    Mockito.when(mockMavenProject.getPackaging()).thenReturn("war");
    Mockito.when(mockBuild.getDirectory()).thenReturn(temporaryFolder.getRoot().toString());
    Mockito.when(mockBuild.getFinalName()).thenReturn("final-name");

    setupBuildConfiguration("/my/app", DEFAULT_CONTAINERIZING_MODE); // should pass
  }

  @Test
  public void testCreateContainerBuilder_noErrorIfWebInfLibDoesNotExist()
      throws IOException, InvalidImageReferenceException, CacheDirectoryCreationException {
    temporaryFolder.newFolder("final-name", "WEB-INF", "classes");
    Mockito.when(mockMavenProject.getPackaging()).thenReturn("war");
    Mockito.when(mockBuild.getDirectory()).thenReturn(temporaryFolder.getRoot().toString());
    Mockito.when(mockBuild.getFinalName()).thenReturn("final-name");

    setupBuildConfiguration("/my/app", DEFAULT_CONTAINERIZING_MODE); // should pass
  }

  @Test
  public void testCreateContainerBuilder_noErrorIfWebInfClassesDoesNotExist()
      throws IOException, InvalidImageReferenceException, CacheDirectoryCreationException {
    temporaryFolder.newFolder("final-name", "WEB-INF", "lib");
    Mockito.when(mockMavenProject.getPackaging()).thenReturn("war");
    Mockito.when(mockBuild.getDirectory()).thenReturn(temporaryFolder.getRoot().toString());
    Mockito.when(mockBuild.getFinalName()).thenReturn("final-name");

    setupBuildConfiguration("/my/app", DEFAULT_CONTAINERIZING_MODE); // should pass
  }

  @Test
  public void testGetJarArtifact() {
    Mockito.when(mockBuild.getDirectory()).thenReturn(temporaryFolder.getRoot().toString());
    Mockito.when(mockBuild.getFinalName()).thenReturn("helloworld-1");

    Assert.assertEquals(
        temporaryFolder.getRoot().toPath().resolve("helloworld-1.jar"),
        mavenProjectProperties.getJarArtifact());
  }

  @Test
  public void testIsWarProject_WarPackagingIsWar() {
    Mockito.when(mockMavenProject.getPackaging()).thenReturn("war");
    Assert.assertTrue(mavenProjectProperties.isWarProject());
  }

  @Test
  public void testIsWarProject_GwtAppPackagingIsWar() {
    Mockito.when(mockMavenProject.getPackaging()).thenReturn("gwt-app");
    Assert.assertTrue(mavenProjectProperties.isWarProject());
  }

  @Test
  public void testIsWarProject_JarPackagingIsNotWar() {
    Mockito.when(mockMavenProject.getPackaging()).thenReturn("jar");
    Assert.assertFalse(mavenProjectProperties.isWarProject());
  }

  @Test
  public void testIsWarProject_GwtLibPackagingIsNotWar() {
    Mockito.when(mockMavenProject.getPackaging()).thenReturn("gwt-lib");
    Assert.assertFalse(mavenProjectProperties.isWarProject());
  }

  private BuildConfiguration setupBuildConfiguration(
      String appRoot, ContainerizingMode containerizingMode)
      throws InvalidImageReferenceException, IOException, CacheDirectoryCreationException {
    JibContainerBuilder JibContainerBuilder =
        new MavenProjectProperties(mockMavenProject, mockMavenSession, mockLog)
            .createContainerBuilder(
                RegistryImage.named("base"), AbsoluteUnixPath.get(appRoot), containerizingMode);
    return JibContainerBuilderTestHelper.toBuildConfiguration(
        JibContainerBuilder,
        Containerizer.to(RegistryImage.named("to"))
            .setExecutorService(MoreExecutors.newDirectExecutorService()));
  }

  @Test
  public void testClassifyDependencies() {
    Set<Artifact> artifacts =
        ImmutableSet.of(
            newArtifact("com.test", "dependencyA", "1.0"),
            newArtifact("com.test", "dependencyB", "4.0-SNAPSHOT"),
            newArtifact("com.test", "projectA", "1.0"),
            newArtifact("com.test", "dependencyC", "1.0-SNAPSHOT"),
            newArtifact("com.test", "dependencyD", "4.0"),
            newArtifact("com.test", "projectB", "1.0-SNAPSHOT"),
            newArtifact("com.test", "projectC", "3.0"));

    Set<Artifact> projectArtifacts =
        ImmutableSet.of(
            newArtifact("com.test", "projectA", "1.0"),
            newArtifact("com.test", "projectB", "1.0-SNAPSHOT"),
            newArtifact("com.test", "projectC", "3.0"));

    Map<LayerType, List<Path>> classifyDependencies =
        new MavenProjectProperties(mockMavenProject, mockMavenSession, mockLog)
            .classifyDependencies(artifacts, projectArtifacts);

    Assert.assertEquals(
        classifyDependencies.get(LayerType.DEPENDENCIES),
        ImmutableList.of(
            newArtifact("com.test", "dependencyA", "1.0").getFile().toPath(),
            newArtifact("com.test", "dependencyD", "4.0").getFile().toPath()));

    Assert.assertEquals(
        classifyDependencies.get(LayerType.SNAPSHOT_DEPENDENCIES),
        ImmutableList.of(
            newArtifact("com.test", "dependencyB", "4.0-SNAPSHOT").getFile().toPath(),
            newArtifact("com.test", "dependencyC", "1.0-SNAPSHOT").getFile().toPath()));

    Assert.assertEquals(
        classifyDependencies.get(LayerType.PROJECT_DEPENDENCIES),
        ImmutableList.of(
            newArtifact("com.test", "projectA", "1.0").getFile().toPath(),
            newArtifact("com.test", "projectB", "1.0-SNAPSHOT").getFile().toPath(),
            newArtifact("com.test", "projectC", "3.0").getFile().toPath()));
  }

  private Artifact newArtifact(String group, String artifactId, String version) {
    Artifact artifact = new DefaultArtifact(group, artifactId, version, null, "jar", "", null);
    artifact.setFile(new File("/tmp/" + group + artifactId + version));
    return artifact;
  }
}
