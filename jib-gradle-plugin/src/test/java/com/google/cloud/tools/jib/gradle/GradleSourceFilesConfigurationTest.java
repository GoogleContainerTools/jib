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

import com.google.common.collect.ImmutableSet;
import com.google.common.io.Resources;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.Convention;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.SourceSetOutput;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/** Test for {@link GradleSourceFilesConfiguration}. */
@RunWith(MockitoJUnitRunner.class)
public class GradleSourceFilesConfigurationTest {

  private GradleSourceFilesConfiguration testGradleSourceFilesConfiguration;

  @Before
  public void setUp() throws URISyntaxException, IOException {
    Project mockProject = Mockito.mock(Project.class);
    Convention mockConvention = Mockito.mock(Convention.class);
    JavaPluginConvention mockJavaPluginConvention = Mockito.mock(JavaPluginConvention.class);
    SourceSetContainer mockSourceSetContainer = Mockito.mock(SourceSetContainer.class);
    SourceSet mockMainSourceSet = Mockito.mock(SourceSet.class);
    SourceSetOutput mockMainSourceSetOutput = Mockito.mock(SourceSetOutput.class);
    FileCollection mockClassesFileCollection = Mockito.mock(FileCollection.class);
    FileCollection mockRuntimeFileCollection = Mockito.mock(FileCollection.class);
    FileCollection mockDependenciesAndResourcesFileCollection = Mockito.mock(FileCollection.class);

    Set<File> classesOutputDirs =
        ImmutableSet.of(Paths.get(Resources.getResource("application/classes").toURI()).toFile());
    File resourcesOutputDir =
        Paths.get(Resources.getResource("application/resources").toURI()).toFile();
    Set<File> dependenciesAndResourcesFiles = new HashSet<>();
    dependenciesAndResourcesFiles.add(resourcesOutputDir);
    dependenciesAndResourcesFiles.add(
        Paths.get(Resources.getResource("application/dependencies/libraryB.jar").toURI()).toFile());
    dependenciesAndResourcesFiles.add(
        Paths.get(Resources.getResource("application/dependencies/libraryA.jar").toURI()).toFile());
    dependenciesAndResourcesFiles.add(
        Paths.get(Resources.getResource("application/dependencies/dependency-1.0.0.jar").toURI())
            .toFile());

    Mockito.when(mockProject.getConvention()).thenReturn(mockConvention);
    Mockito.when(mockConvention.getPlugin(JavaPluginConvention.class))
        .thenReturn(mockJavaPluginConvention);
    Mockito.when(mockJavaPluginConvention.getSourceSets()).thenReturn(mockSourceSetContainer);
    Mockito.when(mockSourceSetContainer.getByName("main")).thenReturn(mockMainSourceSet);
    Mockito.when(mockMainSourceSet.getOutput()).thenReturn(mockMainSourceSetOutput);
    Mockito.when(mockMainSourceSetOutput.getClassesDirs()).thenReturn(mockClassesFileCollection);
    Mockito.when(mockClassesFileCollection.iterator()).thenReturn(classesOutputDirs.iterator());
    Mockito.when(mockMainSourceSetOutput.getResourcesDir()).thenReturn(resourcesOutputDir);
    Mockito.when(mockMainSourceSet.getRuntimeClasspath()).thenReturn(mockRuntimeFileCollection);
    Mockito.when(mockRuntimeFileCollection.minus(mockClassesFileCollection))
        .thenReturn(mockDependenciesAndResourcesFileCollection);
    Mockito.when(mockDependenciesAndResourcesFileCollection.iterator())
        .thenReturn(dependenciesAndResourcesFiles.iterator());

    testGradleSourceFilesConfiguration = GradleSourceFilesConfiguration.getForProject(mockProject);
  }

  @Test
  public void test_correctFiles() throws URISyntaxException {
    List<Path> expectedDependenciesFiles =
        Arrays.asList(
            Paths.get(
                Resources.getResource("application/dependencies/dependency-1.0.0.jar").toURI()),
            Paths.get(Resources.getResource("application/dependencies/libraryA.jar").toURI()),
            Paths.get(Resources.getResource("application/dependencies/libraryB.jar").toURI()));
    List<Path> expectedResourcesFiles =
        Arrays.asList(
            Paths.get(Resources.getResource("application/resources").toURI()).resolve("resourceA"),
            Paths.get(Resources.getResource("application/resources").toURI()).resolve("resourceB"),
            Paths.get(Resources.getResource("application/resources").toURI()).resolve("world"));
    List<Path> expectedClassesFiles =
        Arrays.asList(
            Paths.get(Resources.getResource("application/classes").toURI())
                .resolve("HelloWorld.class"),
            Paths.get(Resources.getResource("application/classes").toURI()).resolve("some.class"));

    Assert.assertEquals(
        expectedDependenciesFiles, testGradleSourceFilesConfiguration.getDependenciesFiles());
    Assert.assertEquals(
        expectedResourcesFiles, testGradleSourceFilesConfiguration.getResourcesFiles());
    Assert.assertEquals(expectedClassesFiles, testGradleSourceFilesConfiguration.getClassesFiles());
  }

  @Test
  public void test_correctPathsOnImage() {
    Assert.assertEquals(
        "/app/libs/", testGradleSourceFilesConfiguration.getDependenciesPathOnImage());
    Assert.assertEquals(
        "/app/resources/", testGradleSourceFilesConfiguration.getResourcesPathOnImage());
    Assert.assertEquals(
        "/app/classes/", testGradleSourceFilesConfiguration.getClassesPathOnImage());
  }
}
