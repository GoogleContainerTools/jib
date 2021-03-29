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

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.cloud.tools.jib.ProjectInfo;
import com.google.cloud.tools.jib.api.LogEvent;
import com.google.cloud.tools.jib.plugins.common.ProjectProperties;
import com.google.common.util.concurrent.Futures;
import java.util.Optional;
import java.util.concurrent.Future;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.WarPlugin;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.War;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.RestoreSystemProperties;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.boot.gradle.plugin.SpringBootPlugin;
import org.springframework.boot.gradle.tasks.bundling.BootWar;

/** Tests for {@link TaskCommon}. */
@RunWith(MockitoJUnitRunner.class)
public class TaskCommonTest {

  @Rule public final RestoreSystemProperties systemPropertyRestorer = new RestoreSystemProperties();
  @Mock private ProjectProperties mockProjectProperties;

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
    assertThat(warProviderTask).isNull();
  }

  @Test
  public void testGetWarTask_normalWarProject() {
    Project project = ProjectBuilder.builder().build();
    project.getPlugins().apply(WarPlugin.class);

    TaskProvider<Task> warTask = TaskCommon.getWarTaskProvider(project);
    assertThat(warTask).isNotNull();
    assertThat(warTask.get()).isInstanceOf(War.class);
  }

  @Test
  public void testGetBootWarTask_bootWarProject() {
    Project project = ProjectBuilder.builder().build();
    project.getPlugins().apply(WarPlugin.class);
    project.getPlugins().apply(SpringBootPlugin.class);

    TaskProvider<Task> bootWarTask = TaskCommon.getBootWarTaskProvider(project);
    assertThat(bootWarTask).isNotNull();
    assertThat(bootWarTask.get()).isInstanceOf(BootWar.class);
  }

  @Test
  public void testFinishUpdateChecker_correctMessageReturned() {
    when(mockProjectProperties.getToolName()).thenReturn("tool-name");
    when(mockProjectProperties.getToolVersion()).thenReturn("2.0.0");
    Future<Optional<String>> updateCheckFuture = Futures.immediateFuture(Optional.of("2.1.0"));
    TaskCommon.finishUpdateChecker(mockProjectProperties, updateCheckFuture);

    verify(mockProjectProperties)
        .log(
            LogEvent.lifecycle(
                "\n\u001B[33mA new version of tool-name (2.1.0) is available (currently using 2.0.0). "
                    + "Update your build configuration to use the latest features and fixes!\n"
                    + ProjectInfo.GITHUB_URL
                    + "/blob/master/jib-gradle-plugin/CHANGELOG.md.\u001B[0m\n\n"
                    + "Please see "
                    + ProjectInfo.GITHUB_URL
                    + "/blob/master/docs/privacy.md for info on disabling this update check.\n"));
  }
}
