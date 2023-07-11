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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import com.google.cloud.tools.jib.plugins.common.JibBuildRunner;
import com.google.cloud.tools.jib.plugins.common.PluginConfigurationProcessor;
import org.gradle.api.Project;
import org.gradle.api.provider.Property;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** Tests for {@link TaskCommon}. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BuildTarTaskTest {

  @Mock JibExtension mockJibExtension;

  @Mock GradleProjectProperties mockProjectProperties;
  @Mock private TargetImageParameters mockTargetImage;
  @Mock private JibBuildRunner mockJibBuildRunner;

  @Test
  void testBuildTarTask() {
    Project project = ProjectBuilder.builder().build();
    BuildTarTask task = project.getTasks().register("test", BuildTarTask.class).get();
    assertNull(task.getJib());
    assertNull(task.getProjectProperties());

    Property<String> property = project.getObjects().property(String.class);
    property.set("test");

    try (MockedStatic<GradleProjectProperties> gpp =
        Mockito.mockStatic(GradleProjectProperties.class)) {
      gpp.when(() -> GradleProjectProperties.getForProject(any(), any(), any(), anyString()))
          .thenReturn(mockProjectProperties);
      when(mockJibExtension.getConfigurationName()).thenReturn(property);
      when(mockTargetImage.getImage()).thenReturn("test");
      when(mockJibExtension.getTo()).thenReturn(mockTargetImage);
      task.setJibExtension(mockJibExtension);
      assertNotNull(task.getJib());
      assertNotNull(task.getProjectProperties());

      try (MockedStatic<PluginConfigurationProcessor> pcp =
          Mockito.mockStatic(PluginConfigurationProcessor.class)) {
        pcp.when(
                () ->
                    PluginConfigurationProcessor.createJibBuildRunnerForTarImage(
                        any(), any(), any(), any(), any()))
            .thenReturn(mockJibBuildRunner);

        try {
          task.buildTar();
          pcp.verify(
              () ->
                  PluginConfigurationProcessor.createJibBuildRunnerForTarImage(
                      any(), any(), any(), any(), any()),
              times(1));
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
    }
  }
}
