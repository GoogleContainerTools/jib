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

package com.google.cloud.tools.jib.gradle;

import com.google.cloud.tools.jib.image.ImageReference;
import com.google.cloud.tools.jib.image.InvalidImageReferenceException;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.Collections;
import org.gradle.api.Project;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.java.archives.Manifest;
import org.gradle.api.java.archives.internal.DefaultManifest;
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
  @Mock private GradleBuildLogger mockGradleBuildLogger;
  @Mock private JibExtension mockJibExtension;
  @Mock private GradleBuildLogger mockBuildLogger;
  @Mock private GradleLayerConfigurations mockGradleLayerConfigurations;

  private Manifest manifest;
  private GradleProjectProperties gradleProjectProperties;

  @Before
  public void setup() {
    manifest = new DefaultManifest(mockFileResolver);
    Mockito.when(mockJar.getManifest()).thenReturn(manifest);

    gradleProjectProperties =
        new GradleProjectProperties(
            mockProject, mockGradleBuildLogger, mockGradleLayerConfigurations);
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
  public void testGetDockerTag_configured() throws InvalidImageReferenceException {
    Mockito.when(mockJibExtension.getTargetImage()).thenReturn("a/b:c");
    ImageReference result =
        gradleProjectProperties.getGeneratedTargetDockerTag(mockJibExtension, mockBuildLogger);
    Assert.assertEquals("a/b", result.getRepository());
    Assert.assertEquals("c", result.getTag());
    Mockito.verify(mockBuildLogger, Mockito.never()).lifecycle(Mockito.any());
  }

  @Test
  public void testGetDockerTag_notConfigured() throws InvalidImageReferenceException {
    Mockito.when(mockProject.getName()).thenReturn("project-name");
    Mockito.when(mockProject.getVersion()).thenReturn("project-version");
    Mockito.when(mockJibExtension.getTargetImage()).thenReturn(null);
    ImageReference result =
        gradleProjectProperties.getGeneratedTargetDockerTag(mockJibExtension, mockBuildLogger);
    Assert.assertEquals("project-name", result.getRepository());
    Assert.assertEquals("project-version", result.getTag());
    Mockito.verify(mockBuildLogger)
        .lifecycle(
            "Tagging image with generated image reference project-name:project-version. If you'd "
                + "like to specify a different tag, you can set the jib.to.image parameter in your "
                + "build.gradle, or use the --image=<MY IMAGE> commandline flag.");
  }
}
