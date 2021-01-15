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
import com.google.cloud.tools.jib.configuration.BuildContext;
import com.google.cloud.tools.jib.filesystem.DirectoryWalker;
import com.google.cloud.tools.jib.filesystem.TempDirectoryProvider;
import com.google.cloud.tools.jib.maven.extension.JibMavenPluginExtension;
import com.google.cloud.tools.jib.plugins.common.ContainerizingMode;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
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
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.StringJoiner;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Build;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.archiver.zip.ZipEntry;
import org.codehaus.plexus.archiver.zip.ZipOutputStream;
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
  private static final Instant SAMPLE_FILE_MODIFICATION_TIME = Instant.ofEpochSecond(32);

  /** Helper for reading back layers in a {@link BuildContext}. */
  private static class ContainerBuilderLayers {

    private final List<FileEntriesLayer> resourcesLayers;
    private final List<FileEntriesLayer> classesLayers;
    private final List<FileEntriesLayer> dependenciesLayers;
    private final List<FileEntriesLayer> snapshotsLayers;
    private final List<FileEntriesLayer> extraFilesLayers;

    private ContainerBuilderLayers(BuildContext buildContext) {
      resourcesLayers = getLayerConfigurationsByName(buildContext, LayerType.RESOURCES.getName());
      classesLayers = getLayerConfigurationsByName(buildContext, LayerType.CLASSES.getName());
      dependenciesLayers =
          getLayerConfigurationsByName(buildContext, LayerType.DEPENDENCIES.getName());
      snapshotsLayers =
          getLayerConfigurationsByName(buildContext, LayerType.SNAPSHOT_DEPENDENCIES.getName());
      extraFilesLayers =
          getLayerConfigurationsByName(buildContext, LayerType.EXTRA_FILES.getName());
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

  private static void assertNonDefaultAppRoot(BuildContext buildContext) {
    ContainerBuilderLayers layers = new ContainerBuilderLayers(buildContext);
    assertExtractionPathsUnordered(
        Arrays.asList(
            "/my/app/libs/dependency-1.0.0-770.jar",
            "/my/app/libs/dependency-1.0.0-200.jar",
            "/my/app/libs/dependency-1.0.0-480.jar",
            "/my/app/libs/libraryA.jar",
            "/my/app/libs/libraryB.jar",
            "/my/app/libs/library.jarC.jar"),
        layers.dependenciesLayers.get(0).getEntries());
    assertExtractionPathsUnordered(
        Collections.singletonList("/my/app/libs/dependencyX-1.0.0-SNAPSHOT.jar"),
        layers.snapshotsLayers.get(0).getEntries());
    assertExtractionPathsUnordered(
        Arrays.asList(
            "/my/app/resources/directory",
            "/my/app/resources/directory/somefile",
            "/my/app/resources/package",
            "/my/app/resources/resourceA",
            "/my/app/resources/resourceB",
            "/my/app/resources/world"),
        layers.resourcesLayers.get(0).getEntries());
    assertExtractionPathsUnordered(
        Arrays.asList(
            "/my/app/classes/HelloWorld.class",
            "/my/app/classes/directory",
            "/my/app/classes/package",
            "/my/app/classes/package/some.class",
            "/my/app/classes/some.class"),
        layers.classesLayers.get(0).getEntries());
  }

  private static Path getResource(String path) throws URISyntaxException {
    return Paths.get(Resources.getResource(path).toURI());
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

  private static Artifact newArtifact(Path sourceJar) {
    Artifact artifact = Mockito.mock(Artifact.class);
    Mockito.when(artifact.getFile()).thenReturn(sourceJar.toFile());
    return artifact;
  }

  private static Artifact newArtifact(String group, String artifactId, String version) {
    Artifact artifact = new DefaultArtifact(group, artifactId, version, null, "jar", "", null);
    artifact.setFile(new File("/tmp/" + group + artifactId + version));
    return artifact;
  }

  private static Xpp3Dom newXpp3Dom(String name, String value) {
    Xpp3Dom node = new Xpp3Dom(name);
    node.setValue(value);
    return node;
  }

  private static Xpp3Dom addXpp3DomChild(Xpp3Dom parent, String name, String value) {
    Xpp3Dom node = new Xpp3Dom(name);
    node.setValue(value);
    parent.addChild(node);
    return node;
  }

  @Rule public final TestRepository testRepository = new TestRepository();
  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  private final Xpp3Dom pluginConfiguration = new Xpp3Dom("configuration");

  @Mock private Build mockBuild;
  @Mock private PluginDescriptor mockJibPluginDescriptor;
  @Mock private MavenProject mockMavenProject;
  @Mock private MavenSession mockMavenSession;
  @Mock private MavenExecutionRequest mockMavenRequest;
  @Mock private Properties mockMavenProperties;
  @Mock private Plugin mockPlugin;
  @Mock private PluginExecution mockPluginExecution;
  @Mock private Log mockLog;
  @Mock private TempDirectoryProvider mockTempDirectoryProvider;
  @Mock private Supplier<List<JibMavenPluginExtension<?>>> mockExtensionLoader;

  private MavenProjectProperties mavenProjectProperties;

  @Before
  public void setUp() throws IOException, URISyntaxException {
    Mockito.when(mockLog.isDebugEnabled()).thenReturn(true);
    Mockito.when(mockLog.isWarnEnabled()).thenReturn(true);
    Mockito.when(mockLog.isErrorEnabled()).thenReturn(true);

    Mockito.when(mockMavenSession.getRequest()).thenReturn(mockMavenRequest);
    mavenProjectProperties =
        new MavenProjectProperties(
            mockJibPluginDescriptor,
            mockMavenProject,
            mockMavenSession,
            mockLog,
            mockTempDirectoryProvider,
            mockExtensionLoader);

    Path outputPath = getResource("maven/application/output");
    Path dependenciesPath = getResource("maven/application/dependencies");

    Mockito.when(mockMavenProject.getBuild()).thenReturn(mockBuild);
    Mockito.when(mockBuild.getOutputDirectory()).thenReturn(outputPath.toString());

    Set<Artifact> artifacts =
        ImmutableSet.of(
            newArtifact(dependenciesPath.resolve("library.jarC.jar")),
            newArtifact(dependenciesPath.resolve("libraryB.jar")),
            newArtifact(dependenciesPath.resolve("libraryA.jar")),
            newArtifact(dependenciesPath.resolve("more/dependency-1.0.0.jar")),
            newArtifact(dependenciesPath.resolve("another/one/dependency-1.0.0.jar")),
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

  @Test
  public void testGetMainClassFromJar_success() {
    Mockito.when(mockMavenProject.getPlugin("org.apache.maven.plugins:maven-jar-plugin"))
        .thenReturn(mockPlugin);
    Mockito.when(mockPlugin.getConfiguration()).thenReturn(pluginConfiguration);
    Xpp3Dom archive = new Xpp3Dom("archive");
    Xpp3Dom manifest = new Xpp3Dom("manifest");
    pluginConfiguration.addChild(archive);
    archive.addChild(manifest);
    manifest.addChild(newXpp3Dom("mainClass", "some.main.class"));

    Assert.assertEquals("some.main.class", mavenProjectProperties.getMainClassFromJarPlugin());
  }

  @Test
  public void testGetMainClassFromJar_missingMainClass() {
    Mockito.when(mockMavenProject.getPlugin("org.apache.maven.plugins:maven-jar-plugin"))
        .thenReturn(mockPlugin);
    Mockito.when(mockPlugin.getConfiguration()).thenReturn(pluginConfiguration);
    Xpp3Dom archive = new Xpp3Dom("archive");
    archive.addChild(new Xpp3Dom("manifest"));
    pluginConfiguration.addChild(archive);

    Assert.assertNull(mavenProjectProperties.getMainClassFromJarPlugin());
  }

  @Test
  public void testGetMainClassFromJar_missingManifest() {
    Mockito.when(mockMavenProject.getPlugin("org.apache.maven.plugins:maven-jar-plugin"))
        .thenReturn(mockPlugin);
    Mockito.when(mockPlugin.getConfiguration()).thenReturn(pluginConfiguration);
    pluginConfiguration.addChild(new Xpp3Dom("archive"));

    Assert.assertNull(mavenProjectProperties.getMainClassFromJarPlugin());
  }

  @Test
  public void testGetMainClassFromJar_missingArchive() {
    Mockito.when(mockMavenProject.getPlugin("org.apache.maven.plugins:maven-jar-plugin"))
        .thenReturn(mockPlugin);
    Mockito.when(mockPlugin.getConfiguration()).thenReturn(pluginConfiguration);

    Assert.assertNull(mavenProjectProperties.getMainClassFromJarPlugin());
  }

  @Test
  public void testGetMainClassFromJar_missingConfiguration() {
    Mockito.when(mockMavenProject.getPlugin("org.apache.maven.plugins:maven-jar-plugin"))
        .thenReturn(mockPlugin);

    Assert.assertNull(mavenProjectProperties.getMainClassFromJarPlugin());
  }

  @Test
  public void testGetMainClassFromJar_missingPlugin() {
    Assert.assertNull(mavenProjectProperties.getMainClassFromJarPlugin());
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
        .thenReturn(mockPlugin);
    Mockito.when(mockPlugin.getConfiguration()).thenReturn(pluginConfiguration);
    Xpp3Dom compilerTarget = new Xpp3Dom("target");
    pluginConfiguration.addChild(compilerTarget);

    compilerTarget.setValue("1.8");
    Assert.assertEquals(8, mavenProjectProperties.getMajorJavaVersion());

    compilerTarget.setValue("1.6");
    Assert.assertEquals(6, mavenProjectProperties.getMajorJavaVersion());

    compilerTarget.setValue("13");
    Assert.assertEquals(13, mavenProjectProperties.getMajorJavaVersion());
  }

  @Test
  public void testValidateBaseImageVersion_compilerPluginRelease() {
    Mockito.when(mockMavenProject.getPlugin("org.apache.maven.plugins:maven-compiler-plugin"))
        .thenReturn(mockPlugin);
    Mockito.when(mockPlugin.getConfiguration()).thenReturn(pluginConfiguration);
    Xpp3Dom compilerRelease = new Xpp3Dom("release");
    pluginConfiguration.addChild(compilerRelease);

    compilerRelease.setValue("1.8");
    Assert.assertEquals(8, mavenProjectProperties.getMajorJavaVersion());

    compilerRelease.setValue("10");
    Assert.assertEquals(10, mavenProjectProperties.getMajorJavaVersion());

    compilerRelease.setValue("13");
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
    BuildContext buildContext = setUpBuildContext("/app", DEFAULT_CONTAINERIZING_MODE);
    ContainerBuilderLayers layers = new ContainerBuilderLayers(buildContext);

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
        layers.dependenciesLayers.get(0).getEntries());
    assertSourcePathsUnordered(
        ImmutableList.of(
            testRepository.artifactPathOnDisk("com.test", "dependencyX", "1.0.0-SNAPSHOT")),
        layers.snapshotsLayers.get(0).getEntries());
    assertSourcePathsUnordered(
        ImmutableList.of(
            applicationDirectory.resolve("output/directory"),
            applicationDirectory.resolve("output/directory/somefile"),
            applicationDirectory.resolve("output/package"),
            applicationDirectory.resolve("output/resourceA"),
            applicationDirectory.resolve("output/resourceB"),
            applicationDirectory.resolve("output/world")),
        layers.resourcesLayers.get(0).getEntries());
    assertSourcePathsUnordered(
        ImmutableList.of(
            applicationDirectory.resolve("output/HelloWorld.class"),
            applicationDirectory.resolve("output/directory"),
            applicationDirectory.resolve("output/package"),
            applicationDirectory.resolve("output/package/some.class"),
            applicationDirectory.resolve("output/some.class")),
        layers.classesLayers.get(0).getEntries());

    assertModificationTime(SAMPLE_FILE_MODIFICATION_TIME, layers.dependenciesLayers);
    assertModificationTime(SAMPLE_FILE_MODIFICATION_TIME, layers.snapshotsLayers);
    assertModificationTime(SAMPLE_FILE_MODIFICATION_TIME, layers.resourcesLayers);
    assertModificationTime(SAMPLE_FILE_MODIFICATION_TIME, layers.classesLayers);
  }

  @Test
  public void testCreateContainerBuilder_nonDefaultAppRoot()
      throws IOException, InvalidImageReferenceException, CacheDirectoryCreationException {
    BuildContext buildContext = setUpBuildContext("/my/app", DEFAULT_CONTAINERIZING_MODE);
    assertNonDefaultAppRoot(buildContext);
  }

  @Test
  public void testCreateContainerBuilder_packagedMode()
      throws InvalidImageReferenceException, IOException, CacheDirectoryCreationException,
          URISyntaxException {
    Path jar = temporaryFolder.newFile("final-name.jar").toPath();
    Mockito.when(mockBuild.getDirectory()).thenReturn(temporaryFolder.getRoot().toString());
    Mockito.when(mockBuild.getFinalName()).thenReturn("final-name");

    BuildContext buildContext = setUpBuildContext("/app-root", ContainerizingMode.PACKAGED);

    ContainerBuilderLayers layers = new ContainerBuilderLayers(buildContext);
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
        layers.dependenciesLayers.get(0).getEntries());
    assertSourcePathsUnordered(
        Arrays.asList(
            testRepository.artifactPathOnDisk("com.test", "dependencyX", "1.0.0-SNAPSHOT")),
        layers.snapshotsLayers.get(0).getEntries());
    assertSourcePathsUnordered(Arrays.asList(jar), layers.extraFilesLayers.get(0).getEntries());

    assertExtractionPathsUnordered(
        Arrays.asList(
            "/app-root/libs/dependency-1.0.0-200.jar",
            "/app-root/libs/dependency-1.0.0-480.jar",
            "/app-root/libs/dependency-1.0.0-770.jar",
            "/app-root/libs/library.jarC.jar",
            "/app-root/libs/libraryA.jar",
            "/app-root/libs/libraryB.jar"),
        layers.dependenciesLayers.get(0).getEntries());
    assertExtractionPathsUnordered(
        Arrays.asList("/app-root/libs/dependencyX-1.0.0-SNAPSHOT.jar"),
        layers.snapshotsLayers.get(0).getEntries());
    assertExtractionPathsUnordered(
        Arrays.asList("/app-root/classpath/final-name.jar"),
        layers.extraFilesLayers.get(0).getEntries());
  }

  @Test
  public void testCreateContainerBuilder_warNonDefaultAppRoot()
      throws URISyntaxException, IOException, InvalidImageReferenceException,
          CacheDirectoryCreationException {
    Path unzipTarget = setUpWar(getResource("maven/webapp/final-name"));

    BuildContext buildContext = setUpBuildContext("/my/app", DEFAULT_CONTAINERIZING_MODE);
    ContainerBuilderLayers layers = new ContainerBuilderLayers(buildContext);
    assertSourcePathsUnordered(
        ImmutableList.of(unzipTarget.resolve("WEB-INF/lib/dependency-1.0.0.jar")),
        layers.dependenciesLayers.get(0).getEntries());
    assertSourcePathsUnordered(
        ImmutableList.of(unzipTarget.resolve("WEB-INF/lib/dependencyX-1.0.0-SNAPSHOT.jar")),
        layers.snapshotsLayers.get(0).getEntries());
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
        layers.resourcesLayers.get(0).getEntries());
    assertSourcePathsUnordered(
        ImmutableList.of(
            unzipTarget.resolve("WEB-INF/classes/HelloWorld.class"),
            unzipTarget.resolve("WEB-INF/classes/empty_dir"),
            unzipTarget.resolve("WEB-INF/classes/package"),
            unzipTarget.resolve("WEB-INF/classes/package/Other.class")),
        layers.classesLayers.get(0).getEntries());

    assertExtractionPathsUnordered(
        Collections.singletonList("/my/app/WEB-INF/lib/dependency-1.0.0.jar"),
        layers.dependenciesLayers.get(0).getEntries());
    assertExtractionPathsUnordered(
        Collections.singletonList("/my/app/WEB-INF/lib/dependencyX-1.0.0-SNAPSHOT.jar"),
        layers.snapshotsLayers.get(0).getEntries());
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
        layers.resourcesLayers.get(0).getEntries());
    assertExtractionPathsUnordered(
        Arrays.asList(
            "/my/app/WEB-INF/classes/HelloWorld.class",
            "/my/app/WEB-INF/classes/empty_dir",
            "/my/app/WEB-INF/classes/package",
            "/my/app/WEB-INF/classes/package/Other.class"),
        layers.classesLayers.get(0).getEntries());
  }

  @Test
  public void testCreateContainerBuilder_jarNonDefaultAppRoot()
      throws IOException, InvalidImageReferenceException, CacheDirectoryCreationException {
    // Test when the default packaging is set
    Mockito.when(mockMavenProject.getPackaging()).thenReturn("jar");
    BuildContext buildContext = setUpBuildContext("/my/app", DEFAULT_CONTAINERIZING_MODE);
    assertNonDefaultAppRoot(buildContext);
  }

  @Test
  public void testCreateContainerBuilder_noErrorIfWebInfDoesNotExist()
      throws IOException, InvalidImageReferenceException, CacheDirectoryCreationException {
    setUpWar(temporaryFolder.newFolder("final-name").toPath());

    setUpBuildContext("/anything", DEFAULT_CONTAINERIZING_MODE); // should pass
  }

  @Test
  public void testCreateContainerBuilder_noErrorIfWebInfLibDoesNotExist()
      throws IOException, InvalidImageReferenceException, CacheDirectoryCreationException {
    temporaryFolder.newFolder("final-name", "WEB-INF", "classes");
    setUpWar(temporaryFolder.getRoot().toPath());

    setUpBuildContext("/anything", DEFAULT_CONTAINERIZING_MODE); // should pass
  }

  @Test
  public void testCreateContainerBuilder_noErrorIfWebInfClassesDoesNotExist()
      throws IOException, InvalidImageReferenceException, CacheDirectoryCreationException {
    temporaryFolder.newFolder("final-name", "WEB-INF", "lib");
    setUpWar(temporaryFolder.getRoot().toPath());

    setUpBuildContext("/anything", DEFAULT_CONTAINERIZING_MODE); // should pass
  }

  @Test
  public void testIsWarProject_warPackagingIsWar() {
    Mockito.when(mockMavenProject.getPackaging()).thenReturn("war");
    Assert.assertTrue(mavenProjectProperties.isWarProject());
  }

  @Test
  public void testIsWarProject_gwtAppPackagingIsWar() {
    Mockito.when(mockMavenProject.getPackaging()).thenReturn("gwt-app");
    Assert.assertTrue(mavenProjectProperties.isWarProject());
  }

  @Test
  public void testIsWarProject_jarPackagingIsNotWar() {
    Mockito.when(mockMavenProject.getPackaging()).thenReturn("jar");
    Assert.assertFalse(mavenProjectProperties.isWarProject());
  }

  @Test
  public void testIsWarProject_gwtLibPackagingIsNotWar() {
    Mockito.when(mockMavenProject.getPackaging()).thenReturn("gwt-lib");
    Assert.assertFalse(mavenProjectProperties.isWarProject());
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
        mavenProjectProperties.classifyDependencies(artifacts, projectArtifacts);

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

  @Test
  public void testGetProjectDependencies() {
    MavenProject rootPomProject = Mockito.mock(MavenProject.class);
    MavenProject jibSubModule = Mockito.mock(MavenProject.class);
    MavenProject sharedLibSubModule = Mockito.mock(MavenProject.class);
    Mockito.when(mockMavenSession.getProjects())
        .thenReturn(Arrays.asList(rootPomProject, sharedLibSubModule, jibSubModule));

    Artifact nullFileArtifact = Mockito.mock(Artifact.class);
    Artifact projectJar = newArtifact("com.test", "my-app", "1.0");
    Artifact sharedLibJar = newArtifact("com.test", "shared-lib", "1.0");

    Mockito.when(rootPomProject.getArtifact()).thenReturn(nullFileArtifact);
    Mockito.when(jibSubModule.getArtifact()).thenReturn(projectJar);
    Mockito.when(sharedLibSubModule.getArtifact()).thenReturn(sharedLibJar);

    Mockito.when(mockMavenProject.getArtifact()).thenReturn(projectJar);

    Assert.assertEquals(
        ImmutableSet.of(sharedLibJar), mavenProjectProperties.getProjectDependencies());
  }

  @Test
  public void testGetChildValue_null() {
    Assert.assertFalse(MavenProjectProperties.getChildValue(null).isPresent());
    Assert.assertFalse(MavenProjectProperties.getChildValue(null, "foo", "bar").isPresent());
  }

  @Test
  public void testGetChildValue_noPathGiven() {
    Xpp3Dom root = newXpp3Dom("root", "value");

    Assert.assertEquals(Optional.of("value"), MavenProjectProperties.getChildValue(root));
  }

  @Test
  public void testGetChildValue_noChild() {
    Xpp3Dom root = newXpp3Dom("root", "value");

    Assert.assertFalse(MavenProjectProperties.getChildValue(root, "foo").isPresent());
    Assert.assertFalse(MavenProjectProperties.getChildValue(root, "foo", "bar").isPresent());
  }

  @Test
  public void testGetChildValue_childPathMatched() {
    Xpp3Dom root = newXpp3Dom("root", "value");
    Xpp3Dom foo = addXpp3DomChild(root, "foo", "foo");
    addXpp3DomChild(foo, "bar", "bar");

    Assert.assertEquals(Optional.of("foo"), MavenProjectProperties.getChildValue(root, "foo"));
    Assert.assertEquals(
        Optional.of("bar"), MavenProjectProperties.getChildValue(root, "foo", "bar"));
    Assert.assertEquals(Optional.of("bar"), MavenProjectProperties.getChildValue(foo, "bar"));
  }

  @Test
  public void testGetChildValue_notFullyMatched() {
    Xpp3Dom root = newXpp3Dom("root", "value");
    Xpp3Dom foo = addXpp3DomChild(root, "foo", "foo");

    addXpp3DomChild(foo, "bar", "bar");
    Assert.assertFalse(MavenProjectProperties.getChildValue(root, "baz").isPresent());
    Assert.assertFalse(MavenProjectProperties.getChildValue(root, "foo", "baz").isPresent());
  }

  @Test
  public void testGetChildValue_nullValue() {
    Xpp3Dom root = new Xpp3Dom("root");
    addXpp3DomChild(root, "foo", null);

    Assert.assertFalse(MavenProjectProperties.getChildValue(root).isPresent());
    Assert.assertFalse(MavenProjectProperties.getChildValue(root, "foo").isPresent());
  }

  @Test
  public void testGetSpringBootRepackageConfiguration_pluginNotApplied() {
    Assert.assertEquals(
        Optional.empty(), mavenProjectProperties.getSpringBootRepackageConfiguration());
  }

  @Test
  public void testGetSpringBootRepackageConfiguration_noConfigurationBlock() {
    Mockito.when(mockMavenProject.getPlugin("org.springframework.boot:spring-boot-maven-plugin"))
        .thenReturn(mockPlugin);
    Mockito.when(mockPlugin.getExecutions()).thenReturn(Arrays.asList(mockPluginExecution));
    Mockito.when(mockPluginExecution.getGoals()).thenReturn(Arrays.asList("repackage"));
    Mockito.when(mockPluginExecution.getConfiguration()).thenReturn(null);
    Assert.assertEquals(
        Optional.of(new Xpp3Dom("configuration")),
        mavenProjectProperties.getSpringBootRepackageConfiguration());
  }

  @Test
  public void testGetSpringBootRepackageConfiguration_noExecutions() {
    Mockito.when(mockMavenProject.getPlugin("org.springframework.boot:spring-boot-maven-plugin"))
        .thenReturn(mockPlugin);
    Mockito.when(mockPlugin.getExecutions()).thenReturn(Collections.emptyList());
    Assert.assertEquals(
        Optional.empty(), mavenProjectProperties.getSpringBootRepackageConfiguration());
  }

  @Test
  public void testGetSpringBootRepackageConfiguration_noRepackageGoal() {
    Mockito.when(mockMavenProject.getPlugin("org.springframework.boot:spring-boot-maven-plugin"))
        .thenReturn(mockPlugin);
    Mockito.when(mockPlugin.getExecutions()).thenReturn(Arrays.asList(mockPluginExecution));
    Mockito.when(mockPluginExecution.getGoals()).thenReturn(Arrays.asList("goal", "foo", "bar"));
    Assert.assertEquals(
        Optional.empty(), mavenProjectProperties.getSpringBootRepackageConfiguration());
  }

  @Test
  public void testGetSpringBootRepackageConfiguration_repackageGoal() {
    Mockito.when(mockMavenProject.getPlugin("org.springframework.boot:spring-boot-maven-plugin"))
        .thenReturn(mockPlugin);
    Mockito.when(mockPlugin.getExecutions()).thenReturn(Arrays.asList(mockPluginExecution));
    Mockito.when(mockPluginExecution.getGoals()).thenReturn(Arrays.asList("goal", "repackage"));
    Mockito.when(mockPluginExecution.getConfiguration()).thenReturn(pluginConfiguration);
    Assert.assertEquals(
        Optional.of(pluginConfiguration),
        mavenProjectProperties.getSpringBootRepackageConfiguration());
  }

  @Test
  public void testGetSpringBootRepackageConfiguration_skipped() {
    Mockito.when(mockMavenProject.getPlugin("org.springframework.boot:spring-boot-maven-plugin"))
        .thenReturn(mockPlugin);
    Mockito.when(mockPlugin.getExecutions()).thenReturn(Arrays.asList(mockPluginExecution));
    Mockito.when(mockPluginExecution.getGoals()).thenReturn(Arrays.asList("repackage"));
    Mockito.when(mockPluginExecution.getConfiguration()).thenReturn(pluginConfiguration);
    addXpp3DomChild(pluginConfiguration, "skip", "true");
    Assert.assertEquals(
        Optional.empty(), mavenProjectProperties.getSpringBootRepackageConfiguration());
  }

  @Test
  public void testGetSpringBootRepackageConfiguration_skipNotTrue() {
    Mockito.when(mockMavenProject.getPlugin("org.springframework.boot:spring-boot-maven-plugin"))
        .thenReturn(mockPlugin);
    Mockito.when(mockPlugin.getExecutions()).thenReturn(Arrays.asList(mockPluginExecution));
    Mockito.when(mockPluginExecution.getGoals()).thenReturn(Arrays.asList("repackage"));
    Mockito.when(mockPluginExecution.getConfiguration()).thenReturn(pluginConfiguration);
    addXpp3DomChild(pluginConfiguration, "skip", null);
    Assert.assertEquals(
        Optional.of(pluginConfiguration),
        mavenProjectProperties.getSpringBootRepackageConfiguration());
  }

  @Test
  public void testGetJarArtifact() throws IOException {
    Mockito.when(mockBuild.getDirectory()).thenReturn(Paths.get("/foo/bar").toString());
    Mockito.when(mockBuild.getFinalName()).thenReturn("helloworld-1");

    Assert.assertEquals(
        Paths.get("/foo/bar/helloworld-1.jar"), mavenProjectProperties.getJarArtifact());
  }

  @Test
  public void testGetJarArtifact_outputDirectoryFromJarPlugin() throws IOException {
    Mockito.when(mockMavenProject.getBasedir()).thenReturn(new File("/should/ignore"));
    Mockito.when(mockBuild.getDirectory()).thenReturn("/should/ignore");
    Mockito.when(mockBuild.getFinalName()).thenReturn("helloworld-1");

    Mockito.when(mockMavenProject.getPlugin("org.apache.maven.plugins:maven-jar-plugin"))
        .thenReturn(mockPlugin);
    Mockito.when(mockPlugin.getExecutions()).thenReturn(Arrays.asList(mockPluginExecution));
    Mockito.when(mockPluginExecution.getId()).thenReturn("default-jar");
    Mockito.when(mockPluginExecution.getConfiguration()).thenReturn(pluginConfiguration);
    addXpp3DomChild(pluginConfiguration, "outputDirectory", Paths.get("/jar/out").toString());

    Assert.assertEquals(
        Paths.get("/jar/out/helloworld-1.jar"), mavenProjectProperties.getJarArtifact());
  }

  @Test
  public void testGetJarArtifact_relativeOutputDirectoryFromJarPlugin() throws IOException {
    Mockito.when(mockMavenProject.getBasedir()).thenReturn(new File("/base/dir"));
    Mockito.when(mockBuild.getDirectory()).thenReturn(temporaryFolder.getRoot().toString());
    Mockito.when(mockBuild.getFinalName()).thenReturn("helloworld-1");

    Mockito.when(mockMavenProject.getPlugin("org.apache.maven.plugins:maven-jar-plugin"))
        .thenReturn(mockPlugin);
    Mockito.when(mockPlugin.getExecutions()).thenReturn(Arrays.asList(mockPluginExecution));
    Mockito.when(mockPluginExecution.getId()).thenReturn("default-jar");
    Mockito.when(mockPluginExecution.getConfiguration()).thenReturn(pluginConfiguration);
    addXpp3DomChild(pluginConfiguration, "outputDirectory", Paths.get("relative").toString());

    Assert.assertEquals(
        Paths.get("/base/dir/relative/helloworld-1.jar"), mavenProjectProperties.getJarArtifact());
  }

  @Test
  public void testGetJarArtifact_classifier() throws IOException {
    Mockito.when(mockBuild.getDirectory()).thenReturn(Paths.get("/foo/bar").toString());
    Mockito.when(mockBuild.getFinalName()).thenReturn("helloworld-1");

    Mockito.when(mockMavenProject.getPlugin("org.apache.maven.plugins:maven-jar-plugin"))
        .thenReturn(mockPlugin);
    Mockito.when(mockPlugin.getExecutions()).thenReturn(Arrays.asList(mockPluginExecution));
    Mockito.when(mockPluginExecution.getId()).thenReturn("default-jar");
    Mockito.when(mockPluginExecution.getConfiguration()).thenReturn(pluginConfiguration);
    addXpp3DomChild(pluginConfiguration, "classifier", "a-class");

    Assert.assertEquals(
        Paths.get("/foo/bar/helloworld-1-a-class.jar"), mavenProjectProperties.getJarArtifact());
  }

  @Test
  public void testGetJarArtifact_executionIdNotMatched() throws IOException {
    Mockito.when(mockBuild.getDirectory()).thenReturn(Paths.get("/foo/bar").toString());
    Mockito.when(mockBuild.getFinalName()).thenReturn("helloworld-1");

    Mockito.when(mockMavenProject.getPlugin("org.apache.maven.plugins:maven-jar-plugin"))
        .thenReturn(mockPlugin);
    Mockito.when(mockPlugin.getExecutions()).thenReturn(Arrays.asList(mockPluginExecution));
    Mockito.when(mockPluginExecution.getId()).thenReturn("no-id-match");
    Mockito.lenient().when(mockPluginExecution.getConfiguration()).thenReturn(pluginConfiguration);
    addXpp3DomChild(pluginConfiguration, "outputDirectory", "/should/ignore");
    addXpp3DomChild(pluginConfiguration, "classifier", "a-class");

    Assert.assertEquals(
        Paths.get("/foo/bar/helloworld-1.jar"), mavenProjectProperties.getJarArtifact());
  }

  @Test
  public void testGetJarArtifact_originalJarCopiedIfSpringBoot() throws IOException {
    temporaryFolder.newFile("helloworld-1.jar.original");
    Mockito.when(mockBuild.getDirectory()).thenReturn(temporaryFolder.getRoot().toString());
    Mockito.when(mockBuild.getFinalName()).thenReturn("helloworld-1");

    setUpSpringBootFatJar();
    Path tempDirectory = temporaryFolder.newFolder("tmp").toPath();
    Mockito.when(mockTempDirectoryProvider.newDirectory()).thenReturn(tempDirectory);

    Assert.assertEquals(
        tempDirectory.resolve("helloworld-1.original.jar"),
        mavenProjectProperties.getJarArtifact());

    mavenProjectProperties.waitForLoggingThread();
    Mockito.verify(mockLog)
        .info("Spring Boot repackaging (fat JAR) detected; using the original JAR");
  }

  @Test
  public void testGetJarArtifact_originalJarIfSpringBoot_differentDirectories() throws IOException {
    Mockito.when(mockMavenProject.getBasedir()).thenReturn(new File("/should/ignore"));
    Mockito.when(mockBuild.getDirectory()).thenReturn("/should/ignore");
    Mockito.when(mockBuild.getFinalName()).thenReturn("helloworld-1");

    Mockito.when(mockMavenProject.getPlugin("org.apache.maven.plugins:maven-jar-plugin"))
        .thenReturn(mockPlugin);
    Mockito.when(mockPlugin.getExecutions()).thenReturn(Arrays.asList(mockPluginExecution));
    Mockito.when(mockPluginExecution.getId()).thenReturn("default-jar");
    Mockito.when(mockPluginExecution.getConfiguration()).thenReturn(pluginConfiguration);
    addXpp3DomChild(pluginConfiguration, "outputDirectory", Paths.get("/jar/out").toString());

    setUpSpringBootFatJar();

    Assert.assertEquals(
        Paths.get("/jar/out/helloworld-1.jar"), mavenProjectProperties.getJarArtifact());

    mavenProjectProperties.waitForLoggingThread();
    Mockito.verify(mockLog)
        .info("Spring Boot repackaging (fat JAR) detected; using the original JAR");
  }

  @Test
  public void testGetJarArtifact_originalJarIfSpringBoot_differentFinalNames() throws IOException {
    Path buildDirectory = temporaryFolder.newFolder("target").toPath();
    Files.createFile(buildDirectory.resolve("helloworld-1.jar"));
    Mockito.when(mockMavenProject.getBasedir()).thenReturn(temporaryFolder.getRoot());
    Mockito.when(mockBuild.getDirectory()).thenReturn(buildDirectory.toString());
    Mockito.when(mockBuild.getFinalName()).thenReturn("helloworld-1");

    Mockito.when(mockMavenProject.getPlugin("org.apache.maven.plugins:maven-jar-plugin"))
        .thenReturn(mockPlugin);
    Mockito.when(mockPlugin.getExecutions()).thenReturn(Arrays.asList(mockPluginExecution));
    Mockito.when(mockPluginExecution.getId()).thenReturn("default-jar");
    Mockito.when(mockPluginExecution.getConfiguration()).thenReturn(pluginConfiguration);
    addXpp3DomChild(pluginConfiguration, "outputDirectory", "target");

    Xpp3Dom bootPluginConfiguration = setUpSpringBootFatJar();
    addXpp3DomChild(bootPluginConfiguration, "finalName", "boot-helloworld-1");

    Assert.assertEquals(
        buildDirectory.resolve("helloworld-1.jar"), mavenProjectProperties.getJarArtifact());

    mavenProjectProperties.waitForLoggingThread();
    Mockito.verify(mockLog)
        .info("Spring Boot repackaging (fat JAR) detected; using the original JAR");
  }

  @Test
  public void testGetJarArtifact_originalJarIfSpringBoot_differentClassifier() throws IOException {
    Path buildDirectory = temporaryFolder.newFolder("target").toPath();
    Files.createFile(buildDirectory.resolve("helloworld-1.jar"));
    Mockito.when(mockMavenProject.getBasedir()).thenReturn(temporaryFolder.getRoot());
    Mockito.when(mockBuild.getDirectory()).thenReturn(buildDirectory.toString());
    Mockito.when(mockBuild.getFinalName()).thenReturn("helloworld-1");

    Mockito.when(mockMavenProject.getPlugin("org.apache.maven.plugins:maven-jar-plugin"))
        .thenReturn(mockPlugin);
    Mockito.when(mockPlugin.getExecutions()).thenReturn(Arrays.asList(mockPluginExecution));
    Mockito.when(mockPluginExecution.getId()).thenReturn("default-jar");
    Mockito.when(mockPluginExecution.getConfiguration()).thenReturn(pluginConfiguration);
    addXpp3DomChild(pluginConfiguration, "outputDirectory", "target");

    Xpp3Dom bootPluginConfiguration = setUpSpringBootFatJar();
    addXpp3DomChild(bootPluginConfiguration, "classifier", "boot-class");

    Assert.assertEquals(
        buildDirectory.resolve("helloworld-1.jar"), mavenProjectProperties.getJarArtifact());

    mavenProjectProperties.waitForLoggingThread();
    Mockito.verify(mockLog)
        .info("Spring Boot repackaging (fat JAR) detected; using the original JAR");
  }

  @Test
  public void testGetJarArtifact_originalJarCopiedIfSpringBoot_sameDirectory() throws IOException {
    Path buildDirectory = temporaryFolder.newFolder("target").toPath();
    Files.createFile(buildDirectory.resolve("helloworld-1.jar.original"));
    Mockito.when(mockMavenProject.getBasedir()).thenReturn(temporaryFolder.getRoot());
    Mockito.when(mockBuild.getDirectory()).thenReturn(buildDirectory.toString());
    Mockito.when(mockBuild.getFinalName()).thenReturn("helloworld-1");

    Mockito.when(mockMavenProject.getPlugin("org.apache.maven.plugins:maven-jar-plugin"))
        .thenReturn(mockPlugin);
    Mockito.when(mockPlugin.getExecutions()).thenReturn(Arrays.asList(mockPluginExecution));
    Mockito.when(mockPluginExecution.getId()).thenReturn("default-jar");
    Mockito.when(mockPluginExecution.getConfiguration()).thenReturn(pluginConfiguration);
    addXpp3DomChild(pluginConfiguration, "outputDirectory", "target");

    setUpSpringBootFatJar();
    Path tempDirectory = temporaryFolder.newFolder("tmp").toPath();
    Mockito.when(mockTempDirectoryProvider.newDirectory()).thenReturn(tempDirectory);

    Assert.assertEquals(
        tempDirectory.resolve("helloworld-1.original.jar"),
        mavenProjectProperties.getJarArtifact());

    mavenProjectProperties.waitForLoggingThread();
    Mockito.verify(mockLog)
        .info("Spring Boot repackaging (fat JAR) detected; using the original JAR");
  }

  @Test
  public void testGetWarArtifact() {
    Mockito.when(mockBuild.getDirectory()).thenReturn(Paths.get("/foo/bar").toString());
    Mockito.when(mockBuild.getFinalName()).thenReturn("helloworld-1");

    Assert.assertEquals(
        Paths.get("/foo/bar/helloworld-1.war"), mavenProjectProperties.getWarArtifact());
  }

  @Test
  public void testGetWarArtifact_warNameProperty() {
    Mockito.when(mockBuild.getDirectory()).thenReturn(Paths.get("/foo/bar").toString());
    Mockito.when(mockBuild.getFinalName()).thenReturn("helloworld-1");

    Mockito.when(mockMavenProject.getPlugin("org.apache.maven.plugins:maven-war-plugin"))
        .thenReturn(mockPlugin);
    Mockito.when(mockPlugin.getExecutions()).thenReturn(Arrays.asList(mockPluginExecution));
    Mockito.when(mockPluginExecution.getId()).thenReturn("default-war");
    Mockito.when(mockPluginExecution.getConfiguration()).thenReturn(pluginConfiguration);
    addXpp3DomChild(pluginConfiguration, "warName", "baz");

    Assert.assertEquals(Paths.get("/foo/bar/baz.war"), mavenProjectProperties.getWarArtifact());
  }

  @Test
  public void testGetWarArtifact_noWarNameProperty() {
    Mockito.when(mockBuild.getDirectory()).thenReturn(Paths.get("/foo/bar").toString());
    Mockito.when(mockBuild.getFinalName()).thenReturn("helloworld-1");

    Mockito.when(mockMavenProject.getPlugin("org.apache.maven.plugins:maven-war-plugin"))
        .thenReturn(mockPlugin);
    Mockito.when(mockPlugin.getExecutions()).thenReturn(Arrays.asList(mockPluginExecution));
    Mockito.when(mockPluginExecution.getId()).thenReturn("default-war");
    Mockito.lenient().when(mockPluginExecution.getConfiguration()).thenReturn(pluginConfiguration);

    Assert.assertEquals(
        Paths.get("/foo/bar/helloworld-1.war"), mavenProjectProperties.getWarArtifact());
  }

  @Test
  public void testGetWarArtifact_executionIdNotMatched() {
    Mockito.when(mockBuild.getDirectory()).thenReturn(Paths.get("/foo/bar").toString());
    Mockito.when(mockBuild.getFinalName()).thenReturn("helloworld-1");

    Mockito.when(mockMavenProject.getPlugin("org.apache.maven.plugins:maven-war-plugin"))
        .thenReturn(mockPlugin);
    Mockito.when(mockPlugin.getExecutions()).thenReturn(Arrays.asList(mockPluginExecution));
    Mockito.when(mockPluginExecution.getId()).thenReturn("no-id-match");
    Mockito.lenient().when(mockPluginExecution.getConfiguration()).thenReturn(pluginConfiguration);
    addXpp3DomChild(pluginConfiguration, "warName", "baz");

    Assert.assertEquals(
        Paths.get("/foo/bar/helloworld-1.war"), mavenProjectProperties.getWarArtifact());
  }

  @Test
  public void testGetDependencies() throws URISyntaxException {
    Assert.assertEquals(
        Arrays.asList(
            getResource("maven/application/dependencies/library.jarC.jar"),
            getResource("maven/application/dependencies/libraryB.jar"),
            getResource("maven/application/dependencies/libraryA.jar"),
            getResource("maven/application/dependencies/more/dependency-1.0.0.jar"),
            getResource("maven/application/dependencies/another/one/dependency-1.0.0.jar"),
            testRepository.artifactPathOnDisk("com.test", "dependency", "1.0.0"),
            testRepository.artifactPathOnDisk("com.test", "dependencyX", "1.0.0-SNAPSHOT")),
        mavenProjectProperties.getDependencies());
  }

  private BuildContext setUpBuildContext(String appRoot, ContainerizingMode containerizingMode)
      throws InvalidImageReferenceException, IOException, CacheDirectoryCreationException {
    JavaContainerBuilder javaContainerBuilder =
        JavaContainerBuilder.from(RegistryImage.named("base"))
            .setAppRoot(AbsoluteUnixPath.get(appRoot))
            .setModificationTimeProvider((ignored1, ignored2) -> SAMPLE_FILE_MODIFICATION_TIME);
    JibContainerBuilder jibContainerBuilder =
        mavenProjectProperties.createJibContainerBuilder(javaContainerBuilder, containerizingMode);
    return JibContainerBuilderTestHelper.toBuildContext(
        jibContainerBuilder, Containerizer.to(RegistryImage.named("to")));
  }

  private Path setUpWar(Path explodedWar) throws IOException {
    Path fakeMavenBuildDirectory = temporaryFolder.getRoot().toPath();
    Mockito.when(mockBuild.getDirectory()).thenReturn(fakeMavenBuildDirectory.toString());
    Mockito.when(mockBuild.getFinalName()).thenReturn("final-name");
    Mockito.when(mockMavenProject.getPackaging()).thenReturn("war");

    zipUpDirectory(explodedWar, fakeMavenBuildDirectory.resolve("final-name.war"));

    // Make "MavenProjectProperties" use this folder to explode the WAR into.
    Path unzipTarget = temporaryFolder.newFolder("exploded").toPath();
    Mockito.when(mockTempDirectoryProvider.newDirectory()).thenReturn(unzipTarget);
    return unzipTarget;
  }

  private Xpp3Dom setUpSpringBootFatJar() {
    Xpp3Dom pluginConfiguration = new Xpp3Dom("configuration");
    PluginExecution execution = Mockito.mock(PluginExecution.class);
    Plugin plugin = Mockito.mock(Plugin.class);
    Mockito.when(mockMavenProject.getPlugin("org.springframework.boot:spring-boot-maven-plugin"))
        .thenReturn(plugin);
    Mockito.when(plugin.getExecutions()).thenReturn(Arrays.asList(execution));
    Mockito.when(execution.getGoals()).thenReturn(Arrays.asList("repackage"));
    Mockito.when(execution.getConfiguration()).thenReturn(pluginConfiguration);
    return pluginConfiguration;
  }
}
