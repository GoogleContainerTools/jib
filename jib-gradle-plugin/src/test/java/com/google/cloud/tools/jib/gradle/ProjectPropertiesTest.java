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

import java.util.Collections;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.java.archives.Manifest;
import org.gradle.api.java.archives.internal.DefaultManifest;
import org.gradle.api.plugins.Convention;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.SourceSetOutput;
import org.gradle.internal.impldep.com.google.common.collect.ImmutableMap;
import org.gradle.internal.impldep.com.google.common.collect.ImmutableSet;
import org.gradle.jvm.tasks.Jar;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests for {@link ProjectProperties}. */
@RunWith(MockitoJUnitRunner.class)
public class ProjectPropertiesTest {

  @Mock private FileResolver mockFileResolver;
  @Mock private Jar mockJar;
  @Mock private Project mockProject;
  @Mock private GradleBuildLogger mockGradleBuildLogger;

  @Mock private Convention mockConvention;
  @Mock private JavaPluginConvention mockPluginConvention;
  @Mock private SourceSetContainer mockSourceSetContainer;
  @Mock private SourceSet mockSourceSet;
  @Mock private SourceSetOutput mockSourceSetOutput;
  @Mock private FileCollection mockFileCollection;

  private Manifest fakeManifest;
  private ProjectProperties testProjectProperties;

  @Before
  public void setUp() {
    fakeManifest = new DefaultManifest(mockFileResolver);
    Mockito.when(mockJar.getManifest()).thenReturn(fakeManifest);

    Mockito.when(mockProject.getConvention()).thenReturn(mockConvention);
    Mockito.when(mockConvention.getPlugin(JavaPluginConvention.class))
        .thenReturn(mockPluginConvention);
    Mockito.when(mockPluginConvention.getSourceSets()).thenReturn(mockSourceSetContainer);
    Mockito.when(mockSourceSetContainer.getByName("main")).thenReturn(mockSourceSet);
    Mockito.when(mockSourceSet.getOutput()).thenReturn(mockSourceSetOutput);
    Mockito.when(mockSourceSetOutput.getClassesDirs()).thenReturn(mockFileCollection);
    Mockito.when(mockFileCollection.getAsPath()).thenReturn("a/b/c");

    testProjectProperties = new ProjectProperties(mockProject, mockGradleBuildLogger);
  }

  @Test
  public void testGetMainClass() {
    fakeManifest.attributes(ImmutableMap.of("Main-Class", "some.main.class"));

    Mockito.when(mockProject.getTasksByName("jar", false)).thenReturn(ImmutableSet.of(mockJar));

    Assert.assertEquals("some.main.class", testProjectProperties.getMainClass(null));
    Assert.assertEquals("configured", testProjectProperties.getMainClass("configured"));
  }

  @Test
  public void testGetMainClass_noJarTask() {
    Mockito.when(mockProject.getTasksByName("jar", false)).thenReturn(Collections.emptySet());

    assertGetMainClassFails();
  }

  @Test
  public void testGetMainClass_couldNotFindInJarTask() {
    Mockito.when(mockProject.getTasksByName("jar", false)).thenReturn(ImmutableSet.of(mockJar));

    assertGetMainClassFails();
  }

  @Test
  public void testGetMainClass_notValid() {
    fakeManifest.attributes(ImmutableMap.of("Main-Class", "${start-class}"));

    Mockito.when(mockProject.getTasksByName("jar", false)).thenReturn(ImmutableSet.of(mockJar));

    Assert.assertEquals("${start-class}", testProjectProperties.getMainClass(null));
    Mockito.verify(mockGradleBuildLogger)
        .warn("'mainClass' is not a valid Java class : ${start-class}");
  }

  private void assertGetMainClassFails() {
    try {
      testProjectProperties.getMainClass(null);
      Assert.fail("Main class not expected");

    } catch (GradleException ex) {
      Mockito.verify(mockGradleBuildLogger)
          .debug(
              "Could not find main class specified in a 'jar' task; attempting to infer main class.");
      Assert.assertThat(
          ex.getMessage(), CoreMatchers.containsString("add a `mainClass` configuration to jib"));
    }
  }
}
