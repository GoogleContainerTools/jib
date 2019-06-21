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
import org.gradle.api.logging.Logger;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.WarPlugin;
import org.gradle.api.tasks.bundling.War;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.boot.gradle.plugin.SpringBootPlugin;
import org.springframework.boot.gradle.tasks.bundling.BootWar;

/** Tests for {@link TaskCommon}. */
@RunWith(MockitoJUnitRunner.class)
public class TaskCommonTest {

  @Mock private JibExtension jibExtension;
  @Mock private Logger logger;

  @Before
  public void setUp() {
    Assert.assertNull(System.getProperty("jib.extraDirectory.path"));
    Assert.assertNull(System.getProperty("jib.extraDirectory.permissions"));
    Assert.assertNull(System.getProperty("jib.extraDirectories.paths"));
    Assert.assertNull(System.getProperty("jib.extraDirectories.permissions"));
  }

  @After
  public void tearDown() {
    System.clearProperty("jib.extraDirectory.path");
    System.clearProperty("jib.extraDirectory.permissions");
    System.clearProperty("jib.extraDirectories.paths");
    System.clearProperty("jib.extraDirectories.permissions");
  }

  @Test
  public void testCheckDeprecatedUsage_default() {
    TaskCommon.checkDeprecatedUsage(jibExtension, logger);
    Mockito.verify(logger, Mockito.never()).warn(Mockito.anyString());
  }

  @Test
  public void testCheckDeprecatedUsage_extraDirectoriesConfigured() {
    jibExtension.extraDirectoriesConfigured = true;
    TaskCommon.checkDeprecatedUsage(jibExtension, logger);
    Mockito.verify(logger, Mockito.never()).warn(Mockito.anyString());
  }

  @Test
  public void testCheckDeprecatedUsage_extraDirectoryPathPropertySet() {
    System.setProperty("jib.extraDirectory.path", "something");
    TaskCommon.checkDeprecatedUsage(jibExtension, logger);
    Mockito.verify(logger, Mockito.times(1))
        .warn(
            "'jib.extraDirectory', 'jib.extraDirectory.path', and 'jib.extraDirectory.permissions' "
                + "are deprecated; use 'jib.extraDirectories.paths' and "
                + "'jib.extraDirectories.permissions'");
  }

  @Test
  public void testCheckDeprecatedUsage_extraDirectoryPermissionsPropertySet() {
    System.setProperty("jib.extraDirectory.permissions", "something");
    TaskCommon.checkDeprecatedUsage(jibExtension, logger);
    Mockito.verify(logger, Mockito.times(1))
        .warn(
            "'jib.extraDirectory', 'jib.extraDirectory.path', and 'jib.extraDirectory.permissions' "
                + "are deprecated; use 'jib.extraDirectories.paths' and "
                + "'jib.extraDirectories.permissions'");
  }

  @Test
  public void testCheckDeprecatedUsage_extraDirectoryAndExtraDirectoriesPropertiesSet() {
    System.setProperty("jib.extraDirectory.path", "something");
    System.setProperty("jib.extraDirectories.permissions", "something");

    try {
      TaskCommon.checkDeprecatedUsage(jibExtension, logger);
      Assert.fail();
    } catch (IllegalArgumentException ex) {
      Assert.assertEquals(
          "You cannot configure both 'jib.extraDirectory.path' and 'jib.extraDirectories.paths'",
          ex.getMessage());
    }
  }

  @Test
  public void testCheckDeprecatedUsage_extraDirectoryConfigured() {
    jibExtension.extraDirectoryConfigured = true;
    TaskCommon.checkDeprecatedUsage(jibExtension, logger);
    Mockito.verify(logger, Mockito.times(1))
        .warn(
            "'jib.extraDirectory', 'jib.extraDirectory.path', and 'jib.extraDirectory.permissions' "
                + "are deprecated; use 'jib.extraDirectories.paths' and "
                + "'jib.extraDirectories.permissions'");
  }

  @Test
  public void testCheckDeprecatedUsage_extraDirectoryAndExtraDirectoriesConfigured() {
    jibExtension.extraDirectoryConfigured = true;
    jibExtension.extraDirectoriesConfigured = true;
    try {
      TaskCommon.checkDeprecatedUsage(jibExtension, logger);
      Assert.fail();
    } catch (IllegalArgumentException ex) {
      Assert.assertEquals(
          "You cannot configure both 'jib.extraDirectory.path' and 'jib.extraDirectories.paths'",
          ex.getMessage());
    }
  }

  @Test
  public void testGetWarTask_normalJavaProject() {
    Project project = ProjectBuilder.builder().build();
    project.getPlugins().apply(JavaPlugin.class);

    War warTask = TaskCommon.getWarTask(project);

    Assert.assertNull(warTask);
  }

  @Test
  public void testGetWarTask_bootJavaProject() {
    Project project = ProjectBuilder.builder().build();
    project.getPlugins().apply(JavaPlugin.class);
    project.getPlugins().apply(SpringBootPlugin.class);

    War warTask = TaskCommon.getWarTask(project);

    Assert.assertNull(warTask);
  }

  @Test
  public void testGetWarTask_normalWarProject() {
    Project project = ProjectBuilder.builder().build();
    project.getPlugins().apply(WarPlugin.class);

    War warTask = TaskCommon.getWarTask(project);

    Assert.assertNotNull(warTask);
  }

  @Test
  public void testGetWarTask_bootWarProject() {
    Project project = ProjectBuilder.builder().build();
    project.getPlugins().apply(WarPlugin.class);
    project.getPlugins().apply(SpringBootPlugin.class);

    War warTask = TaskCommon.getWarTask(project);

    Assert.assertNotNull(warTask);
    Assert.assertTrue(warTask instanceof BootWar);
  }
}
