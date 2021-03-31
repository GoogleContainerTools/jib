/*
 * Copyright 2019 Google LLC.
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
import org.gradle.api.Task;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.WarPlugin;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.War;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.RestoreSystemProperties;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.boot.gradle.plugin.SpringBootPlugin;
import org.springframework.boot.gradle.tasks.bundling.BootWar;

/** Tests for {@link TaskCommon}. */
@RunWith(MockitoJUnitRunner.class)
public class TaskCommonTest {

  @Rule public final RestoreSystemProperties systemPropertyRestorer = new RestoreSystemProperties();

  @Before
  public void setUp() {
    System.clearProperty("jib.extraDirectories.paths");
    System.clearProperty("jib.extraDirectories.permissions");
  }

  @Test
  public void testGetWarTask_normalJavaProject() {
    Project project = ProjectBuilder.builder().build();
    project.getPlugins().apply(JavaPlugin.class);

    TaskProvider<Task> warProviderTask = TaskCommon.getWarTaskProvider(project);
    Assert.assertNull(warProviderTask);
  }

  @Test
  public void testGetWarTask_normalWarProject() {
    Project project = ProjectBuilder.builder().build();
    project.getPlugins().apply(WarPlugin.class);

    TaskProvider<Task> warTask = TaskCommon.getWarTaskProvider(project);
    Assert.assertNotNull(warTask);
    Assert.assertNotNull(warTask instanceof War);
  }

  @Test
  public void testGetBootWarTask_bootWarProject() {
    Project project = ProjectBuilder.builder().build();
    project.getPlugins().apply(WarPlugin.class);
    project.getPlugins().apply(SpringBootPlugin.class);

    TaskProvider<Task> bootWarTask = TaskCommon.getBootWarTaskProvider(project);
    Assert.assertNotNull(bootWarTask);
    Assert.assertNotNull(bootWarTask instanceof BootWar);
  }
}