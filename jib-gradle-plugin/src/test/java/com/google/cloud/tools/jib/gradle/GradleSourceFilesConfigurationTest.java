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

import com.google.common.io.Resources;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.Convention;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.SourceSetOutput;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

/** Test for {@link GradleSourceFilesConfiguration}. */
@RunWith(MockitoJUnitRunner.class)
public class GradleSourceFilesConfigurationTest {

  @Mock
  private Project mockProject;
  @Mock
  private SourceSet mockMainSourceSet;

  @Before
  public void setUp() {
    Convention mockConvention = Mockito.mock(Convention.class);
    JavaPluginConvention mockJavaPluginConvention = Mockito.mock(JavaPluginConvention.class);
    SourceSetContainer mockSourceSetContainer = Mockito.mock(SourceSetContainer.class);
    SourceSetOutput mockMainSourceSetOutput = Mockito.mock(SourceSetOutput.class);

    Mockito.when(mockProject.getConvention()).thenReturn(mockConvention);
    Mockito.when(mockConvention.getPlugin(JavaPluginConvention.class)).thenReturn(mockJavaPluginConvention);
    Mockito.when(mockJavaPluginConvention.getSourceSets()).thenReturn(mockSourceSetContainer);
    Mockito.when(mockSourceSetContainer.getByName("main")).thenReturn(mockMainSourceSet);
    Mockito.when(mockMainSourceSet.getOutput()).thenReturn(mockMainSourceSetOutput);
    Mockito.when(mockMainSourceSetOutput.getClassesDirs()).thenReturn()
    FileCollection classesOutputDirectories = mainSourceSet.getOutput().getClassesDirs()
  }

  @Test
  public void test_correctFiles() {
    List<Path> expectedDependenciesFiles =
        Arrays.asList(
            Paths.get("application", "dependencies", "dependency-1.0.0.jar"),
            Paths.get("application", "dependencies", "libraryA.jar"),
            Paths.get("application", "dependencies", "libraryB.jar"));
    List<Path> expectedResourcesFiles =
        Arrays.asList(
            Paths.get(Resources.getResource("application/output").toURI()).resolve("resourceA"),
            Paths.get(Resources.getResource("application/output").toURI()).resolve("resourceB"),
            Paths.get(Resources.getResource("application/output").toURI()).resolve("world"));
    List<Path> expectedClassesFiles =
        Arrays.asList(
            Paths.get(Resources.getResource("application/output").toURI())
                .resolve("HelloWorld.class"),
            Paths.get(Resources.getResource("application/output").toURI()).resolve("some.class"));
  }
}
