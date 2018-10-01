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

import com.google.cloud.tools.jib.filesystem.AbsoluteUnixPath;
import com.google.cloud.tools.jib.frontend.JavaLayerConfigurations;
import com.google.cloud.tools.jib.image.LayerEntry;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Resources;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.AbstractFileCollection;
import org.gradle.api.logging.Logger;
import org.gradle.api.plugins.Convention;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.plugins.WarPluginConvention;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.SourceSetOutput;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.bundling.War;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/** Test for {@link GradleLayerConfigurations}. */
@RunWith(MockitoJUnitRunner.class)
public class GradleLayerConfigurationsTest {

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
        expectedPaths, entries, LayerEntry::getAbsoluteExtractionPathString);
  }

  @Mock private Project mockProject;
  @Mock private Project mockWebappProject;
  @Mock private Convention mockConvention;
  @Mock private JavaPluginConvention mockJavaPluginConvention;
  @Mock private WarPluginConvention mockWarPluginConvention;
  @Mock private SourceSetContainer mockSourceSetContainer;
  @Mock private SourceSet mockMainSourceSet;
  @Mock private SourceSetOutput mockMainSourceSetOutput;
  @Mock private Logger mockLogger;
  @Mock private TaskContainer taskContainer;
  @Mock private War war;

  @Before
  public void setUp() throws URISyntaxException, IOException {
    Set<Path> classesFiles =
        ImmutableSet.of(Paths.get(Resources.getResource("application/classes").toURI()));
    FileCollection classesFileCollection = new TestFileCollection(classesFiles);
    Path resourcesOutputDir = Paths.get(Resources.getResource("application/resources").toURI());

    Set<Path> allFiles = new HashSet<>(classesFiles);
    allFiles.add(resourcesOutputDir);
    allFiles.add(Paths.get(Resources.getResource("application/dependencies/libraryB.jar").toURI()));
    allFiles.add(Paths.get(Resources.getResource("application/dependencies/libraryA.jar").toURI()));
    allFiles.add(
        Paths.get(Resources.getResource("application/dependencies/dependency-1.0.0.jar").toURI()));
    allFiles.add(
        Paths.get(
            Resources.getResource("application/dependencies/dependencyX-1.0.0-SNAPSHOT.jar")
                .toURI()));
    FileCollection runtimeFileCollection = new TestFileCollection(allFiles);

    Mockito.when(mockProject.getConvention()).thenReturn(mockConvention);
    Mockito.when(mockConvention.getPlugin(JavaPluginConvention.class))
        .thenReturn(mockJavaPluginConvention);
    Mockito.when(mockJavaPluginConvention.getSourceSets()).thenReturn(mockSourceSetContainer);
    Mockito.when(mockSourceSetContainer.getByName("main")).thenReturn(mockMainSourceSet);
    Mockito.when(mockMainSourceSet.getOutput()).thenReturn(mockMainSourceSetOutput);
    Mockito.when(mockMainSourceSetOutput.getClassesDirs()).thenReturn(classesFileCollection);
    Mockito.when(mockMainSourceSetOutput.getResourcesDir()).thenReturn(resourcesOutputDir.toFile());
    Mockito.when(mockMainSourceSet.getRuntimeClasspath()).thenReturn(runtimeFileCollection);
    // We can't commit an empty directory in Git, so it's created if it does not exist
    Path emptyDirectory =
        Paths.get(Resources.getResource("webapp").toURI())
            .resolve("jib-exploded-war/WEB-INF/classes/empty_dir");
    Files.createDirectories(emptyDirectory);
  }

  @Test
  public void test_correctFiles() throws URISyntaxException, IOException {
    Path applicationDirectory = Paths.get(Resources.getResource("application").toURI());
    ImmutableList<Path> expectedDependenciesFiles =
        ImmutableList.of(
            applicationDirectory.resolve("dependencies/dependency-1.0.0.jar"),
            applicationDirectory.resolve("dependencies/libraryA.jar"),
            applicationDirectory.resolve("dependencies/libraryB.jar"));
    ImmutableList<Path> expectedSnapshotDependenciesFiles =
        ImmutableList.of(
            applicationDirectory.resolve("dependencies/dependencyX-1.0.0-SNAPSHOT.jar"));
    ImmutableList<Path> expectedResourcesFiles =
        ImmutableList.of(
            applicationDirectory.resolve("resources"),
            applicationDirectory.resolve("resources/resourceA"),
            applicationDirectory.resolve("resources/resourceB"),
            applicationDirectory.resolve("resources/world"));
    ImmutableList<Path> expectedClassesFiles =
        ImmutableList.of(
            applicationDirectory.resolve("classes/HelloWorld.class"),
            applicationDirectory.resolve("classes/some.class"));
    ImmutableList<Path> expectedExtraFiles = ImmutableList.of();

    AbsoluteUnixPath appRoot = AbsoluteUnixPath.get("/app");
    JavaLayerConfigurations javaLayerConfigurations =
        GradleLayerConfigurations.getForProject(
            mockProject, mockLogger, Paths.get("nonexistent/path"), appRoot);
    assertSourcePathsUnordered(
        expectedDependenciesFiles, javaLayerConfigurations.getDependencyLayerEntries());
    assertSourcePathsUnordered(
        expectedSnapshotDependenciesFiles,
        javaLayerConfigurations.getSnapshotDependencyLayerEntries());
    assertSourcePathsUnordered(
        expectedResourcesFiles, javaLayerConfigurations.getResourceLayerEntries());
    assertSourcePathsUnordered(
        expectedClassesFiles, javaLayerConfigurations.getClassLayerEntries());
    assertSourcePathsUnordered(
        expectedExtraFiles, javaLayerConfigurations.getExtraFilesLayerEntries());
  }

  @Test
  public void test_noClassesFiles() throws IOException {
    Path nonexistentFile = Paths.get("/nonexistent/file");
    Mockito.when(mockMainSourceSetOutput.getClassesDirs())
        .thenReturn(new TestFileCollection(ImmutableSet.of(nonexistentFile)));

    AbsoluteUnixPath appRoot = AbsoluteUnixPath.get("/app");
    GradleLayerConfigurations.getForProject(
        mockProject, mockLogger, Paths.get("nonexistent/path"), appRoot);

    Mockito.verify(mockLogger)
        .info("Adding corresponding output directories of source sets to image");
    Mockito.verify(mockLogger).info("\t'" + nonexistentFile + "' (not found, skipped)");
    Mockito.verify(mockLogger).warn("No classes files were found - did you compile your project?");
  }

  @Test
  public void test_extraFiles() throws URISyntaxException, IOException {
    Path extraFilesDirectory = Paths.get(Resources.getResource("layer").toURI());

    JavaLayerConfigurations javaLayerConfigurations =
        GradleLayerConfigurations.getForProject(
            mockProject, mockLogger, extraFilesDirectory, AbsoluteUnixPath.get("/app"));

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
  public void testGetForProject_nonDefaultAppRoot() throws IOException, URISyntaxException {
    Path extraFilesDirectory = Paths.get(Resources.getResource("layer").toURI());

    JavaLayerConfigurations configuration =
        GradleLayerConfigurations.getForProject(
            mockProject, mockLogger, extraFilesDirectory, AbsoluteUnixPath.get("/my/app"));

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
            "/my/app/resources",
            "/my/app/resources/resourceA",
            "/my/app/resources/resourceB",
            "/my/app/resources/world"),
        configuration.getResourceLayerEntries());
    assertExtractionPathsUnordered(
        Arrays.asList("/my/app/classes/HelloWorld.class", "/my/app/classes/some.class"),
        configuration.getClassLayerEntries());
    assertExtractionPathsUnordered(
        Arrays.asList("/a", "/a/b", "/a/b/bar", "/c", "/c/cat", "/foo"),
        configuration.getExtraFilesLayerEntries());
  }

  @Test
  public void testGetForProject_defaultAppRoot() throws IOException, URISyntaxException {
    Path extraFilesDirectory = Paths.get(Resources.getResource("layer").toURI());

    JavaLayerConfigurations configuration =
        GradleLayerConfigurations.getForProject(
            mockProject,
            mockLogger,
            extraFilesDirectory,
            AbsoluteUnixPath.get(JibPlugin.DEFAULT_APP_ROOT));

    assertExtractionPathsUnordered(
        Arrays.asList(
            "/app/libs/dependency-1.0.0.jar", "/app/libs/libraryA.jar", "/app/libs/libraryB.jar"),
        configuration.getDependencyLayerEntries());
    assertExtractionPathsUnordered(
        Arrays.asList("/app/libs/dependencyX-1.0.0-SNAPSHOT.jar"),
        configuration.getSnapshotDependencyLayerEntries());
    assertExtractionPathsUnordered(
        Arrays.asList(
            "/app/resources",
            "/app/resources/resourceA",
            "/app/resources/resourceB",
            "/app/resources/world"),
        configuration.getResourceLayerEntries());
    assertExtractionPathsUnordered(
        Arrays.asList("/app/classes/HelloWorld.class", "/app/classes/some.class"),
        configuration.getClassLayerEntries());
    assertExtractionPathsUnordered(
        Arrays.asList("/a", "/a/b", "/a/b/bar", "/c", "/c/cat", "/foo"),
        configuration.getExtraFilesLayerEntries());
  }

  @Test
  public void testWebApp() throws URISyntaxException, IOException {
    Path webappDirectory = Paths.get(Resources.getResource("webapp").toURI());
    Mockito.when(mockWebappProject.getBuildDir()).thenReturn(webappDirectory.toFile());
    Mockito.when(mockWebappProject.getConvention()).thenReturn(mockConvention);
    Mockito.when(mockConvention.findPlugin(WarPluginConvention.class))
        .thenReturn(mockWarPluginConvention);
    Mockito.when(mockWarPluginConvention.getProject()).thenReturn(mockWebappProject);
    Mockito.when(mockWebappProject.getTasks()).thenReturn(taskContainer);
    Mockito.when(taskContainer.findByName("war")).thenReturn(war);

    Path extraFilesDirectory = Paths.get(Resources.getResource("layer").toURI());

    JavaLayerConfigurations configuration =
        GradleLayerConfigurations.getForProject(
            mockWebappProject, mockLogger, extraFilesDirectory, AbsoluteUnixPath.get("/my/app"));
    ImmutableList<Path> expectedDependenciesFiles =
        ImmutableList.of(
            webappDirectory.resolve("jib-exploded-war/WEB-INF/lib/dependency-1.0.0.jar"));
    ImmutableList<Path> expectedSnapshotDependenciesFiles =
        ImmutableList.of(
            webappDirectory.resolve("jib-exploded-war/WEB-INF/lib/dependencyX-1.0.0-SNAPSHOT.jar"));
    ImmutableList<Path> expectedResourcesFiles =
        ImmutableList.of(
            webappDirectory.resolve("jib-exploded-war/META-INF"),
            webappDirectory.resolve("jib-exploded-war/META-INF/context.xml"),
            webappDirectory.resolve("jib-exploded-war/Test.jsp"),
            webappDirectory.resolve("jib-exploded-war/WEB-INF/classes/empty_dir"),
            webappDirectory.resolve("jib-exploded-war/WEB-INF/classes/package/test.properties"),
            webappDirectory.resolve("jib-exploded-war/WEB-INF/web.xml"));
    ImmutableList<Path> expectedClassesFiles =
        ImmutableList.of(
            webappDirectory.resolve("jib-exploded-war/WEB-INF/classes/HelloWorld.class"),
            webappDirectory.resolve("jib-exploded-war/WEB-INF/classes/package/Other.class"));

    assertSourcePathsUnordered(
        expectedDependenciesFiles, configuration.getDependencyLayerEntries());
    assertSourcePathsUnordered(
        expectedSnapshotDependenciesFiles, configuration.getSnapshotDependencyLayerEntries());
    assertSourcePathsUnordered(expectedResourcesFiles, configuration.getResourceLayerEntries());
    assertSourcePathsUnordered(expectedClassesFiles, configuration.getClassLayerEntries());
    assertExtractionPathsUnordered(
        Arrays.asList("/a", "/a/b", "/a/b/bar", "/c", "/c/cat", "/foo"),
        configuration.getExtraFilesLayerEntries());

    assertExtractionPathsUnordered(
        Arrays.asList("/my/app/WEB-INF/lib/dependency-1.0.0.jar"),
        configuration.getDependencyLayerEntries());
    assertExtractionPathsUnordered(
        Arrays.asList("/my/app/WEB-INF/lib/dependencyX-1.0.0-SNAPSHOT.jar"),
        configuration.getSnapshotDependencyLayerEntries());
    assertExtractionPathsUnordered(
        Arrays.asList(
            "/my/app/META-INF",
            "/my/app/META-INF/context.xml",
            "/my/app/Test.jsp",
            "/my/app/WEB-INF/classes/empty_dir",
            "/my/app/WEB-INF/classes/package/test.properties",
            "/my/app/WEB-INF/web.xml"),
        configuration.getResourceLayerEntries());
    assertExtractionPathsUnordered(
        Arrays.asList(
            "/my/app/WEB-INF/classes/HelloWorld.class",
            "/my/app/WEB-INF/classes/package/Other.class"),
        configuration.getClassLayerEntries());
    assertExtractionPathsUnordered(
        Arrays.asList("/a", "/a/b", "/a/b/bar", "/c", "/c/cat", "/foo"),
        configuration.getExtraFilesLayerEntries());
  }

  @Test
  public void testWebApp_defaultWebAppRoot() throws URISyntaxException, IOException {
    Path webappDirectory = Paths.get(Resources.getResource("webapp").toURI());
    Mockito.when(mockWebappProject.getBuildDir()).thenReturn(webappDirectory.toFile());
    Mockito.when(mockWebappProject.getConvention()).thenReturn(mockConvention);
    Mockito.when(mockConvention.findPlugin(WarPluginConvention.class))
        .thenReturn(mockWarPluginConvention);
    Mockito.when(mockWarPluginConvention.getProject()).thenReturn(mockWebappProject);
    Mockito.when(mockWebappProject.getTasks()).thenReturn(taskContainer);
    Mockito.when(taskContainer.findByName("war")).thenReturn(war);

    Path extraFilesDirectory = Paths.get(Resources.getResource("layer").toURI());

    JavaLayerConfigurations configuration =
        GradleLayerConfigurations.getForProject(
            mockWebappProject,
            mockLogger,
            extraFilesDirectory,
            AbsoluteUnixPath.get(JibPlugin.DEFAULT_WEBAPP_ROOT));

    assertExtractionPathsUnordered(
        Arrays.asList("/jetty/webapps/ROOT/WEB-INF/lib/dependency-1.0.0.jar"),
        configuration.getDependencyLayerEntries());
    assertExtractionPathsUnordered(
        Arrays.asList("/jetty/webapps/ROOT/WEB-INF/lib/dependencyX-1.0.0-SNAPSHOT.jar"),
        configuration.getSnapshotDependencyLayerEntries());
    assertExtractionPathsUnordered(
        Arrays.asList(
            "/jetty/webapps/ROOT/META-INF",
            "/jetty/webapps/ROOT/META-INF/context.xml",
            "/jetty/webapps/ROOT/Test.jsp",
            "/jetty/webapps/ROOT/WEB-INF/classes/empty_dir",
            "/jetty/webapps/ROOT/WEB-INF/classes/package/test.properties",
            "/jetty/webapps/ROOT/WEB-INF/web.xml"),
        configuration.getResourceLayerEntries());
    assertExtractionPathsUnordered(
        Arrays.asList(
            "/jetty/webapps/ROOT/WEB-INF/classes/HelloWorld.class",
            "/jetty/webapps/ROOT/WEB-INF/classes/package/Other.class"),
        configuration.getClassLayerEntries());
    assertExtractionPathsUnordered(
        Arrays.asList("/a", "/a/b", "/a/b/bar", "/c", "/c/cat", "/foo"),
        configuration.getExtraFilesLayerEntries());
  }
}
