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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.google.common.collect.ImmutableSet;
import com.google.common.io.ByteStreams;
import com.google.common.io.Resources;
import com.google.common.truth.Correspondence;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.StringJoiner;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** Test for {@link MavenProjectProperties}. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MavenProjectPropertiesTest {

  private static final Correspondence<FileEntry, Path> SOURCE_FILE_OF =
      Correspondence.transforming(FileEntry::getSourceFile, "has sourceFile of");
  private static final Correspondence<FileEntry, String> EXTRACTION_PATH_OF =
      Correspondence.transforming(
          entry -> entry.getExtractionPath().toString(), "has extractionPath of");

  private static final Instant EPOCH_PLUS_32 = Instant.ofEpochSecond(32);

  /** Helper for reading back layers in a {@link BuildContext}. */
  private static class ContainerBuilderLayers {

    @Nullable private final FileEntriesLayer resourcesLayer;
    @Nullable private final FileEntriesLayer classesLayer;
    @Nullable private final FileEntriesLayer dependenciesLayer;
    @Nullable private final FileEntriesLayer snapshotsLayer;
    @Nullable private final FileEntriesLayer extraFilesLayer;

    private ContainerBuilderLayers(BuildContext buildContext) {
      resourcesLayer = getLayerByName(buildContext, LayerType.RESOURCES.getName());
      classesLayer = getLayerByName(buildContext, LayerType.CLASSES.getName());
      dependenciesLayer = getLayerByName(buildContext, LayerType.DEPENDENCIES.getName());
      snapshotsLayer = getLayerByName(buildContext, LayerType.SNAPSHOT_DEPENDENCIES.getName());
      extraFilesLayer = getLayerByName(buildContext, LayerType.EXTRA_FILES.getName());
    }

    @Nullable
    private static FileEntriesLayer getLayerByName(BuildContext buildContext, String name) {
      List<FileEntriesLayer> layers = buildContext.getLayerConfigurations();
      return layers.stream().filter(layer -> layer.getName().equals(name)).findFirst().orElse(null);
    }
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
    Artifact artifact = mock(Artifact.class);
    when(artifact.getFile()).thenReturn(sourceJar.toFile());
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

  @RegisterExtension public TestRepository testRepository = new TestRepository();
  @TempDir public Path temporaryFolder;

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
  @Mock private JavaContainerBuilder mockJavaContainerBuilder;

  private MavenProjectProperties mavenProjectProperties;

  @BeforeEach
  void setUpBefore() throws IOException, URISyntaxException {
    when(mockLog.isDebugEnabled()).thenReturn(true);
    when(mockLog.isWarnEnabled()).thenReturn(true);
    when(mockLog.isErrorEnabled()).thenReturn(true);

    when(mockMavenSession.getRequest()).thenReturn(mockMavenRequest);
    mavenProjectProperties =
        new MavenProjectProperties(
            mockJibPluginDescriptor,
            mockMavenProject,
            mockMavenSession,
            mockLog,
            mockTempDirectoryProvider,
            Collections.emptyList(),
            mockExtensionLoader);

    Path outputPath = getResource("maven/application/output");
    Path dependenciesPath = getResource("maven/application/dependencies");

    when(mockMavenProject.getBuild()).thenReturn(mockBuild);
    when(mockBuild.getOutputDirectory()).thenReturn(outputPath.toString());

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
    when(mockMavenProject.getArtifacts()).thenReturn(artifacts);

    Path emptyDirectory =
        getResource("maven/webapp").resolve("final-name/WEB-INF/classes/empty_dir");
    Files.createDirectories(emptyDirectory);

    when(mockMavenProject.getProperties()).thenReturn(mockMavenProperties);
  }

  @Test
  void testGetMainClassFromJar_success() {
    when(mockMavenProject.getPlugin("org.apache.maven.plugins:maven-jar-plugin"))
        .thenReturn(mockPlugin);
    when(mockPlugin.getConfiguration()).thenReturn(pluginConfiguration);
    Xpp3Dom archive = new Xpp3Dom("archive");
    Xpp3Dom manifest = new Xpp3Dom("manifest");
    pluginConfiguration.addChild(archive);
    archive.addChild(manifest);
    manifest.addChild(newXpp3Dom("mainClass", "some.main.class"));

    assertThat(mavenProjectProperties.getMainClassFromJarPlugin()).isEqualTo("some.main.class");
  }

  @Test
  void testGetMainClassFromJar_missingMainClass() {
    when(mockMavenProject.getPlugin("org.apache.maven.plugins:maven-jar-plugin"))
        .thenReturn(mockPlugin);
    when(mockPlugin.getConfiguration()).thenReturn(pluginConfiguration);
    Xpp3Dom archive = new Xpp3Dom("archive");
    archive.addChild(new Xpp3Dom("manifest"));
    pluginConfiguration.addChild(archive);

    assertThat(mavenProjectProperties.getMainClassFromJarPlugin()).isNull();
  }

  @Test
  void testGetMainClassFromJar_missingManifest() {
    when(mockMavenProject.getPlugin("org.apache.maven.plugins:maven-jar-plugin"))
        .thenReturn(mockPlugin);
    when(mockPlugin.getConfiguration()).thenReturn(pluginConfiguration);
    pluginConfiguration.addChild(new Xpp3Dom("archive"));

    assertThat(mavenProjectProperties.getMainClassFromJarPlugin()).isNull();
  }

  @Test
  void testGetMainClassFromJar_missingArchive() {
    when(mockMavenProject.getPlugin("org.apache.maven.plugins:maven-jar-plugin"))
        .thenReturn(mockPlugin);
    when(mockPlugin.getConfiguration()).thenReturn(pluginConfiguration);

    assertThat(mavenProjectProperties.getMainClassFromJarPlugin()).isNull();
  }

  @Test
  void testGetMainClassFromJar_missingConfiguration() {
    when(mockMavenProject.getPlugin("org.apache.maven.plugins:maven-jar-plugin"))
        .thenReturn(mockPlugin);

    assertThat(mavenProjectProperties.getMainClassFromJarPlugin()).isNull();
  }

  @Test
  void testGetMainClassFromJar_missingPlugin() {
    assertThat(mavenProjectProperties.getMainClassFromJarPlugin()).isNull();
  }

  @Test
  void testIsWarProject() {
    assertThat(mavenProjectProperties.isWarProject()).isFalse();
  }

  @Test
  void testGetVersionFromString() {
    assertThat(MavenProjectProperties.getVersionFromString("1.8")).isEqualTo(8);
    assertThat(MavenProjectProperties.getVersionFromString("1.8.0_123")).isEqualTo(8);
    assertThat(MavenProjectProperties.getVersionFromString("11")).isEqualTo(11);
    assertThat(MavenProjectProperties.getVersionFromString("11.0.1")).isEqualTo(11);

    assertThat(MavenProjectProperties.getVersionFromString("asdfasdf")).isEqualTo(0);
    assertThat(MavenProjectProperties.getVersionFromString("")).isEqualTo(0);
    assertThat(MavenProjectProperties.getVersionFromString("11abc")).isEqualTo(0);
    assertThat(MavenProjectProperties.getVersionFromString("1.abc")).isEqualTo(0);
  }

  @Test
  void testGetMajorJavaVersion_undefinedDefaultsTo6() {
    assertThat(mavenProjectProperties.getMajorJavaVersion()).isEqualTo(6);
  }

  @Test
  void testGetMajorJavaVersion_targetProperty() {
    when(mockMavenProperties.getProperty("maven.compiler.target")).thenReturn("1.8");
    assertThat(mavenProjectProperties.getMajorJavaVersion()).isEqualTo(8);

    when(mockMavenProperties.getProperty("maven.compiler.target")).thenReturn("1.7");
    assertThat(mavenProjectProperties.getMajorJavaVersion()).isEqualTo(7);

    when(mockMavenProperties.getProperty("maven.compiler.target")).thenReturn("11");
    assertThat(mavenProjectProperties.getMajorJavaVersion()).isEqualTo(11);
  }

  @Test
  void testValidateBaseImageVersion_releaseProperty() {
    when(mockMavenProperties.getProperty("maven.compiler.release")).thenReturn("1.8");
    assertThat(mavenProjectProperties.getMajorJavaVersion()).isEqualTo(8);

    when(mockMavenProperties.getProperty("maven.compiler.release")).thenReturn("1.7");
    assertThat(mavenProjectProperties.getMajorJavaVersion()).isEqualTo(7);

    when(mockMavenProperties.getProperty("maven.compiler.release")).thenReturn("9");
    assertThat(mavenProjectProperties.getMajorJavaVersion()).isEqualTo(9);
  }

  @Test
  void testValidateBaseImageVersion_compilerPluginTarget() {
    when(mockMavenProject.getPlugin("org.apache.maven.plugins:maven-compiler-plugin"))
        .thenReturn(mockPlugin);
    when(mockPlugin.getConfiguration()).thenReturn(pluginConfiguration);
    Xpp3Dom compilerTarget = new Xpp3Dom("target");
    pluginConfiguration.addChild(compilerTarget);

    compilerTarget.setValue("1.8");
    assertThat(mavenProjectProperties.getMajorJavaVersion()).isEqualTo(8);

    compilerTarget.setValue("1.6");
    assertThat(mavenProjectProperties.getMajorJavaVersion()).isEqualTo(6);

    compilerTarget.setValue("13");
    assertThat(mavenProjectProperties.getMajorJavaVersion()).isEqualTo(13);
  }

  @Test
  void testValidateBaseImageVersion_compilerPluginRelease() {
    when(mockMavenProject.getPlugin("org.apache.maven.plugins:maven-compiler-plugin"))
        .thenReturn(mockPlugin);
    when(mockPlugin.getConfiguration()).thenReturn(pluginConfiguration);
    Xpp3Dom compilerRelease = new Xpp3Dom("release");
    pluginConfiguration.addChild(compilerRelease);

    compilerRelease.setValue("1.8");
    assertThat(mavenProjectProperties.getMajorJavaVersion()).isEqualTo(8);

    compilerRelease.setValue("10");
    assertThat(mavenProjectProperties.getMajorJavaVersion()).isEqualTo(10);
  }

  @Test
  void isProgressFooterEnabled() {
    when(mockMavenRequest.isInteractiveMode()).thenReturn(false);
    assertThat(MavenProjectProperties.isProgressFooterEnabled(mockMavenSession)).isFalse();
  }

  @Test
  void testCreateContainerBuilder_correctFiles()
      throws URISyntaxException, IOException, InvalidImageReferenceException,
          CacheDirectoryCreationException {
    ContainerBuilderLayers layers = new ContainerBuilderLayers(setUpBuildContext());

    Path dependenciesPath = getResource("maven/application/dependencies");
    Path applicationDirectory = getResource("maven/application");
    assertThat(layers.dependenciesLayer.getEntries())
        .comparingElementsUsing(SOURCE_FILE_OF)
        .containsExactly(
            testRepository.artifactPathOnDisk("com.test", "dependency", "1.0.0"),
            dependenciesPath.resolve("more/dependency-1.0.0.jar"),
            dependenciesPath.resolve("another/one/dependency-1.0.0.jar"),
            dependenciesPath.resolve("libraryA.jar"),
            dependenciesPath.resolve("libraryB.jar"),
            dependenciesPath.resolve("library.jarC.jar"));
    assertThat(layers.snapshotsLayer.getEntries())
        .comparingElementsUsing(SOURCE_FILE_OF)
        .containsExactly(
            testRepository.artifactPathOnDisk("com.test", "dependencyX", "1.0.0-SNAPSHOT"));
    assertThat(layers.resourcesLayer.getEntries())
        .comparingElementsUsing(SOURCE_FILE_OF)
        .containsExactly(
            applicationDirectory.resolve("output/directory"),
            applicationDirectory.resolve("output/directory/somefile"),
            applicationDirectory.resolve("output/package"),
            applicationDirectory.resolve("output/resourceA"),
            applicationDirectory.resolve("output/resourceB"),
            applicationDirectory.resolve("output/world"));
    assertThat(layers.classesLayer.getEntries())
        .comparingElementsUsing(SOURCE_FILE_OF)
        .containsExactly(
            applicationDirectory.resolve("output/HelloWorld.class"),
            applicationDirectory.resolve("output/directory"),
            applicationDirectory.resolve("output/package"),
            applicationDirectory.resolve("output/package/some.class"),
            applicationDirectory.resolve("output/some.class"));

    List<FileEntry> allFileEntries = new ArrayList<>();
    allFileEntries.addAll(layers.dependenciesLayer.getEntries());
    allFileEntries.addAll(layers.snapshotsLayer.getEntries());
    allFileEntries.addAll(layers.resourcesLayer.getEntries());
    allFileEntries.addAll(layers.classesLayer.getEntries());
    Set<Instant> modificationTimes =
        allFileEntries.stream().map(FileEntry::getModificationTime).collect(Collectors.toSet());
    assertThat(modificationTimes).containsExactly(EPOCH_PLUS_32);
  }

  @Test
  void testCreateContainerBuilder_packagedMode()
      throws InvalidImageReferenceException, IOException, CacheDirectoryCreationException,
          URISyntaxException {
    File f = new File(temporaryFolder.toFile(), "final-name.jar");
    f.createNewFile();
    Path jar = f.toPath();
    when(mockBuild.getDirectory()).thenReturn(temporaryFolder.toString());
    when(mockBuild.getFinalName()).thenReturn("final-name");

    ContainerBuilderLayers layers =
        new ContainerBuilderLayers(setUpBuildContext(ContainerizingMode.PACKAGED));

    Path dependenciesPath = getResource("maven/application/dependencies");
    assertThat(layers.dependenciesLayer).isNotNull();
    assertThat(layers.dependenciesLayer.getEntries())
        .comparingElementsUsing(SOURCE_FILE_OF)
        .containsExactly(
            testRepository.artifactPathOnDisk("com.test", "dependency", "1.0.0"),
            dependenciesPath.resolve("more/dependency-1.0.0.jar"),
            dependenciesPath.resolve("another/one/dependency-1.0.0.jar"),
            dependenciesPath.resolve("libraryA.jar"),
            dependenciesPath.resolve("libraryB.jar"),
            dependenciesPath.resolve("library.jarC.jar"));
    assertThat(layers.snapshotsLayer).isNotNull();
    assertThat(layers.snapshotsLayer.getEntries())
        .comparingElementsUsing(SOURCE_FILE_OF)
        .containsExactly(
            testRepository.artifactPathOnDisk("com.test", "dependencyX", "1.0.0-SNAPSHOT"));
    assertThat(layers.extraFilesLayer).isNotNull();
    assertThat(layers.extraFilesLayer.getEntries())
        .comparingElementsUsing(SOURCE_FILE_OF)
        .containsExactly(jar);
    assertThat(layers.resourcesLayer).isNull();
    assertThat(layers.classesLayer).isNull();

    assertThat(layers.dependenciesLayer.getEntries())
        .comparingElementsUsing(EXTRACTION_PATH_OF)
        .containsExactly(
            "/my/app/libs/dependency-1.0.0-200.jar",
            "/my/app/libs/dependency-1.0.0-480.jar",
            "/my/app/libs/dependency-1.0.0-770.jar",
            "/my/app/libs/library.jarC.jar",
            "/my/app/libs/libraryA.jar",
            "/my/app/libs/libraryB.jar");
    assertThat(layers.snapshotsLayer.getEntries())
        .comparingElementsUsing(EXTRACTION_PATH_OF)
        .containsExactly("/my/app/libs/dependencyX-1.0.0-SNAPSHOT.jar");
    assertThat(layers.extraFilesLayer.getEntries())
        .comparingElementsUsing(EXTRACTION_PATH_OF)
        .containsExactly("/my/app/classpath/final-name.jar");
  }

  @Test
  void testCreateContainerBuilder_war_correctSourceFilePaths()
      throws URISyntaxException, IOException, InvalidImageReferenceException,
          CacheDirectoryCreationException {
    Path unzipTarget = setUpWar(getResource("maven/webapp/final-name"));

    ContainerBuilderLayers layers = new ContainerBuilderLayers(setUpBuildContext());
    assertThat(layers.dependenciesLayer.getEntries())
        .comparingElementsUsing(SOURCE_FILE_OF)
        .containsExactly(unzipTarget.resolve("WEB-INF/lib/dependency-1.0.0.jar"));
    assertThat(layers.snapshotsLayer.getEntries())
        .comparingElementsUsing(SOURCE_FILE_OF)
        .containsExactly(unzipTarget.resolve("WEB-INF/lib/dependencyX-1.0.0-SNAPSHOT.jar"));
    assertThat(layers.resourcesLayer.getEntries())
        .comparingElementsUsing(SOURCE_FILE_OF)
        .containsExactly(
            unzipTarget.resolve("META-INF"),
            unzipTarget.resolve("META-INF/context.xml"),
            unzipTarget.resolve("Test.jsp"),
            unzipTarget.resolve("WEB-INF"),
            unzipTarget.resolve("WEB-INF/classes"),
            unzipTarget.resolve("WEB-INF/classes/empty_dir"),
            unzipTarget.resolve("WEB-INF/classes/package"),
            unzipTarget.resolve("WEB-INF/classes/package/test.properties"),
            unzipTarget.resolve("WEB-INF/lib"),
            unzipTarget.resolve("WEB-INF/web.xml"));
    assertThat(layers.classesLayer.getEntries())
        .comparingElementsUsing(SOURCE_FILE_OF)
        .containsExactly(
            unzipTarget.resolve("WEB-INF/classes/HelloWorld.class"),
            unzipTarget.resolve("WEB-INF/classes/empty_dir"),
            unzipTarget.resolve("WEB-INF/classes/package"),
            unzipTarget.resolve("WEB-INF/classes/package/Other.class"));
  }

  @Test
  void testCreateContainerBuilder_war_correctExtractionPaths()
      throws URISyntaxException, IOException, InvalidImageReferenceException,
          CacheDirectoryCreationException {
    setUpWar(getResource("maven/webapp/final-name"));

    ContainerBuilderLayers layers = new ContainerBuilderLayers(setUpBuildContext());
    assertThat(layers.dependenciesLayer.getEntries())
        .comparingElementsUsing(EXTRACTION_PATH_OF)
        .containsExactly("/my/app/WEB-INF/lib/dependency-1.0.0.jar");
    assertThat(layers.snapshotsLayer.getEntries())
        .comparingElementsUsing(EXTRACTION_PATH_OF)
        .containsExactly("/my/app/WEB-INF/lib/dependencyX-1.0.0-SNAPSHOT.jar");
    assertThat(layers.resourcesLayer.getEntries())
        .comparingElementsUsing(EXTRACTION_PATH_OF)
        .containsExactly(
            "/my/app/META-INF",
            "/my/app/META-INF/context.xml",
            "/my/app/Test.jsp",
            "/my/app/WEB-INF",
            "/my/app/WEB-INF/classes",
            "/my/app/WEB-INF/classes/empty_dir",
            "/my/app/WEB-INF/classes/package",
            "/my/app/WEB-INF/classes/package/test.properties",
            "/my/app/WEB-INF/lib",
            "/my/app/WEB-INF/web.xml");
    assertThat(layers.classesLayer.getEntries())
        .comparingElementsUsing(EXTRACTION_PATH_OF)
        .containsExactly(
            "/my/app/WEB-INF/classes/HelloWorld.class",
            "/my/app/WEB-INF/classes/empty_dir",
            "/my/app/WEB-INF/classes/package",
            "/my/app/WEB-INF/classes/package/Other.class");
  }

  @Test
  void testCreateContainerBuilder_noErrorIfWebInfDoesNotExist()
      throws IOException, InvalidImageReferenceException {
    File f = new File(temporaryFolder.toFile(), "final-name");
    f.mkdirs();
    setUpWar(f.toPath());

    assertThat(
            mavenProjectProperties.createJibContainerBuilder(
                JavaContainerBuilder.from("ignored"), ContainerizingMode.EXPLODED))
        .isNotNull();
  }

  @Test
  void testCreateContainerBuilder_noErrorIfWebInfLibDoesNotExist()
      throws IOException, InvalidImageReferenceException {
    new File(temporaryFolder.toFile(), "final-name").mkdirs();
    new File(temporaryFolder.toFile(), "WEB-INF").mkdirs();
    new File(temporaryFolder.toFile(), "classes").mkdirs();

    setUpWar(temporaryFolder);

    assertThat(
            mavenProjectProperties.createJibContainerBuilder(
                JavaContainerBuilder.from("ignored"), ContainerizingMode.EXPLODED))
        .isNotNull();
  }

  @Test
  void testCreateContainerBuilder_exceptionMessageHasPackageSuggestionIfProjectIsWar()
      throws IOException {
    when(mockBuild.getDirectory()).thenReturn(Paths.get("/foo/bar").toString());
    when(mockBuild.getFinalName()).thenReturn("helloworld-1");

    String expectedMessage =
        "Obtaining project build output files failed; make sure you have "
            + "packaged your project before trying to build the image. (Did you accidentally run \"mvn clean "
            + "jib:build\" instead of \"mvn clean package jib:build\"?)";

    when(mockMavenProject.getPackaging()).thenReturn("war");
    when(mockTempDirectoryProvider.newDirectory()).thenThrow(IOException.class);

    for (ContainerizingMode containerizingMode : ContainerizingMode.values()) {
      IOException thrownException =
          assertThrows(
              IOException.class,
              () ->
                  mavenProjectProperties.createJibContainerBuilder(
                      mockJavaContainerBuilder, containerizingMode));
      assertThat(thrownException).hasMessageThat().isEqualTo(expectedMessage);
    }
  }

  @Test
  void testCreateContainerBuilder_exceptionMessageHasCompileSuggestionIfProjectIsExplodedAndNotWar()
      throws IOException {
    String expectedMessage =
        "Obtaining project build output files failed; make sure you have "
            + "compiled your project before trying to build the image. (Did you accidentally run \"mvn clean "
            + "jib:build\" instead of \"mvn clean compile jib:build\"?)";

    when(mockMavenProject.getPackaging()).thenReturn("jar");
    when(mockJavaContainerBuilder.addResources(any(), any())).thenThrow(IOException.class);

    IOException thrownException =
        assertThrows(
            IOException.class,
            () ->
                mavenProjectProperties.createJibContainerBuilder(
                    mockJavaContainerBuilder, ContainerizingMode.EXPLODED));
    assertThat(thrownException).hasMessageThat().isEqualTo(expectedMessage);
  }

  @Test
  void testCreateContainerBuilder_noErrorIfWebInfClassesDoesNotExist()
      throws IOException, InvalidImageReferenceException {
    new File(temporaryFolder.toFile(), "final-name").mkdirs();
    new File(temporaryFolder.toFile(), "WEB-INF").mkdirs();
    new File(temporaryFolder.toFile(), "classes").mkdirs();

    setUpWar(temporaryFolder);

    assertThat(
            mavenProjectProperties.createJibContainerBuilder(
                JavaContainerBuilder.from("ignored"), ContainerizingMode.EXPLODED))
        .isNotNull();
  }

  @Test
  void testIsWarProject_warPackagingIsWar() {
    when(mockMavenProject.getPackaging()).thenReturn("war");
    assertThat(mavenProjectProperties.isWarProject()).isTrue();
  }

  @Test
  void testIsWarProject_gwtAppPackagingIsWar() {
    when(mockMavenProject.getPackaging()).thenReturn("gwt-app");
    assertThat(mavenProjectProperties.isWarProject()).isTrue();
  }

  @Test
  void testIsWarProject_jarPackagingIsNotWar() {
    when(mockMavenProject.getPackaging()).thenReturn("jar");
    assertThat(mavenProjectProperties.isWarProject()).isFalse();
  }

  @Test
  void testIsWarProject_gwtLibPackagingIsNotWar() {
    when(mockMavenProject.getPackaging()).thenReturn("gwt-lib");
    assertThat(mavenProjectProperties.isWarProject()).isFalse();
  }

  @Test
  void testClassifyDependencies() {
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

    Map<LayerType, List<Path>> classified =
        mavenProjectProperties.classifyDependencies(artifacts, projectArtifacts);

    assertThat(classified.get(LayerType.DEPENDENCIES))
        .containsExactly(
            newArtifact("com.test", "dependencyA", "1.0").getFile().toPath(),
            newArtifact("com.test", "dependencyD", "4.0").getFile().toPath());

    assertThat(classified.get(LayerType.SNAPSHOT_DEPENDENCIES))
        .containsExactly(
            newArtifact("com.test", "dependencyB", "4.0-SNAPSHOT").getFile().toPath(),
            newArtifact("com.test", "dependencyC", "1.0-SNAPSHOT").getFile().toPath());

    assertThat(classified.get(LayerType.PROJECT_DEPENDENCIES))
        .containsExactly(
            newArtifact("com.test", "projectA", "1.0").getFile().toPath(),
            newArtifact("com.test", "projectB", "1.0-SNAPSHOT").getFile().toPath(),
            newArtifact("com.test", "projectC", "3.0").getFile().toPath());
  }

  @Test
  void testGetProjectDependencies() {
    MavenProject rootPomProject = mock(MavenProject.class);
    MavenProject jibSubModule = mock(MavenProject.class);
    MavenProject sharedLibSubModule = mock(MavenProject.class);
    when(mockMavenSession.getProjects())
        .thenReturn(Arrays.asList(rootPomProject, sharedLibSubModule, jibSubModule));

    Artifact nullFileArtifact = mock(Artifact.class);
    Artifact projectJar = newArtifact("com.test", "my-app", "1.0");
    Artifact sharedLibJar = newArtifact("com.test", "shared-lib", "1.0");

    when(rootPomProject.getArtifact()).thenReturn(nullFileArtifact);
    when(jibSubModule.getArtifact()).thenReturn(projectJar);
    when(sharedLibSubModule.getArtifact()).thenReturn(sharedLibJar);

    when(mockMavenProject.getArtifact()).thenReturn(projectJar);

    assertThat(mavenProjectProperties.getProjectDependencies()).containsExactly(sharedLibJar);
  }

  @Test
  void testGetChildValue_null() {
    assertThat(MavenProjectProperties.getChildValue(null)).isEmpty();
    assertThat(MavenProjectProperties.getChildValue(null, "foo", "bar")).isEmpty();
  }

  @Test
  void testGetChildValue_noPathGiven() {
    Xpp3Dom root = newXpp3Dom("root", "value");

    assertThat(MavenProjectProperties.getChildValue(root)).isEqualTo(Optional.of("value"));
  }

  @Test
  void testGetChildValue_noChild() {
    Xpp3Dom root = newXpp3Dom("root", "value");

    assertThat(MavenProjectProperties.getChildValue(root, "foo")).isEmpty();
    assertThat(MavenProjectProperties.getChildValue(root, "foo", "bar")).isEmpty();
  }

  @Test
  void testGetChildValue_childPathMatched() {
    Xpp3Dom root = newXpp3Dom("root", "value");
    Xpp3Dom foo = addXpp3DomChild(root, "foo", "foo");
    addXpp3DomChild(foo, "bar", "bar");

    assertThat(MavenProjectProperties.getChildValue(root, "foo")).isEqualTo(Optional.of("foo"));
    assertThat(MavenProjectProperties.getChildValue(root, "foo", "bar"))
        .isEqualTo(Optional.of("bar"));
    assertThat(MavenProjectProperties.getChildValue(foo, "bar")).isEqualTo(Optional.of("bar"));
  }

  @Test
  void testGetChildValue_notFullyMatched() {
    Xpp3Dom root = newXpp3Dom("root", "value");
    Xpp3Dom foo = addXpp3DomChild(root, "foo", "foo");

    addXpp3DomChild(foo, "bar", "bar");
    assertThat(MavenProjectProperties.getChildValue(root, "baz")).isEmpty();
    assertThat(MavenProjectProperties.getChildValue(root, "foo", "baz")).isEmpty();
  }

  @Test
  void testGetChildValue_nullValue() {
    Xpp3Dom root = new Xpp3Dom("root");
    addXpp3DomChild(root, "foo", null);

    assertThat(MavenProjectProperties.getChildValue(root)).isEmpty();
    assertThat(MavenProjectProperties.getChildValue(root, "foo")).isEmpty();
  }

  @Test
  void testGetSpringBootRepackageConfiguration_pluginNotApplied() {
    assertThat(mavenProjectProperties.getSpringBootRepackageConfiguration()).isEmpty();
  }

  @Test
  void testGetSpringBootRepackageConfiguration_noConfigurationBlock() {
    when(mockMavenProject.getPlugin("org.springframework.boot:spring-boot-maven-plugin"))
        .thenReturn(mockPlugin);
    when(mockPlugin.getExecutions()).thenReturn(Arrays.asList(mockPluginExecution));
    when(mockPluginExecution.getGoals()).thenReturn(Arrays.asList("repackage"));
    when(mockPluginExecution.getConfiguration()).thenReturn(null);
    assertThat(mavenProjectProperties.getSpringBootRepackageConfiguration())
        .isEqualTo(Optional.of(new Xpp3Dom("configuration")));
  }

  @Test
  void testGetSpringBootRepackageConfiguration_noExecutions() {
    when(mockMavenProject.getPlugin("org.springframework.boot:spring-boot-maven-plugin"))
        .thenReturn(mockPlugin);
    when(mockPlugin.getExecutions()).thenReturn(Collections.emptyList());
    assertThat(mavenProjectProperties.getSpringBootRepackageConfiguration()).isEmpty();
  }

  @Test
  void testGetSpringBootRepackageConfiguration_noRepackageGoal() {
    when(mockMavenProject.getPlugin("org.springframework.boot:spring-boot-maven-plugin"))
        .thenReturn(mockPlugin);
    when(mockPlugin.getExecutions()).thenReturn(Arrays.asList(mockPluginExecution));
    when(mockPluginExecution.getGoals()).thenReturn(Arrays.asList("goal", "foo", "bar"));
    assertThat(mavenProjectProperties.getSpringBootRepackageConfiguration()).isEmpty();
  }

  @Test
  void testGetSpringBootRepackageConfiguration_repackageGoal() {
    when(mockMavenProject.getPlugin("org.springframework.boot:spring-boot-maven-plugin"))
        .thenReturn(mockPlugin);
    when(mockPlugin.getExecutions()).thenReturn(Arrays.asList(mockPluginExecution));
    when(mockPluginExecution.getGoals()).thenReturn(Arrays.asList("goal", "repackage"));
    when(mockPluginExecution.getConfiguration()).thenReturn(pluginConfiguration);
    assertThat(mavenProjectProperties.getSpringBootRepackageConfiguration())
        .isEqualTo(Optional.of(pluginConfiguration));
  }

  @Test
  void testGetSpringBootRepackageConfiguration_skipped() {
    when(mockMavenProject.getPlugin("org.springframework.boot:spring-boot-maven-plugin"))
        .thenReturn(mockPlugin);
    when(mockPlugin.getExecutions()).thenReturn(Arrays.asList(mockPluginExecution));
    when(mockPluginExecution.getGoals()).thenReturn(Arrays.asList("repackage"));
    when(mockPluginExecution.getConfiguration()).thenReturn(pluginConfiguration);
    addXpp3DomChild(pluginConfiguration, "skip", "true");
    assertThat(mavenProjectProperties.getSpringBootRepackageConfiguration()).isEmpty();
  }

  @Test
  void testGetSpringBootRepackageConfiguration_skipNotTrue() {
    when(mockMavenProject.getPlugin("org.springframework.boot:spring-boot-maven-plugin"))
        .thenReturn(mockPlugin);
    when(mockPlugin.getExecutions()).thenReturn(Arrays.asList(mockPluginExecution));
    when(mockPluginExecution.getGoals()).thenReturn(Arrays.asList("repackage"));
    when(mockPluginExecution.getConfiguration()).thenReturn(pluginConfiguration);
    addXpp3DomChild(pluginConfiguration, "skip", null);
    assertThat(mavenProjectProperties.getSpringBootRepackageConfiguration())
        .isEqualTo(Optional.of(pluginConfiguration));
  }

  @Test
  void testGetJarArtifact() throws IOException {
    when(mockBuild.getDirectory()).thenReturn(Paths.get("/foo/bar").toString());
    when(mockBuild.getFinalName()).thenReturn("helloworld-1");

    assertThat(mavenProjectProperties.getJarArtifact())
        .isEqualTo(Paths.get("/foo/bar/helloworld-1.jar"));
  }

  @Test
  void testGetJarArtifact_outputDirectoryFromJarPlugin() throws IOException {
    when(mockMavenProject.getBasedir()).thenReturn(new File("/should/ignore"));
    when(mockBuild.getDirectory()).thenReturn("/should/ignore");
    when(mockBuild.getFinalName()).thenReturn("helloworld-1");

    when(mockMavenProject.getPlugin("org.apache.maven.plugins:maven-jar-plugin"))
        .thenReturn(mockPlugin);
    when(mockPlugin.getExecutions()).thenReturn(Arrays.asList(mockPluginExecution));
    when(mockPluginExecution.getId()).thenReturn("default-jar");
    when(mockPluginExecution.getConfiguration()).thenReturn(pluginConfiguration);
    addXpp3DomChild(pluginConfiguration, "outputDirectory", Paths.get("/jar/out").toString());

    assertThat(mavenProjectProperties.getJarArtifact())
        .isEqualTo(Paths.get("/jar/out/helloworld-1.jar"));
  }

  @Test
  void testGetJarArtifact_relativeOutputDirectoryFromJarPlugin() throws IOException {
    when(mockMavenProject.getBasedir()).thenReturn(new File("/base/dir"));
    when(mockBuild.getDirectory()).thenReturn(temporaryFolder.getRoot().toString());
    when(mockBuild.getFinalName()).thenReturn("helloworld-1");

    when(mockMavenProject.getPlugin("org.apache.maven.plugins:maven-jar-plugin"))
        .thenReturn(mockPlugin);
    when(mockPlugin.getExecutions()).thenReturn(Arrays.asList(mockPluginExecution));
    when(mockPluginExecution.getId()).thenReturn("default-jar");
    when(mockPluginExecution.getConfiguration()).thenReturn(pluginConfiguration);
    addXpp3DomChild(pluginConfiguration, "outputDirectory", Paths.get("relative").toString());

    assertThat(mavenProjectProperties.getJarArtifact())
        .isEqualTo(Paths.get("/base/dir/relative/helloworld-1.jar"));
  }

  @Test
  void testGetJarArtifact_classifier() throws IOException {
    when(mockBuild.getDirectory()).thenReturn(Paths.get("/foo/bar").toString());
    when(mockBuild.getFinalName()).thenReturn("helloworld-1");

    when(mockMavenProject.getPlugin("org.apache.maven.plugins:maven-jar-plugin"))
        .thenReturn(mockPlugin);
    when(mockPlugin.getExecutions()).thenReturn(Arrays.asList(mockPluginExecution));
    when(mockPluginExecution.getId()).thenReturn("default-jar");
    when(mockPluginExecution.getConfiguration()).thenReturn(pluginConfiguration);
    addXpp3DomChild(pluginConfiguration, "classifier", "a-class");

    assertThat(mavenProjectProperties.getJarArtifact())
        .isEqualTo(Paths.get("/foo/bar/helloworld-1-a-class.jar"));
  }

  @Test
  void testGetJarArtifact_executionIdNotMatched() throws IOException {
    when(mockBuild.getDirectory()).thenReturn(Paths.get("/foo/bar").toString());
    when(mockBuild.getFinalName()).thenReturn("helloworld-1");

    when(mockMavenProject.getPlugin("org.apache.maven.plugins:maven-jar-plugin"))
        .thenReturn(mockPlugin);
    when(mockPlugin.getExecutions()).thenReturn(Arrays.asList(mockPluginExecution));
    when(mockPluginExecution.getId()).thenReturn("no-id-match");
    Mockito.lenient().when(mockPluginExecution.getConfiguration()).thenReturn(pluginConfiguration);
    addXpp3DomChild(pluginConfiguration, "outputDirectory", "/should/ignore");
    addXpp3DomChild(pluginConfiguration, "classifier", "a-class");

    assertThat(mavenProjectProperties.getJarArtifact())
        .isEqualTo(Paths.get("/foo/bar/helloworld-1.jar"));
  }

  @Test
  void testGetJarArtifact_originalJarCopiedIfSpringBoot() throws IOException {
    File f = new File(temporaryFolder.toFile(), "helloworld-1.jar.original");
    f.createNewFile();
    when(mockBuild.getDirectory()).thenReturn(temporaryFolder.toString());
    when(mockBuild.getFinalName()).thenReturn("helloworld-1");

    setUpSpringBootFatJar();
    File f2 = new File(temporaryFolder.toFile(), "tmp");
    f2.mkdirs();
    Path tempDirectory = f2.toPath();
    when(mockTempDirectoryProvider.newDirectory()).thenReturn(tempDirectory);

    assertThat(mavenProjectProperties.getJarArtifact())
        .isEqualTo(tempDirectory.resolve("helloworld-1.original.jar"));

    mavenProjectProperties.waitForLoggingThread();
    verify(mockLog).info("Spring Boot repackaging (fat JAR) detected; using the original JAR");
  }

  @Test
  void testGetJarArtifact_originalJarIfSpringBoot_differentDirectories() throws IOException {
    when(mockMavenProject.getBasedir()).thenReturn(new File("/should/ignore"));
    when(mockBuild.getDirectory()).thenReturn("/should/ignore");
    when(mockBuild.getFinalName()).thenReturn("helloworld-1");

    when(mockMavenProject.getPlugin("org.apache.maven.plugins:maven-jar-plugin"))
        .thenReturn(mockPlugin);
    when(mockPlugin.getExecutions()).thenReturn(Arrays.asList(mockPluginExecution));
    when(mockPluginExecution.getId()).thenReturn("default-jar");
    when(mockPluginExecution.getConfiguration()).thenReturn(pluginConfiguration);
    addXpp3DomChild(pluginConfiguration, "outputDirectory", Paths.get("/jar/out").toString());

    setUpSpringBootFatJar();

    assertThat(mavenProjectProperties.getJarArtifact())
        .isEqualTo(Paths.get("/jar/out/helloworld-1.jar"));

    mavenProjectProperties.waitForLoggingThread();
    verify(mockLog).info("Spring Boot repackaging (fat JAR) detected; using the original JAR");
  }

  @Test
  void testGetJarArtifact_originalJarIfSpringBoot_differentFinalNames() throws IOException {
    File f = new File(temporaryFolder.toFile(), "target");
    f.mkdirs();
    Path buildDirectory = f.toPath();
    Files.createFile(buildDirectory.resolve("helloworld-1.jar"));
    when(mockMavenProject.getBasedir()).thenReturn(temporaryFolder.toFile());
    when(mockBuild.getDirectory()).thenReturn(buildDirectory.toString());
    when(mockBuild.getFinalName()).thenReturn("helloworld-1");

    when(mockMavenProject.getPlugin("org.apache.maven.plugins:maven-jar-plugin"))
        .thenReturn(mockPlugin);
    when(mockPlugin.getExecutions()).thenReturn(Arrays.asList(mockPluginExecution));
    when(mockPluginExecution.getId()).thenReturn("default-jar");
    when(mockPluginExecution.getConfiguration()).thenReturn(pluginConfiguration);
    addXpp3DomChild(pluginConfiguration, "outputDirectory", "target");

    Xpp3Dom bootPluginConfiguration = setUpSpringBootFatJar();
    addXpp3DomChild(bootPluginConfiguration, "finalName", "boot-helloworld-1");

    assertThat(mavenProjectProperties.getJarArtifact())
        .isEqualTo(buildDirectory.resolve("helloworld-1.jar"));

    mavenProjectProperties.waitForLoggingThread();
    verify(mockLog).info("Spring Boot repackaging (fat JAR) detected; using the original JAR");
  }

  @Test
  void testGetJarArtifact_originalJarIfSpringBoot_differentClassifier() throws IOException {
    File f = new File(temporaryFolder.toFile(), "target");
    f.mkdirs();

    Path buildDirectory = f.toPath();
    Files.createFile(buildDirectory.resolve("helloworld-1.jar"));
    when(mockMavenProject.getBasedir()).thenReturn(temporaryFolder.toFile());
    when(mockBuild.getDirectory()).thenReturn(buildDirectory.toString());
    when(mockBuild.getFinalName()).thenReturn("helloworld-1");

    when(mockMavenProject.getPlugin("org.apache.maven.plugins:maven-jar-plugin"))
        .thenReturn(mockPlugin);
    when(mockPlugin.getExecutions()).thenReturn(Arrays.asList(mockPluginExecution));
    when(mockPluginExecution.getId()).thenReturn("default-jar");
    when(mockPluginExecution.getConfiguration()).thenReturn(pluginConfiguration);
    addXpp3DomChild(pluginConfiguration, "outputDirectory", "target");

    Xpp3Dom bootPluginConfiguration = setUpSpringBootFatJar();
    addXpp3DomChild(bootPluginConfiguration, "classifier", "boot-class");

    assertThat(mavenProjectProperties.getJarArtifact())
        .isEqualTo(buildDirectory.resolve("helloworld-1.jar"));

    mavenProjectProperties.waitForLoggingThread();
    verify(mockLog).info("Spring Boot repackaging (fat JAR) detected; using the original JAR");
  }

  @Test
  void testGetJarArtifact_originalJarCopiedIfSpringBoot_sameDirectory() throws IOException {
    File f = new File(temporaryFolder.toFile(), "target");
    f.mkdirs();

    Path buildDirectory = f.toPath();
    Files.createFile(buildDirectory.resolve("helloworld-1.jar.original"));
    when(mockMavenProject.getBasedir()).thenReturn(temporaryFolder.toFile());
    when(mockBuild.getDirectory()).thenReturn(buildDirectory.toString());
    when(mockBuild.getFinalName()).thenReturn("helloworld-1");

    when(mockMavenProject.getPlugin("org.apache.maven.plugins:maven-jar-plugin"))
        .thenReturn(mockPlugin);
    when(mockPlugin.getExecutions()).thenReturn(Arrays.asList(mockPluginExecution));
    when(mockPluginExecution.getId()).thenReturn("default-jar");
    when(mockPluginExecution.getConfiguration()).thenReturn(pluginConfiguration);
    addXpp3DomChild(pluginConfiguration, "outputDirectory", "target");

    setUpSpringBootFatJar();
    f = new File(temporaryFolder.toFile(), "tmp");
    f.mkdirs();
    Path tempDirectory = f.toPath();
    when(mockTempDirectoryProvider.newDirectory()).thenReturn(tempDirectory);

    assertThat(mavenProjectProperties.getJarArtifact())
        .isEqualTo(tempDirectory.resolve("helloworld-1.original.jar"));

    mavenProjectProperties.waitForLoggingThread();
    verify(mockLog).info("Spring Boot repackaging (fat JAR) detected; using the original JAR");
  }

  @Test
  void testGetWarArtifact() {
    when(mockBuild.getDirectory()).thenReturn(Paths.get("/foo/bar").toString());
    when(mockBuild.getFinalName()).thenReturn("helloworld-1");

    assertThat(mavenProjectProperties.getWarArtifact())
        .isEqualTo(Paths.get("/foo/bar/helloworld-1.war"));
  }

  @Test
  void testGetWarArtifact_warNameProperty() {
    when(mockBuild.getDirectory()).thenReturn(Paths.get("/foo/bar").toString());
    when(mockBuild.getFinalName()).thenReturn("helloworld-1");

    when(mockMavenProject.getPlugin("org.apache.maven.plugins:maven-war-plugin"))
        .thenReturn(mockPlugin);
    when(mockPlugin.getExecutions()).thenReturn(Arrays.asList(mockPluginExecution));
    when(mockPluginExecution.getId()).thenReturn("default-war");
    when(mockPluginExecution.getConfiguration()).thenReturn(pluginConfiguration);
    addXpp3DomChild(pluginConfiguration, "warName", "baz");

    assertThat(mavenProjectProperties.getWarArtifact()).isEqualTo(Paths.get("/foo/bar/baz.war"));
  }

  @Test
  void testGetWarArtifact_noWarNameProperty() {
    when(mockBuild.getDirectory()).thenReturn(Paths.get("/foo/bar").toString());
    when(mockBuild.getFinalName()).thenReturn("helloworld-1");

    when(mockMavenProject.getPlugin("org.apache.maven.plugins:maven-war-plugin"))
        .thenReturn(mockPlugin);
    when(mockPlugin.getExecutions()).thenReturn(Arrays.asList(mockPluginExecution));
    when(mockPluginExecution.getId()).thenReturn("default-war");
    Mockito.lenient().when(mockPluginExecution.getConfiguration()).thenReturn(pluginConfiguration);

    assertThat(mavenProjectProperties.getWarArtifact())
        .isEqualTo(Paths.get("/foo/bar/helloworld-1.war"));
  }

  @Test
  void testGetWarArtifact_executionIdNotMatched() {
    when(mockBuild.getDirectory()).thenReturn(Paths.get("/foo/bar").toString());
    when(mockBuild.getFinalName()).thenReturn("helloworld-1");

    when(mockMavenProject.getPlugin("org.apache.maven.plugins:maven-war-plugin"))
        .thenReturn(mockPlugin);
    when(mockPlugin.getExecutions()).thenReturn(Arrays.asList(mockPluginExecution));
    when(mockPluginExecution.getId()).thenReturn("no-id-match");
    Mockito.lenient().when(mockPluginExecution.getConfiguration()).thenReturn(pluginConfiguration);
    addXpp3DomChild(pluginConfiguration, "warName", "baz");

    assertThat(mavenProjectProperties.getWarArtifact())
        .isEqualTo(Paths.get("/foo/bar/helloworld-1.war"));
  }

  @Test
  void testGetDependencies() throws URISyntaxException {
    assertThat(mavenProjectProperties.getDependencies())
        .containsExactly(
            getResource("maven/application/dependencies/library.jarC.jar"),
            getResource("maven/application/dependencies/libraryB.jar"),
            getResource("maven/application/dependencies/libraryA.jar"),
            getResource("maven/application/dependencies/more/dependency-1.0.0.jar"),
            getResource("maven/application/dependencies/another/one/dependency-1.0.0.jar"),
            testRepository.artifactPathOnDisk("com.test", "dependency", "1.0.0"),
            testRepository.artifactPathOnDisk("com.test", "dependencyX", "1.0.0-SNAPSHOT"));
  }

  private BuildContext setUpBuildContext()
      throws InvalidImageReferenceException, IOException, CacheDirectoryCreationException {
    return setUpBuildContext(ContainerizingMode.EXPLODED);
  }

  private BuildContext setUpBuildContext(ContainerizingMode containerizingMode)
      throws InvalidImageReferenceException, IOException, CacheDirectoryCreationException {
    JavaContainerBuilder javaContainerBuilder =
        JavaContainerBuilder.from(RegistryImage.named("base"))
            .setAppRoot(AbsoluteUnixPath.get("/my/app"))
            .setModificationTimeProvider((ignored1, ignored2) -> EPOCH_PLUS_32);
    JibContainerBuilder jibContainerBuilder =
        mavenProjectProperties.createJibContainerBuilder(javaContainerBuilder, containerizingMode);
    return JibContainerBuilderTestHelper.toBuildContext(
        jibContainerBuilder, Containerizer.to(RegistryImage.named("to")));
  }

  private Path setUpWar(Path explodedWar) throws IOException {
    Path fakeMavenBuildDirectory = temporaryFolder;
    when(mockBuild.getDirectory()).thenReturn(fakeMavenBuildDirectory.toString());
    when(mockBuild.getFinalName()).thenReturn("final-name");
    when(mockMavenProject.getPackaging()).thenReturn("war");

    zipUpDirectory(explodedWar, fakeMavenBuildDirectory.resolve("final-name.war"));

    // Make "MavenProjectProperties" use this folder to explode the WAR into.
    File f = new File(temporaryFolder.toFile(), "exploded");
    f.mkdirs();
    Path unzipTarget = f.toPath();
    when(mockTempDirectoryProvider.newDirectory()).thenReturn(unzipTarget);
    return unzipTarget;
  }

  private Xpp3Dom setUpSpringBootFatJar() {
    Xpp3Dom pluginConfiguration = new Xpp3Dom("configuration");
    PluginExecution execution = mock(PluginExecution.class);
    Plugin plugin = mock(Plugin.class);
    when(mockMavenProject.getPlugin("org.springframework.boot:spring-boot-maven-plugin"))
        .thenReturn(plugin);
    when(plugin.getExecutions()).thenReturn(Arrays.asList(execution));
    when(execution.getGoals()).thenReturn(Arrays.asList("repackage"));
    when(execution.getConfiguration()).thenReturn(pluginConfiguration);
    return pluginConfiguration;
  }
}
