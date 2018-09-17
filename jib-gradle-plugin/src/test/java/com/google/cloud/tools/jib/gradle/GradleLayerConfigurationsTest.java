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

import com.google.cloud.tools.jib.frontend.JavaLayerConfigurations;
import com.google.cloud.tools.jib.image.LayerEntry;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Resources;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.AbstractFileCollection;
import org.gradle.api.plugins.Convention;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.SourceSetOutput;
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

    private TestFileCollection(Set<File> files) {
      this.files = files;
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

  private static ImmutableList<Path> getSourceFilesFromLayerEntries(
      ImmutableList<LayerEntry> layerEntries) {
    return layerEntries
        .stream()
        .map(LayerEntry::getSourceFile)
        .collect(ImmutableList.toImmutableList());
  }

  @Mock private Project mockProject;
  @Mock private Convention mockConvention;
  @Mock private JavaPluginConvention mockJavaPluginConvention;
  @Mock private SourceSetContainer mockSourceSetContainer;
  @Mock private SourceSet mockMainSourceSet;
  @Mock private SourceSetOutput mockMainSourceSetOutput;
  @Mock private GradleJibLogger mockGradleJibLogger;

  @Before
  public void setUp() throws URISyntaxException {
    Set<File> classesFiles =
        ImmutableSet.of(Paths.get(Resources.getResource("application/classes").toURI()).toFile());
    FileCollection classesFileCollection = new TestFileCollection(classesFiles);
    File resourcesOutputDir =
        Paths.get(Resources.getResource("application/resources").toURI()).toFile();

    Set<File> allFiles = new HashSet<>(classesFiles);
    allFiles.add(resourcesOutputDir);
    allFiles.add(
        Paths.get(Resources.getResource("application/dependencies/libraryB.jar").toURI()).toFile());
    allFiles.add(
        Paths.get(Resources.getResource("application/dependencies/libraryA.jar").toURI()).toFile());
    allFiles.add(
        Paths.get(Resources.getResource("application/dependencies/dependency-1.0.0.jar").toURI())
            .toFile());
    allFiles.add(
        Paths.get(
                Resources.getResource("application/dependencies/dependencyX-1.0.0-SNAPSHOT.jar")
                    .toURI())
            .toFile());
    FileCollection runtimeFileCollection = new TestFileCollection(allFiles);

    Mockito.when(mockProject.getConvention()).thenReturn(mockConvention);
    Mockito.when(mockConvention.getPlugin(JavaPluginConvention.class))
        .thenReturn(mockJavaPluginConvention);
    Mockito.when(mockJavaPluginConvention.getSourceSets()).thenReturn(mockSourceSetContainer);
    Mockito.when(mockSourceSetContainer.getByName("main")).thenReturn(mockMainSourceSet);
    Mockito.when(mockMainSourceSet.getOutput()).thenReturn(mockMainSourceSetOutput);
    Mockito.when(mockMainSourceSetOutput.getClassesDirs()).thenReturn(classesFileCollection);
    Mockito.when(mockMainSourceSetOutput.getResourcesDir()).thenReturn(resourcesOutputDir);
    Mockito.when(mockMainSourceSet.getRuntimeClasspath()).thenReturn(runtimeFileCollection);
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
            applicationDirectory.resolve("resources/resourceA"),
            applicationDirectory.resolve("resources/resourceB"),
            applicationDirectory.resolve("resources/world"));
    ImmutableList<Path> expectedClassesFiles =
        ImmutableList.of(
            applicationDirectory.resolve("classes").resolve("HelloWorld.class"),
            applicationDirectory.resolve("classes/some.class"));
    ImmutableList<Path> expectedExtraFiles = ImmutableList.of();

    JavaLayerConfigurations javaLayerConfigurations =
        GradleLayerConfigurations.getForProject(
            mockProject, mockGradleJibLogger, Paths.get("nonexistent/path"));
    Assert.assertEquals(
        expectedDependenciesFiles,
        getSourceFilesFromLayerEntries(javaLayerConfigurations.getDependencyLayerEntries()));
    Assert.assertEquals(
        expectedSnapshotDependenciesFiles,
        getSourceFilesFromLayerEntries(
            javaLayerConfigurations.getSnapshotDependencyLayerEntries()));
    Assert.assertEquals(
        expectedResourcesFiles,
        getSourceFilesFromLayerEntries(javaLayerConfigurations.getResourceLayerEntries()));
    Assert.assertEquals(
        expectedClassesFiles,
        getSourceFilesFromLayerEntries(javaLayerConfigurations.getClassLayerEntries()));
    Assert.assertEquals(
        expectedExtraFiles,
        getSourceFilesFromLayerEntries(javaLayerConfigurations.getExtraFilesLayerEntries()));
  }

  @Test
  public void test_noClassesFiles() throws IOException {
    File nonexistentFile = new File("/nonexistent/file");
    Mockito.when(mockMainSourceSetOutput.getClassesDirs())
        .thenReturn(new TestFileCollection(ImmutableSet.of(nonexistentFile)));

    GradleLayerConfigurations.getForProject(
        mockProject, mockGradleJibLogger, Paths.get("nonexistent/path"));

    Mockito.verify(mockGradleJibLogger)
        .info("Adding corresponding output directories of source sets to image");
    Mockito.verify(mockGradleJibLogger).info("\t'" + nonexistentFile + "' (not found, skipped)");
    Mockito.verify(mockGradleJibLogger)
        .warn("No classes files were found - did you compile your project?");
  }

  @Test
  public void test_extraFiles() throws URISyntaxException, IOException {
    Path extraFilesDirectory = Paths.get(Resources.getResource("layer").toURI());

    JavaLayerConfigurations javaLayerConfigurations =
        GradleLayerConfigurations.getForProject(
            mockProject, mockGradleJibLogger, extraFilesDirectory);

    ImmutableList<Path> expectedExtraFiles =
        ImmutableList.of(
            extraFilesDirectory.resolve("a"),
            extraFilesDirectory.resolve("a/b"),
            extraFilesDirectory.resolve("a/b/bar"),
            extraFilesDirectory.resolve("c"),
            extraFilesDirectory.resolve("c/cat"),
            extraFilesDirectory.resolve("foo"));

    Assert.assertEquals(
        expectedExtraFiles,
        getSourceFilesFromLayerEntries(javaLayerConfigurations.getExtraFilesLayerEntries()));
  }
}
