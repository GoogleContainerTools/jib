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

package com.google.cloud.tools.jib.maven;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.maven.model.Build;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests for {@link DockerContextMojo}. */
@RunWith(MockitoJUnitRunner.class)
public class DockerContextMojoTest {

  @Rule public final TemporaryFolder projectRoot = new TemporaryFolder();

  private DockerContextMojo mojo;
  private String appRoot = "/app";

  @Before
  public void setUp() throws IOException {
    File outputFolder = projectRoot.newFolder("target");

    MavenProject project = Mockito.mock(MavenProject.class);
    Build build = Mockito.mock(Build.class);
    Mockito.when(project.getBuild()).thenReturn(build);
    Mockito.when(build.getSourceDirectory()).thenReturn(projectRoot.newFolder("src").toString());
    Mockito.when(build.getOutputDirectory()).thenReturn(outputFolder.toString());

    mojo =
        new DockerContextMojo() {
          @Override
          MavenProject getProject() {
            return project;
          }

          @Override
          Path getExtraDirectory() {
            return projectRoot.getRoot().toPath();
          }

          @Override
          String getMainClass() {
            return "MainClass";
          }

          @Override
          String getAppRoot() {
            return appRoot;
          }
        };
    mojo.targetDir = outputFolder.toString();
  }

  @Test
  public void testEntrypoint() throws MojoExecutionException, IOException {
    mojo.execute();

    Assert.assertEquals(
        "ENTRYPOINT [\"java\",\"-cp\",\"/app/resources:/app/classes:/app/libs/*\",\"MainClass\"]",
        getEntrypoint());
  }

  @Test
  public void testEntrypoint_nonDefaultAppRoot() throws MojoExecutionException, IOException {
    appRoot = "/";
    mojo.execute();

    Assert.assertEquals(
        "ENTRYPOINT [\"java\",\"-cp\",\"/resources:/classes:/libs/*\",\"MainClass\"]",
        getEntrypoint());
  }

  @Test
  public void testGenerateDockerContext_errorOnNonAbsoluteAppRoot() {
    appRoot = "relative/path";
    try {
      mojo.execute();
      Assert.fail();
    } catch (MojoExecutionException ex) {
      Assert.assertEquals(
          "<container><appRoot> is not an absolute Unix-style path: relative/path",
          ex.getMessage());
    }
  }

  @Test
  public void testGenerateDockerContext_errorOnWindowsAppRoot() {
    appRoot = "\\windows\\path";
    try {
      mojo.execute();
      Assert.fail();
    } catch (MojoExecutionException ex) {
      Assert.assertEquals(
          "<container><appRoot> is not an absolute Unix-style path: \\windows\\path",
          ex.getMessage());
    }
  }

  @Test
  public void testGenerateDockerContext_errorOnWindowsAppRootWithDriveLetter() {
    appRoot = "C:\\windows\\path";
    try {
      mojo.execute();
      Assert.fail();
    } catch (MojoExecutionException ex) {
      Assert.assertEquals(
          "<container><appRoot> is not an absolute Unix-style path: C:\\windows\\path",
          ex.getMessage());
    }
  }

  private String getEntrypoint() throws IOException {
    Path dockerfile = projectRoot.getRoot().toPath().resolve("target/Dockerfile");
    List<String> lines = Files.readAllLines(dockerfile);
    return lines.stream().filter(line -> line.startsWith("ENTRYPOINT")).findFirst().get();
  }
}
