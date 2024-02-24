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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import com.google.cloud.tools.jib.docker.CliDockerClient;
import com.google.cloud.tools.jib.plugins.common.JibBuildRunner;
import com.google.cloud.tools.jib.plugins.common.PluginConfigurationProcessor;
import org.gradle.api.Project;
import org.gradle.api.provider.Property;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests for {@link TaskCommon}. */
@RunWith(MockitoJUnitRunner.class)
public class BuildDockerTaskTest {

  @Mock JibExtension mockJibExtension;

  @Mock GradleProjectProperties mockProjectProperties;
  @Mock private TargetImageParameters mockTargetImage;
  @Mock private JibBuildRunner mockJibBuildRunner;
  @Mock private DockerClientParameters mockDockerClientParams;

  @Test
  public void testBuildDockerTask() {
    Project project = ProjectBuilder.builder().build();
    BuildDockerTask task = project.getTasks().register("test", BuildDockerTask.class).get();
    assertNull(task.getJib());
    assertNull(task.getProjectProperties());

    Property<String> property = project.getObjects().property(String.class);
    property.set("test");

    try (MockedStatic<GradleProjectProperties> gpp =
        Mockito.mockStatic(GradleProjectProperties.class)) {
      gpp.when(() -> GradleProjectProperties.getForProject(any(), any(), any(), anyString()))
          .thenReturn(mockProjectProperties);
      when(mockJibExtension.getConfigurationName()).thenReturn(property);
      //      when(mockTargetImage.getImage()).thenReturn("test");
      when(mockJibExtension.getDockerClient()).thenReturn(mockDockerClientParams);
      when(mockDockerClientParams.getExecutablePath()).thenReturn(null);

      try (MockedStatic<CliDockerClient> cdc = Mockito.mockStatic(CliDockerClient.class)) {
        cdc.when(() -> CliDockerClient.isDefaultDockerInstalled()).thenReturn(true);

        task.setJibExtension(mockJibExtension);
        assertNotNull(task.getJib());
        assertNotNull(task.getProjectProperties());

        try (MockedStatic<PluginConfigurationProcessor> pcp =
            Mockito.mockStatic(PluginConfigurationProcessor.class)) {
          pcp.when(
                  () ->
                      PluginConfigurationProcessor.createJibBuildRunnerForDockerDaemonImage(
                          any(), any(), any(), any(), any()))
              .thenReturn(mockJibBuildRunner);

          try {
            task.buildDocker();
            pcp.verify(
                () ->
                    PluginConfigurationProcessor.createJibBuildRunnerForDockerDaemonImage(
                        any(), any(), any(), any(), any()),
                times(1));
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        }
      }
    }
  }
}
