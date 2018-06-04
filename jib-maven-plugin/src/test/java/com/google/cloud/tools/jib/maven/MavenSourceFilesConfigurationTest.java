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
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Build;
import org.apache.maven.project.MavenProject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests for {@link MavenSourceFilesConfiguration}. */
@RunWith(MockitoJUnitRunner.class)
public class MavenSourceFilesConfigurationTest {

  @Mock private MavenProject mockMavenProject;
  @Mock private Build mockBuild;

  private MavenSourceFilesConfiguration testMavenSourceFilesConfiguration;

  @Before
  public void setUp() throws IOException, URISyntaxException {
    Path sourcePath = Paths.get(Resources.getResource("application/source").toURI());
    Path outputPath = Paths.get(Resources.getResource("application/output").toURI());

    Mockito.when(mockMavenProject.getBuild()).thenReturn(mockBuild);
    Mockito.when(mockBuild.getSourceDirectory()).thenReturn(sourcePath.toString());
    Mockito.when(mockBuild.getOutputDirectory()).thenReturn(outputPath.toString());
    Set<Artifact> artifacts =
        new HashSet<>(
            Arrays.asList(
                makeArtifact(Paths.get("application", "dependencies", "libraryB.jar")),
                makeArtifact(Paths.get("application", "dependencies", "libraryA.jar")),
                makeArtifact(Paths.get("application", "dependencies", "dependency-1.0.0.jar"))));
    Mockito.when(mockMavenProject.getArtifacts()).thenReturn(artifacts);

    testMavenSourceFilesConfiguration =
        MavenSourceFilesConfiguration.getForProject(mockMavenProject);
  }

  @Test
  public void test_correctFiles() throws URISyntaxException {
    ImmutableList<Path> expectedDependenciesFiles =
        ImmutableList.of(
            Paths.get("application", "dependencies", "dependency-1.0.0.jar"),
            Paths.get("application", "dependencies", "libraryA.jar"),
            Paths.get("application", "dependencies", "libraryB.jar"));
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
        expectedDependenciesFiles, testMavenSourceFilesConfiguration.getDependenciesFiles());
    Assert.assertEquals(
        expectedResourcesFiles, testMavenSourceFilesConfiguration.getResourcesFiles());
    Assert.assertEquals(expectedClassesFiles, testMavenSourceFilesConfiguration.getClassesFiles());
  }

  @Test
  public void test_correctPathsOnImage() {
    Assert.assertEquals(
        "/app/libs/", testMavenSourceFilesConfiguration.getDependenciesPathOnImage());
    Assert.assertEquals(
        "/app/resources/", testMavenSourceFilesConfiguration.getResourcesPathOnImage());
    Assert.assertEquals("/app/classes/", testMavenSourceFilesConfiguration.getClassesPathOnImage());
  }

  private Artifact makeArtifact(Path path) {
    Artifact artifact = Mockito.mock(Artifact.class);
    Mockito.when(artifact.getFile()).thenReturn(path.toFile());
    return artifact;
  }
}
