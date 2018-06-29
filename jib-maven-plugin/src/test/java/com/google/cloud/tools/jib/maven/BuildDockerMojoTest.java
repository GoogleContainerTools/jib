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

package com.google.cloud.tools.jib.maven;

import com.google.cloud.tools.jib.image.ImageReference;
import org.apache.maven.project.MavenProject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class BuildDockerMojoTest {

  @Mock private MavenProject mavenProject;
  @Mock private MavenBuildLogger mockBuildLogger;

  @InjectMocks private BuildDockerMojo buildDockerMojo;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    Mockito.when(mavenProject.getName()).thenReturn("project-name");
    Mockito.when(mavenProject.getVersion()).thenReturn("project-version");
    buildDockerMojo.setProject(mavenProject);
  }

  @Test
  public void testGetDockerTag_configured() {
    buildDockerMojo.setTargetImage("a/b:c");
    ImageReference result = buildDockerMojo.getDockerTag(mockBuildLogger);
    Assert.assertEquals("a/b", result.getRepository());
    Assert.assertEquals("c", result.getTag());
    Mockito.verify(mockBuildLogger, Mockito.never()).lifecycle(Mockito.any());
  }

  @Test
  public void testGetDockerTag_notConfigured() {
    buildDockerMojo.setTargetImage(null);
    ImageReference result = buildDockerMojo.getDockerTag(mockBuildLogger);
    Assert.assertEquals("project-name", result.getRepository());
    Assert.assertEquals("project-version", result.getTag());
    Mockito.verify(mockBuildLogger)
        .lifecycle(
            "Tagging image with generated image reference project-name:project-version. If you'd "
                + "like to specify a different tag, you can set the <to><image> parameter in your "
                + "pom.xml, or use the -Dimage=<MY IMAGE> commandline flag.");
  }
}
