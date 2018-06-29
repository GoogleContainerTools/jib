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
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class BuildDockerTaskTest {

  @Mock private JibExtension mockJibExtension;
  @Mock private GradleBuildLogger mockBuildLogger;

  private BuildDockerTask task;

  @Before
  public void setup() {
    Project tempProject = ProjectBuilder.builder().withName("project-name").build();
    tempProject.setVersion("project-version");
    task = tempProject.getTasks().create("tempDockerTask", BuildDockerTask.class);
    task.setJibExtension(mockJibExtension);
  }

  @Test
  public void testGetDockerTag_configured() throws InvalidImageReferenceException {
    Mockito.when(mockJibExtension.getTargetImage()).thenReturn("a/b:c");
    ImageReference result = task.getDockerTag(mockBuildLogger);
    Assert.assertEquals("a/b", result.getRepository());
    Assert.assertEquals("c", result.getTag());
    Mockito.verify(mockBuildLogger, Mockito.never()).lifecycle(Mockito.any());
  }

  @Test
  public void testGetDockerTag_notConfigured() throws InvalidImageReferenceException {
    Mockito.when(mockJibExtension.getTargetImage()).thenReturn(null);
    ImageReference result = task.getDockerTag(mockBuildLogger);
    Assert.assertEquals("project-name", result.getRepository());
    Assert.assertEquals("project-version", result.getTag());
    Mockito.verify(mockBuildLogger)
        .lifecycle(
            "Tagging image with generated image reference project-name:project-version. If you'd "
                + "like to specify a different tag, you can set the jib.to.image parameter in your "
                + "build.gradle, or use the --image=<MY IMAGE> commandline flag.");
  }
}
