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

import org.gradle.api.Project;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.java.archives.Manifest;
import org.gradle.api.java.archives.internal.DefaultManifest;
import org.gradle.api.logging.Logger;
import org.gradle.internal.impldep.com.google.common.collect.ImmutableMap;
import org.gradle.internal.impldep.com.google.common.collect.ImmutableSet;
import org.gradle.jvm.tasks.Jar;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

/** Tests for {@link ProjectProperties}. */
public class ProjectPropertiesTest {

  @Test
  public void testGetMainClassFromJarTask() {
    Manifest fakeManifest = new DefaultManifest(Mockito.mock(FileResolver.class));
    fakeManifest.attributes(ImmutableMap.of("Main-Class", "some.main.class"));

    Jar mockJar = Mockito.mock(Jar.class);
    Mockito.when(mockJar.getManifest()).thenReturn(fakeManifest);

    Project mockProject = Mockito.mock(Project.class);
    Mockito.when(mockProject.getTasksByName("jar", false)).thenReturn(ImmutableSet.of(mockJar));

    ProjectProperties testProjectProperties =
        new ProjectProperties(mockProject, Mockito.mock(Logger.class));

    Assert.assertEquals("some.main.class", testProjectProperties.getMainClassFromJarTask());
  }
}
