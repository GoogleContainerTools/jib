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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Build;
import org.apache.maven.project.MavenProject;
import org.hamcrest.CoreMatchers;
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
        expectedPaths, entries, LayerEntry::getAbsoluteExtractionPathString);
  }

  @Rule public final TestRepository testRepository = new TestRepository();
  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Mock private MavenProject mockMavenProject;
  @Mock private Build mockBuild;

  @Mock private MavenLayerConfigurations.FileToLayerAdder fileToLayerAdder;

  @Before
  public void setUp() throws URISyntaxException {
    Path outputPath = Paths.get(Resources.getResource("application/output").toURI());

    Mockito.when(mockMavenProject.getBuild()).thenReturn(mockBuild);
    Mockito.when(mockBuild.getOutputDirectory()).thenReturn(outputPath.toString());

    Set<Artifact> artifacts =
        ImmutableSet.of(
            makeArtifact(Paths.get("application", "dependencies", "libraryB.jar")),
            makeArtifact(Paths.get("application", "dependencies", "libraryA.jar")),
            // maven reads and populates "Artifacts" with it's own processing, so read some from
            // a repository
            testRepository.findArtifact("com.test", "dependency", "1.0.0"),
            testRepository.findArtifact("com.test", "dependencyX", "1.0.0-SNAPSHOT"));
    Mockito.when(mockMavenProject.getArtifacts()).thenReturn(artifacts);
  }

  @Test
  public void test_correctFiles() throws URISyntaxException, IOException {
    ImmutableList<Path> expectedDependenciesFiles =
        ImmutableList.of(
            testRepository.artifactPathOnDisk("com.test", "dependency", "1.0.0"),
            Paths.get("application", "dependencies", "libraryA.jar"),
            Paths.get("application", "dependencies", "libraryB.jar"));
    ImmutableList<Path> expectedSnapshotDependenciesFiles =
        ImmutableList.of(
            testRepository.artifactPathOnDisk("com.test", "dependencyX", "1.0.0-SNAPSHOT"));
    Path applicationDirectory = Paths.get(Resources.getResource("application").toURI());
    ImmutableList<Path> expectedResourcesFiles =
        ImmutableList.of(
            applicationDirectory.resolve("output/directory/somefile"),
            applicationDirectory.resolve("output/resourceA"),
            applicationDirectory.resolve("output/resourceB"),
            applicationDirectory.resolve("output/world"));
    ImmutableList<Path> expectedClassesFiles =
        ImmutableList.of(
            applicationDirectory.resolve("output/HelloWorld.class"),
            applicationDirectory.resolve("output/package/some.class"),
            applicationDirectory.resolve("output/some.class"));

    JavaLayerConfigurations javaLayerConfigurations =
        MavenLayerConfigurations.getForProject(
            mockMavenProject, Paths.get("nonexistent/path"), AbsoluteUnixPath.get("/app"));
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
  public void test_extraFiles() throws URISyntaxException, IOException {
    Path extraFilesDirectory = Paths.get(Resources.getResource("layer").toURI());

    AbsoluteUnixPath appRoot = AbsoluteUnixPath.get("/app");
    JavaLayerConfigurations javaLayerConfigurations =
        MavenLayerConfigurations.getForProject(mockMavenProject, extraFilesDirectory, appRoot);

    ImmutableList<Path> expectedExtraFiles =
        ImmutableList.of(
            extraFilesDirectory.resolve("a/b/bar"),
            extraFilesDirectory.resolve("c/cat"),
            extraFilesDirectory.resolve("foo"));

    assertSourcePathsUnordered(
        expectedExtraFiles, javaLayerConfigurations.getExtraFilesLayerEntries());
  }

  @Test
  public void testGetForProject_nonDefaultAppRoot() throws URISyntaxException, IOException {
    Path extraFilesDirectory = Paths.get(Resources.getResource("layer").toURI());

    AbsoluteUnixPath appRoot = AbsoluteUnixPath.get("/my/app");
    JavaLayerConfigurations configuration =
        MavenLayerConfigurations.getForProject(mockMavenProject, extraFilesDirectory, appRoot);

    assertExtractionPathsUnordered(
        Arrays.asList(
            "/my/app/libs/dependency-1.0.0.jar",
            "/my/app/libs/libraryA.jar",
            "/my/app/libs/libraryB.jar"),
        configuration.getDependencyLayerEntries());
    assertExtractionPathsUnordered(
        Arrays.asList("/my/app/libs/dependencyX-1.0.0-SNAPSHOT.jar"),
        configuration.getSnapshotDependencyLayerEntries());
    assertExtractionPathsUnordered(
        Arrays.asList(
            "/my/app/resources/directory/somefile",
            "/my/app/resources/resourceA",
            "/my/app/resources/resourceB",
            "/my/app/resources/world"),
        configuration.getResourceLayerEntries());
    assertExtractionPathsUnordered(
        Arrays.asList(
            "/my/app/classes/HelloWorld.class",
            "/my/app/classes/package/some.class",
            "/my/app/classes/some.class"),
        configuration.getClassLayerEntries());
    assertExtractionPathsUnordered(
        Arrays.asList("/a/b/bar", "/c/cat", "/foo"), configuration.getExtraFilesLayerEntries());
  }

  @Test
  public void testIsEmptyDirectory() throws IOException {
    Assert.assertTrue(
        MavenLayerConfigurations.isEmptyDirectory(temporaryFolder.getRoot().toPath()));
  }

  @Test
  public void testIsEmptyDirectory_file() throws IOException {
    Assert.assertFalse(
        MavenLayerConfigurations.isEmptyDirectory(temporaryFolder.newFile().toPath()));
  }

  @Test
  public void testIsEmptyDirectory_nonExistent() throws IOException {
    Assert.assertFalse(MavenLayerConfigurations.isEmptyDirectory(Paths.get("non/existent")));
  }

  @Test
  public void testAddFilesToLayer_file() throws IOException {
    temporaryFolder.newFile("file");

    Path sourceRoot = temporaryFolder.getRoot().toPath();
    AbsoluteUnixPath basePath = AbsoluteUnixPath.get("/path/in/container");

    MavenLayerConfigurations.addFilesToLayer(sourceRoot, path -> true, basePath, fileToLayerAdder);
    Mockito.verify(fileToLayerAdder).add(sourceRoot.resolve("file"), basePath.resolve("file"));
    Mockito.verifyNoMoreInteractions(fileToLayerAdder);
  }

  @Test
  public void testAddFilesToLayer_emptyDirectory() throws IOException {
    temporaryFolder.newFolder("leaf");

    Path sourceRoot = temporaryFolder.getRoot().toPath();
    AbsoluteUnixPath basePath = AbsoluteUnixPath.get("/");

    MavenLayerConfigurations.addFilesToLayer(sourceRoot, path -> true, basePath, fileToLayerAdder);
    Mockito.verify(fileToLayerAdder).add(sourceRoot.resolve("leaf"), basePath.resolve("leaf"));
    Mockito.verifyNoMoreInteractions(fileToLayerAdder);
  }

  @Test
  public void testAddFilesToLayer_nonEmptyDirectoryIgnored() throws IOException {
    temporaryFolder.newFolder("non-empty", "leaf");

    Path sourceRoot = temporaryFolder.getRoot().toPath();
    AbsoluteUnixPath basePath = AbsoluteUnixPath.get("/path/in/container");

    MavenLayerConfigurations.addFilesToLayer(sourceRoot, path -> true, basePath, fileToLayerAdder);
    Mockito.verify(fileToLayerAdder)
        .add(sourceRoot.resolve("non-empty/leaf"), basePath.resolve("non-empty/leaf"));
    Mockito.verifyNoMoreInteractions(fileToLayerAdder);
  }

  @Test
  public void testAddFilesToLayer_filter() throws IOException {
    temporaryFolder.newFile("non-target");
    temporaryFolder.newFolder("sub");
    temporaryFolder.newFile("sub/target");

    Path sourceRoot = temporaryFolder.getRoot().toPath();
    AbsoluteUnixPath basePath = AbsoluteUnixPath.get("/");

    Predicate<Path> nameIsTarget = path -> "target".equals(path.getFileName().toString());
    MavenLayerConfigurations.addFilesToLayer(sourceRoot, nameIsTarget, basePath, fileToLayerAdder);
    Mockito.verify(fileToLayerAdder)
        .add(sourceRoot.resolve("sub/target"), basePath.resolve("sub/target"));
    Mockito.verifyNoMoreInteractions(fileToLayerAdder);
  }

  @Test
  public void testAddFilesToLayer_emptyDirectoryForced() throws IOException {
    temporaryFolder.newFolder("sub", "leaf");

    Path sourceRoot = temporaryFolder.getRoot().toPath();
    AbsoluteUnixPath basePath = AbsoluteUnixPath.get("/path/in/container");

    MavenLayerConfigurations.addFilesToLayer(sourceRoot, path -> false, basePath, fileToLayerAdder);
    Mockito.verify(fileToLayerAdder)
        .add(sourceRoot.resolve("sub/leaf"), basePath.resolve("sub/leaf"));
    Mockito.verifyNoMoreInteractions(fileToLayerAdder);
  }

  @Test
  public void testAddFilesToLayer_fileAsSource() throws IOException {
    Path sourceFile = temporaryFolder.newFile("foo").toPath();

    AbsoluteUnixPath basePath = AbsoluteUnixPath.get("/");
    try {
      MavenLayerConfigurations.addFilesToLayer(
          sourceFile, path -> true, basePath, fileToLayerAdder);
    } catch (NotDirectoryException ex) {
      Assert.assertThat(ex.getMessage(), CoreMatchers.containsString("foo is not a directory"));
    }
  }

  @Test
  public void testAddFilesToLayer_complex() throws IOException {
    temporaryFolder.newFile("A.class");
    temporaryFolder.newFile("B.java");
    temporaryFolder.newFolder("example", "dir");
    temporaryFolder.newFile("example/dir/C.class");
    temporaryFolder.newFile("example/C.class");
    temporaryFolder.newFolder("test", "resources", "leaf");
    temporaryFolder.newFile("test/resources/D.java");
    temporaryFolder.newFile("test/D.class");

    Path sourceRoot = temporaryFolder.getRoot().toPath();
    AbsoluteUnixPath basePath = AbsoluteUnixPath.get("/base");

    Predicate<Path> isClassFile = path -> path.getFileName().toString().endsWith(".class");

    MavenLayerConfigurations.addFilesToLayer(sourceRoot, isClassFile, basePath, fileToLayerAdder);
    Mockito.verify(fileToLayerAdder)
        .add(sourceRoot.resolve("A.class"), basePath.resolve("A.class"));
    Mockito.verify(fileToLayerAdder)
        .add(sourceRoot.resolve("example/dir/C.class"), basePath.resolve("example/dir/C.class"));
    Mockito.verify(fileToLayerAdder)
        .add(sourceRoot.resolve("example/C.class"), basePath.resolve("example/C.class"));
    Mockito.verify(fileToLayerAdder)
        .add(sourceRoot.resolve("test/resources/leaf"), basePath.resolve("test/resources/leaf"));
    Mockito.verify(fileToLayerAdder)
        .add(sourceRoot.resolve("test/D.class"), basePath.resolve("test/D.class"));
    Mockito.verifyNoMoreInteractions(fileToLayerAdder);
  }

  private Artifact makeArtifact(Path path) {
    Artifact artifact = Mockito.mock(Artifact.class);
    Mockito.when(artifact.getFile()).thenReturn(path.toFile());
    return artifact;
  }
}
