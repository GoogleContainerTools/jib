/*
 * Copyright 2018 Google LLC. All rights reserved.
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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Build;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests for {@link MavenLayerConfigurations}. */
@RunWith(MockitoJUnitRunner.class)
public class MavenLayerConfigurationsTest {

  @Rule public TestRepository testRepository = new TestRepository();

  @Mock private MavenProject mockMavenProject;
  @Mock private Build mockBuild;

  private MavenLayerConfigurations testMavenLayerConfigurations;

  @Before
  public void setUp() throws IOException, URISyntaxException, MojoExecutionException {
    Path sourcePath = Paths.get(Resources.getResource("application/source").toURI());
    Path outputPath = Paths.get(Resources.getResource("application/output").toURI());

    Mockito.when(mockMavenProject.getBuild()).thenReturn(mockBuild);
    Mockito.when(mockBuild.getSourceDirectory()).thenReturn(sourcePath.toString());
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

    testMavenLayerConfigurations =
        MavenLayerConfigurations.getForProject(mockMavenProject, Paths.get("nonexistent/path"));
  }

  @Test
  public void test_correctFiles() throws URISyntaxException {
    ImmutableList<Path> expectedDependenciesFiles =
        // on windows, these files may be in a different order, so sort
        ImmutableList.sortedCopyOf(
            ImmutableList.of(
                testRepository.artifactPathOnDisk("com.test", "dependency", "1.0.0"),
                Paths.get("application", "dependencies", "libraryA.jar"),
                Paths.get("application", "dependencies", "libraryB.jar")));
    ImmutableList<Path> expectedSnapshotDependenciesFiles =
        ImmutableList.of(
            testRepository.artifactPathOnDisk("com.test", "dependencyX", "1.0.0-SNAPSHOT"));
    ImmutableList<Path> expectedResourcesFiles =
        ImmutableList.of(
            Paths.get(Resources.getResource("application/output/directory").toURI()),
            Paths.get(Resources.getResource("application/output/resourceA").toURI()),
            Paths.get(Resources.getResource("application/output/resourceB").toURI()),
            Paths.get(Resources.getResource("application/output/world").toURI()));
    ImmutableList<Path> expectedClassesFiles =
        ImmutableList.of(
            Paths.get(Resources.getResource("application/output/HelloWorld.class").toURI()),
            Paths.get(Resources.getResource("application/output/package").toURI()),
            Paths.get(Resources.getResource("application/output/some.class").toURI()));

    Assert.assertEquals(
        expectedDependenciesFiles,
        testMavenLayerConfigurations.getDependenciesLayerEntry().getSourceFiles());
    Assert.assertEquals(
        expectedSnapshotDependenciesFiles,
        testMavenLayerConfigurations.getSnapshotDependenciesLayerEntry().getSourceFiles());
    Assert.assertEquals(
        expectedResourcesFiles,
        testMavenLayerConfigurations.getResourcesLayerEntry().getSourceFiles());
    Assert.assertEquals(
        expectedClassesFiles, testMavenLayerConfigurations.getClassesLayerEntry().getSourceFiles());
  }

  @Test
  public void test_extraFiles() throws URISyntaxException, IOException, MojoExecutionException {
    Path extraFilesDirectory = Paths.get(Resources.getResource("layer").toURI());

    testMavenLayerConfigurations =
        MavenLayerConfigurations.getForProject(mockMavenProject, extraFilesDirectory);

    ImmutableList<Path> expectedExtraFiles =
        ImmutableList.of(
            Paths.get(Resources.getResource("layer/a").toURI()),
            Paths.get(Resources.getResource("layer/c").toURI()),
            Paths.get(Resources.getResource("layer/foo").toURI()));

    Assert.assertEquals(
        expectedExtraFiles,
        testMavenLayerConfigurations.getExtraFilesLayerEntry().getSourceFiles());
  }

  @Test
  public void test_correctPathsOnImage() {
    Assert.assertEquals(
        "/app/libs/", testMavenLayerConfigurations.getDependenciesLayerEntry().getExtractionPath());
    Assert.assertEquals(
        "/app/libs/",
        testMavenLayerConfigurations.getSnapshotDependenciesLayerEntry().getExtractionPath());
    Assert.assertEquals(
        "/app/resources/",
        testMavenLayerConfigurations.getResourcesLayerEntry().getExtractionPath());
    Assert.assertEquals(
        "/app/classes/", testMavenLayerConfigurations.getClassesLayerEntry().getExtractionPath());
    Assert.assertEquals(
        "/", testMavenLayerConfigurations.getExtraFilesLayerEntry().getExtractionPath());
  }

  private Artifact makeArtifact(Path path) {
    Artifact artifact = Mockito.mock(Artifact.class);
    Mockito.when(artifact.getFile()).thenReturn(path.toFile());
    return artifact;
  }
}
