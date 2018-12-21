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

import com.google.cloud.tools.jib.plugins.common.RawConfiguration;
import java.util.Optional;
import org.gradle.api.Project;
import org.gradle.api.plugins.Convention;
import org.gradle.api.plugins.WarPluginConvention;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.bundling.War;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests for {@link TaskCommon}. */
@RunWith(MockitoJUnitRunner.class)
public class TaskCommonTest {

  @Mock private RawConfiguration rawConfiguration;
  @Mock private Project project;
  @Mock private Convention convention;
  @Mock private WarPluginConvention warPluginConvention;
  @Mock private TaskContainer taskContainer;
  @Mock private War war;

  @Before
  public void setUp() {
    Mockito.when(project.getConvention()).thenReturn(convention);
    Mockito.when(convention.findPlugin(WarPluginConvention.class)).thenReturn(warPluginConvention);
    Mockito.when(warPluginConvention.getProject()).thenReturn(project);
    Mockito.when(project.getTasks()).thenReturn(taskContainer);
  }

  @Test
  public void testIsWarPackaging_jarProjectAndNoOverride() {
    Mockito.when(rawConfiguration.getPackagingOverride()).thenReturn(Optional.empty());

    Assert.assertFalse(TaskCommon.isWarContainerization(project, rawConfiguration));
  }

  @Test
  public void testIsWarPackaging_warProjectAndNoOverride() {
    Mockito.when(rawConfiguration.getPackagingOverride()).thenReturn(Optional.empty());
    applyGradleWarPlugin();

    Assert.assertTrue(TaskCommon.isWarContainerization(project, rawConfiguration));
  }

  @Test
  public void testIsWarPackaging_warOverride() {
    Mockito.when(rawConfiguration.getPackagingOverride()).thenReturn(Optional.of("war"));

    Assert.assertTrue(TaskCommon.isWarContainerization(project, rawConfiguration));
  }

  @Test
  public void testIsWarPackaging_javaOverride() {
    Mockito.when(rawConfiguration.getPackagingOverride()).thenReturn(Optional.of("java"));
    applyGradleWarPlugin();

    Assert.assertFalse(TaskCommon.isWarContainerization(project, rawConfiguration));
  }

  private void applyGradleWarPlugin() {
    Mockito.when(taskContainer.findByName("war")).thenReturn(war);
  }
}
