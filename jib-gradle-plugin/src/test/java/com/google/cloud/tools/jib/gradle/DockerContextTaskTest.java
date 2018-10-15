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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.NoSuchElementException;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/** Test for {@link DockerContextTask}. */
@RunWith(MockitoJUnitRunner.class)
public class DockerContextTaskTest {

  @Rule public final TemporaryFolder projectRoot = new TemporaryFolder();

  @Mock private ContainerParameters containerParameters;
  @Mock private BaseImageParameters baseImageParameters;

  private DockerContextTask task;
  private Project project;

  @Before
  public void setUp() throws IOException {
    projectRoot.newFolder("build", "jib-exploded-war", "WEB-INF", "lib");
    projectRoot.newFolder("build", "jib-exploded-war", "WEB-INF", "classes");
    projectRoot.newFolder("build", "jib-docker-context");

    JibExtension jibExtension = Mockito.mock(JibExtension.class);
    Mockito.when(jibExtension.getContainer()).thenReturn(containerParameters);
    Mockito.when(jibExtension.getExtraDirectoryPath()).thenReturn(projectRoot.getRoot().toPath());
    Mockito.when(jibExtension.getContainer().getMainClass()).thenReturn("MainClass");
    Mockito.when(jibExtension.getFrom()).thenReturn(baseImageParameters);
    Mockito.when(baseImageParameters.getImage()).thenReturn("base image");
    Mockito.when(containerParameters.getAppRoot()).thenReturn("/app");
    Mockito.when(containerParameters.getArgs()).thenCallRealMethod();

    project = ProjectBuilder.builder().withProjectDir(projectRoot.getRoot()).build();
    project.getPluginManager().apply("java");

    task = project.getTasks().create("jibExportDockerContext", DockerContextTask.class);
    task.setJibExtension(jibExtension);
  }

  @Test
  public void testEntrypoint() throws IOException {
    task.generateDockerContext();

    Assert.assertEquals(
        "ENTRYPOINT [\"java\",\"-cp\",\"/app/resources:/app/classes:/app/libs/*\",\"MainClass\"]",
        getEntrypoint());
  }

  @Test
  public void testEntrypoint_nonDefaultAppRoot() throws IOException {
    Mockito.when(containerParameters.getAppRoot()).thenReturn("/");
    task.generateDockerContext();

    Assert.assertEquals(
        "ENTRYPOINT [\"java\",\"-cp\",\"/resources:/classes:/libs/*\",\"MainClass\"]",
        getEntrypoint());
    try {
      getCmd();
      Assert.fail();
    } catch (NoSuchElementException ex) {
      // pass
    }
  }

  @Test
  public void testEntrypoint_inheritedEntrypoint() throws IOException {
    Mockito.when(containerParameters.getAppRoot()).thenReturn("/");
    Mockito.when(containerParameters.getArgs()).thenCallRealMethod();
    project.getPluginManager().apply("war");

    task.generateDockerContext();

    try {
      getEntrypoint();
      Assert.fail();
    } catch (NoSuchElementException ex) {
      // pass
    }
    try {
      getCmd();
      Assert.fail();
    } catch (NoSuchElementException ex) {
      // pass
    }
  }

  @Test
  public void testUser() throws IOException {
    Mockito.when(containerParameters.getUser()).thenReturn("tomcat");
    task.generateDockerContext();

    Assert.assertEquals("USER tomcat", getUser());
  }

  @Test
  public void testUser_null() throws IOException {
    Mockito.when(containerParameters.getUser()).thenReturn(null);
    task.generateDockerContext();
    try {
      getUser();
      Assert.fail();
    } catch (NoSuchElementException ex) {
      // pass
    }
  }

  @Test
  public void testGenerateDockerContext_errorOnNonAbsoluteAppRoot() {
    Mockito.when(containerParameters.getAppRoot()).thenReturn("relative/path");

    try {
      task.generateDockerContext();
      Assert.fail();
    } catch (GradleException ex) {
      Assert.assertEquals(
          "container.appRoot is not an absolute Unix-style path: relative/path", ex.getMessage());
    }
  }

  @Test
  public void testGenerateDockerContext_errorOnWindowsAppRoot() {
    Mockito.when(containerParameters.getAppRoot()).thenReturn("\\windows\\path");

    try {
      task.generateDockerContext();
      Assert.fail();
    } catch (GradleException ex) {
      Assert.assertEquals(
          "container.appRoot is not an absolute Unix-style path: \\windows\\path", ex.getMessage());
    }
  }

  @Test
  public void testGenerateDockerContext_errorOnWindowsAppRootWithDriveLetter() {
    Mockito.when(containerParameters.getAppRoot()).thenReturn("C:\\windows\\path");

    try {
      task.generateDockerContext();
      Assert.fail();
    } catch (GradleException ex) {
      Assert.assertEquals(
          "container.appRoot is not an absolute Unix-style path: C:\\windows\\path",
          ex.getMessage());
    }
  }

  private String getUser() throws IOException {
    return getDockerfileLine("USER");
  }

  private String getEntrypoint() throws IOException {
    return getDockerfileLine("ENTRYPOINT");
  }

  private String getCmd() throws IOException {
    return getDockerfileLine("CMD");
  }

  private String getDockerfileLine(String command) throws IOException {
    Path dockerfile = projectRoot.getRoot().toPath().resolve("build/jib-docker-context/Dockerfile");
    List<String> lines = Files.readAllLines(dockerfile);
    return lines.stream().filter(line -> line.startsWith(command)).findFirst().get();
  }
}
