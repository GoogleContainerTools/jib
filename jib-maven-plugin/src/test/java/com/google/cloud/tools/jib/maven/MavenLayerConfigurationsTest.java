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

import com.google.cloud.tools.jib.filesystem.AbsoluteUnixPath;
import com.google.cloud.tools.jib.frontend.JavaLayerConfigurations;
import com.google.cloud.tools.jib.image.LayerEntry;
import com.google.cloud.tools.jib.plugins.common.RawConfiguration;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Build;
import org.apache.maven.project.MavenProject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests for {@link MavenLayerConfigurations}. */
@RunWith(MockitoJUnitRunner.class)
public class MavenLayerConfigurationsTest {

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

  private static void assertNonDefaultAppRoot(JavaLayerConfigurations configuration) {
    assertExtractionPathsUnordered(
        Arrays.asList(
            "/my/app/libs/dependency-1.0.0-770.jar",
            "/my/app/libs/dependency-1.0.0-200.jar",
            "/my/app/libs/dependency-1.0.0-480.jar",
            "/my/app/libs/libraryA.jar",
            "/my/app/libs/libraryB.jar",
            "/my/app/libs/library.jarC.jar"),
        configuration.getDependencyLayerEntries());
    assertExtractionPathsUnordered(
        Collections.singletonList("/my/app/libs/dependencyX-1.0.0-SNAPSHOT.jar"),
        configuration.getSnapshotDependencyLayerEntries());
    assertExtractionPathsUnordered(
        Arrays.asList(
            "/my/app/resources/directory",
            "/my/app/resources/directory/somefile",
            "/my/app/resources/package",
            "/my/app/resources/resourceA",
            "/my/app/resources/resourceB",
            "/my/app/resources/world"),
        configuration.getResourceLayerEntries());
    assertExtractionPathsUnordered(
        Arrays.asList(
            "/my/app/classes/HelloWorld.class",
            "/my/app/classes/directory",
            "/my/app/classes/package",
            "/my/app/classes/package/some.class",
            "/my/app/classes/some.class"),
        configuration.getClassLayerEntries());
    assertExtractionPathsUnordered(
        Arrays.asList("/a", "/a/b", "/a/b/bar", "/c", "/c/cat", "/foo"),
        configuration.getExtraFilesLayerEntries());
  }

  @Rule public final TestRepository testRepository = new TestRepository();
  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Mock private MavenProject mockMavenProject;
  @Mock private RawConfiguration rawConfiguration;
  @Mock private Build mockBuild;

  private Path extraFilesDirectory;

  @Before
  public void setUp() throws URISyntaxException, IOException {
    Path outputPath = Paths.get(Resources.getResource("application/output").toURI());
    Path dependenciesPath = Paths.get(Resources.getResource("application/dependencies").toURI());

    Mockito.when(mockMavenProject.getBuild()).thenReturn(mockBuild);
    Mockito.when(mockBuild.getOutputDirectory()).thenReturn(outputPath.toString());

    Set<Artifact> artifacts =
        ImmutableSet.of(
            makeArtifact(dependenciesPath.resolve("library.jarC.jar")),
            makeArtifact(dependenciesPath.resolve("libraryB.jar")),
            makeArtifact(dependenciesPath.resolve("libraryA.jar")),
            makeArtifact(dependenciesPath.resolve("more").resolve("dependency-1.0.0.jar")),
            makeArtifact(
                dependenciesPath.resolve("another").resolve("one").resolve("dependency-1.0.0.jar")),
            // Maven reads and populates "Artifacts" with its own processing, so read some from a
            // repository
            testRepository.findArtifact("com.test", "dependency", "1.0.0"),
            testRepository.findArtifact("com.test", "dependencyX", "1.0.0-SNAPSHOT"));
    Mockito.when(mockMavenProject.getArtifacts()).thenReturn(artifacts);

    Path emptyDirectory =
        Paths.get(Resources.getResource("webapp").toURI())
            .resolve("final-name/WEB-INF/classes/empty_dir");
    Files.createDirectories(emptyDirectory);

    extraFilesDirectory = Paths.get(Resources.getResource("layer").toURI());
  }

  @Test
  public void test_correctFiles() throws URISyntaxException, IOException {
    Path dependenciesPath = Paths.get(Resources.getResource("application/dependencies").toURI());
    ImmutableList<Path> expectedDependenciesFiles =
        ImmutableList.of(
            testRepository.artifactPathOnDisk("com.test", "dependency", "1.0.0"),
            dependenciesPath.resolve("more").resolve("dependency-1.0.0.jar"),
            dependenciesPath.resolve("another").resolve("one").resolve("dependency-1.0.0.jar"),
            dependenciesPath.resolve("libraryA.jar"),
            dependenciesPath.resolve("libraryB.jar"),
            dependenciesPath.resolve("library.jarC.jar"));
    ImmutableList<Path> expectedSnapshotDependenciesFiles =
        ImmutableList.of(
            testRepository.artifactPathOnDisk("com.test", "dependencyX", "1.0.0-SNAPSHOT"));
    Path applicationDirectory = Paths.get(Resources.getResource("application").toURI());
    ImmutableList<Path> expectedResourcesFiles =
        ImmutableList.of(
            applicationDirectory.resolve("output/directory"),
            applicationDirectory.resolve("output/directory/somefile"),
            applicationDirectory.resolve("output/package"),
            applicationDirectory.resolve("output/resourceA"),
            applicationDirectory.resolve("output/resourceB"),
            applicationDirectory.resolve("output/world"));
    ImmutableList<Path> expectedClassesFiles =
        ImmutableList.of(
            applicationDirectory.resolve("output/HelloWorld.class"),
            applicationDirectory.resolve("output/directory"),
            applicationDirectory.resolve("output/package"),
            applicationDirectory.resolve("output/package/some.class"),
            applicationDirectory.resolve("output/some.class"));

    JavaLayerConfigurations javaLayerConfigurations =
        MavenLayerConfigurations.getForProject(
            mockMavenProject,
            false,
            Paths.get("nonexistent/path"),
            Collections.emptyMap(),
            AbsoluteUnixPath.get("/app"));
    assertSourcePathsUnordered(
        expectedDependenciesFiles, javaLayerConfigurations.getDependencyLayerEntries());
    assertSourcePathsUnordered(
        expectedSnapshotDependenciesFiles,
        javaLayerConfigurations.getSnapshotDependencyLayerEntries());
    assertSourcePathsUnordered(
        expectedResourcesFiles, javaLayerConfigurations.getResourceLayerEntries());
    assertSourcePathsUnordered(
        expectedClassesFiles, javaLayerConfigurations.getClassLayerEntries());
  }

  @Test
  public void test_extraFiles() throws IOException {
    AbsoluteUnixPath appRoot = AbsoluteUnixPath.get("/app");
    JavaLayerConfigurations javaLayerConfigurations =
        MavenLayerConfigurations.getForProject(
            mockMavenProject, false, extraFilesDirectory, Collections.emptyMap(), appRoot);

    ImmutableList<Path> expectedExtraFiles =
        ImmutableList.of(
            extraFilesDirectory.resolve("a"),
            extraFilesDirectory.resolve("a/b"),
            extraFilesDirectory.resolve("a/b/bar"),
            extraFilesDirectory.resolve("c"),
            extraFilesDirectory.resolve("c/cat"),
            extraFilesDirectory.resolve("foo"));

    assertSourcePathsUnordered(
        expectedExtraFiles, javaLayerConfigurations.getExtraFilesLayerEntries());
  }

  @Test
  public void testGetForProject_nonDefaultAppRoot() throws IOException {
    AbsoluteUnixPath appRoot = AbsoluteUnixPath.get("/my/app");
    JavaLayerConfigurations configuration =
        MavenLayerConfigurations.getForProject(
            mockMavenProject, false, extraFilesDirectory, Collections.emptyMap(), appRoot);

    assertNonDefaultAppRoot(configuration);
  }

  private Artifact makeArtifact(Path path) {
    Artifact artifact = Mockito.mock(Artifact.class);
    Mockito.when(artifact.getFile()).thenReturn(path.toFile());
    return artifact;
  }

  @Test
  public void testGetForWarProject_nonDefaultAppRoot() throws URISyntaxException, IOException {
    Path outputPath = Paths.get(Resources.getResource("webapp").toURI());
    Mockito.when(mockBuild.getDirectory()).thenReturn(outputPath.toString());
    Mockito.when(mockBuild.getFinalName()).thenReturn("final-name");

    AbsoluteUnixPath appRoot = AbsoluteUnixPath.get("/my/app");
    JavaLayerConfigurations configuration =
        MavenLayerConfigurations.getForProject(
            mockMavenProject, true, extraFilesDirectory, Collections.emptyMap(), appRoot);

    ImmutableList<Path> expectedDependenciesFiles =
        ImmutableList.of(outputPath.resolve("final-name/WEB-INF/lib/dependency-1.0.0.jar"));
    ImmutableList<Path> expectedSnapshotDependenciesFiles =
        ImmutableList.of(
            outputPath.resolve("final-name/WEB-INF/lib/dependencyX-1.0.0-SNAPSHOT.jar"));
    ImmutableList<Path> expectedResourcesFiles =
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
            outputPath.resolve("final-name/WEB-INF/web.xml"));
    ImmutableList<Path> expectedClassesFiles =
        ImmutableList.of(
            outputPath.resolve("final-name/WEB-INF/classes/HelloWorld.class"),
            outputPath.resolve("final-name/WEB-INF/classes/empty_dir"),
            outputPath.resolve("final-name/WEB-INF/classes/package"),
            outputPath.resolve("final-name/WEB-INF/classes/package/Other.class"));
    ImmutableList<Path> expectedExtraFiles =
        ImmutableList.of(
            extraFilesDirectory.resolve("a"),
            extraFilesDirectory.resolve("a/b"),
            extraFilesDirectory.resolve("a/b/bar"),
            extraFilesDirectory.resolve("c"),
            extraFilesDirectory.resolve("c/cat"),
            extraFilesDirectory.resolve("foo"));

    assertSourcePathsUnordered(
        expectedDependenciesFiles, configuration.getDependencyLayerEntries());
    assertSourcePathsUnordered(
        expectedSnapshotDependenciesFiles, configuration.getSnapshotDependencyLayerEntries());
    assertSourcePathsUnordered(expectedResourcesFiles, configuration.getResourceLayerEntries());
    assertSourcePathsUnordered(expectedClassesFiles, configuration.getClassLayerEntries());
    assertSourcePathsUnordered(expectedExtraFiles, configuration.getExtraFilesLayerEntries());

    assertExtractionPathsUnordered(
        Collections.singletonList("/my/app/WEB-INF/lib/dependency-1.0.0.jar"),
        configuration.getDependencyLayerEntries());
    assertExtractionPathsUnordered(
        Collections.singletonList("/my/app/WEB-INF/lib/dependencyX-1.0.0-SNAPSHOT.jar"),
        configuration.getSnapshotDependencyLayerEntries());
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
        configuration.getResourceLayerEntries());
    assertExtractionPathsUnordered(
        Arrays.asList(
            "/my/app/WEB-INF/classes/HelloWorld.class",
            "/my/app/WEB-INF/classes/empty_dir",
            "/my/app/WEB-INF/classes/package",
            "/my/app/WEB-INF/classes/package/Other.class"),
        configuration.getClassLayerEntries());
    assertExtractionPathsUnordered(
        Arrays.asList("/a", "/a/b", "/a/b/bar", "/c", "/c/cat", "/foo"),
        configuration.getExtraFilesLayerEntries());
  }

  @Test
  public void testGetForJarProject_nonDefaultAppRoot() throws IOException {
    AbsoluteUnixPath appRoot = AbsoluteUnixPath.get("/my/app");
    JavaLayerConfigurations configuration =
        MavenLayerConfigurations.getForProject(
            mockMavenProject, false, extraFilesDirectory, Collections.emptyMap(), appRoot);

    assertNonDefaultAppRoot(configuration);
  }

  @Test
  public void testGetForWarProject_noErrorIfWebInfDoesNotExist() throws IOException {
    temporaryFolder.newFolder("final-name");
    Mockito.when(mockBuild.getDirectory())
        .thenReturn(temporaryFolder.getRoot().toPath().toString());
    Mockito.when(mockBuild.getFinalName()).thenReturn("final-name");
    AbsoluteUnixPath appRoot = AbsoluteUnixPath.get("/my/app");

    // should pass
    MavenLayerConfigurations.getForProject(
        mockMavenProject, true, extraFilesDirectory, Collections.emptyMap(), appRoot);
  }

  @Test
  public void testGetForWarProject_noErrorIfWebInfLibDoesNotExist() throws IOException {
    temporaryFolder.newFolder("final-name", "WEB-INF", "classes");
    Mockito.when(mockBuild.getDirectory())
        .thenReturn(temporaryFolder.getRoot().toPath().toString());
    Mockito.when(mockBuild.getFinalName()).thenReturn("final-name");
    AbsoluteUnixPath appRoot = AbsoluteUnixPath.get("/my/app");

    // should pass
    MavenLayerConfigurations.getForProject(
        mockMavenProject, true, extraFilesDirectory, Collections.emptyMap(), appRoot);
  }

  @Test
  public void testGetForWarProject_noErrorIfWebInfClassesDoesNotExist() throws IOException {
    temporaryFolder.newFolder("final-name", "WEB-INF", "lib");
    Mockito.when(mockBuild.getDirectory())
        .thenReturn(temporaryFolder.getRoot().toPath().toString());
    Mockito.when(mockBuild.getFinalName()).thenReturn("final-name");
    AbsoluteUnixPath appRoot = AbsoluteUnixPath.get("/my/app");

    // should pass
    MavenLayerConfigurations.getForProject(
        mockMavenProject, true, extraFilesDirectory, Collections.emptyMap(), appRoot);
  }

  @Test
  public void testIsWarProject_warPackagingAndNoOverride() {
    Mockito.when(rawConfiguration.getPackagingOverride()).thenReturn(Optional.empty());
    Mockito.when(mockMavenProject.getPackaging()).thenReturn("war");

    Assert.assertTrue(MojoCommon.isWarContainerization(mockMavenProject, rawConfiguration));
  }

  @Test
  public void testIsWarProject_gwtAppPackagingAndNoOverride() {
    Mockito.when(rawConfiguration.getPackagingOverride()).thenReturn(Optional.empty());
    Mockito.when(mockMavenProject.getPackaging()).thenReturn("gwt-app");

    Assert.assertTrue(MojoCommon.isWarContainerization(mockMavenProject, rawConfiguration));
  }

  @Test
  public void testIsWarProject_jarPackagingAndNoOverride() {
    Mockito.when(rawConfiguration.getPackagingOverride()).thenReturn(Optional.empty());
    Mockito.when(mockMavenProject.getPackaging()).thenReturn("jar");

    Assert.assertFalse(MojoCommon.isWarContainerization(mockMavenProject, rawConfiguration));
  }

  @Test
  public void testIsWarProject_gwtLibPackagingAndNoOverride() {
    Mockito.when(rawConfiguration.getPackagingOverride()).thenReturn(Optional.empty());
    Mockito.when(mockMavenProject.getPackaging()).thenReturn("gwt-lib");

    Assert.assertFalse(MojoCommon.isWarContainerization(mockMavenProject, rawConfiguration));
  }

  @Test
  public void testIsWarProject_warOverride() {
    Mockito.when(rawConfiguration.getPackagingOverride()).thenReturn(Optional.of("war"));
    Mockito.lenient().when(mockMavenProject.getPackaging()).thenReturn("jar");

    Assert.assertTrue(MojoCommon.isWarContainerization(mockMavenProject, rawConfiguration));
  }

  @Test
  public void testIsWarProject_javaOverride() {
    Mockito.when(rawConfiguration.getPackagingOverride()).thenReturn(Optional.of("java"));
    Mockito.lenient().when(mockMavenProject.getPackaging()).thenReturn("war");

    Assert.assertFalse(MojoCommon.isWarContainerization(mockMavenProject, rawConfiguration));
  }
}
