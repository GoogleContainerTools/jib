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

import com.google.cloud.tools.jib.configuration.FilePermissions;
import com.google.cloud.tools.jib.filesystem.AbsoluteUnixPath;
import com.google.cloud.tools.jib.frontend.JavaLayerConfigurations;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.Collections;
import org.gradle.StartParameter;
import org.gradle.api.Project;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.java.archives.Manifest;
import org.gradle.api.java.archives.internal.DefaultManifest;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.configuration.ConsoleOutput;
import org.gradle.api.plugins.Convention;
import org.gradle.api.plugins.WarPluginConvention;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.bundling.War;
import org.gradle.jvm.tasks.Jar;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/** Test for {@link GradleProjectProperties}. */
@RunWith(MockitoJUnitRunner.class)
public class GradleProjectPropertiesTest {

  @Mock private FileResolver mockFileResolver;
  @Mock private Jar mockJar;
  @Mock private Jar mockJar2;
  @Mock private Project mockProject;
  @Mock private Convention mockConvention;
  @Mock private WarPluginConvention mockWarPluginConvection;
  @Mock private TaskContainer mockTaskContainer;
  @Mock private JavaLayerConfigurations mockJavaLayerConfigurations;
  @Mock private Logger mockLogger;
  @Mock private Gradle mockGradle;
  @Mock private StartParameter mockStartParameter;

  private Manifest manifest;
  private GradleProjectProperties gradleProjectProperties;

  @Before
  public void setup() {
    manifest = new DefaultManifest(mockFileResolver);
    Mockito.when(mockProject.getConvention()).thenReturn(mockConvention);
    Mockito.when(mockConvention.findPlugin(WarPluginConvention.class))
        .thenReturn(mockWarPluginConvection);
    Mockito.when(mockWarPluginConvection.getProject()).thenReturn(mockProject);
    Mockito.when(mockProject.getTasks()).thenReturn(mockTaskContainer);
    Mockito.when(mockTaskContainer.findByName("war")).thenReturn(Mockito.mock(War.class));
    Mockito.when(mockJar.getManifest()).thenReturn(manifest);

    Mockito.when(mockProject.getGradle()).thenReturn(mockGradle);
    Mockito.when(mockGradle.getStartParameter()).thenReturn(mockStartParameter);
    Mockito.when(mockStartParameter.getConsoleOutput()).thenReturn(ConsoleOutput.Auto);

    gradleProjectProperties =
        new GradleProjectProperties(mockProject, mockLogger, mockJavaLayerConfigurations);
  }

  @Test
  public void testGetMainClassFromJar_success() {
    manifest.attributes(ImmutableMap.of("Main-Class", "some.main.class"));
    Mockito.when(mockProject.getTasksByName("jar", false)).thenReturn(ImmutableSet.of(mockJar));
    Assert.assertEquals("some.main.class", gradleProjectProperties.getMainClassFromJar());
  }

  @Test
  public void testGetMainClassFromJar_missing() {
    Mockito.when(mockProject.getTasksByName("jar", false)).thenReturn(Collections.emptySet());
    Assert.assertNull(gradleProjectProperties.getMainClassFromJar());
  }

  @Test
  public void testGetMainClassFromJar_multiple() {
    manifest.attributes(ImmutableMap.of("Main-Class", "some.main.class"));
    Mockito.when(mockProject.getTasksByName("jar", false))
        .thenReturn(ImmutableSet.of(mockJar, mockJar2));
    Assert.assertNull(gradleProjectProperties.getMainClassFromJar());
  }

  @Test
  public void testGetWar_warProject() {
    Assert.assertNotNull(GradleProjectProperties.getWarTask(mockProject));
  }

  @Test
  public void testGetWar_noWarPlugin() {
    Mockito.when(mockConvention.findPlugin(WarPluginConvention.class)).thenReturn(null);

    Assert.assertNull(GradleProjectProperties.getWarTask(mockProject));
  }

  @Test
  public void testGetWar_noWarTask() {
    Mockito.when(mockTaskContainer.findByName("war")).thenReturn(null);

    Assert.assertNull(GradleProjectProperties.getWarTask(mockProject));
  }

  @Test
  public void testConvertPermissionsMap() {
    Assert.assertEquals(
        ImmutableMap.of(
            AbsoluteUnixPath.get("/test/folder/file1"),
            FilePermissions.fromOctalString("123"),
            AbsoluteUnixPath.get("/test/file2"),
            FilePermissions.fromOctalString("456")),
        GradleProjectProperties.convertPermissionsMap(
            ImmutableMap.of("/test/folder/file1", "123", "/test/file2", "456")));

    try {
      GradleProjectProperties.convertPermissionsMap(
          ImmutableMap.of("a path", "not valid permission"));
      Assert.fail();
    } catch (IllegalArgumentException ignored) {
      // pass
    }
  }
}
