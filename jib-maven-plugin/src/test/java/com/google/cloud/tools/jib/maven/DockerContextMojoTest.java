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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import org.apache.maven.model.Build;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests for {@link DockerContextMojo}. */
@RunWith(MockitoJUnitRunner.class)
public class DockerContextMojoTest {

  @Rule public final TemporaryFolder projectRoot = new TemporaryFolder();

  private DockerContextMojo mojo;
  private String appRoot = "/app";
  private File outputFolder;
  private @Mock MavenProject project;
  private @Mock Build build;

  @Before
  public void setUp() throws IOException {
    outputFolder = projectRoot.newFolder("target");
    Mockito.when(project.getBuild()).thenReturn(build);
    Mockito.when(project.getBasedir()).thenReturn(projectRoot.getRoot());
    Mockito.when(build.getOutputDirectory()).thenReturn(outputFolder.toString());

    mojo = new BaseDockerContextMojo();
    mojo.targetDir = outputFolder.toString();
  }

  @Test
  public void testEntrypoint() throws MojoExecutionException, IOException {
    mojo.execute();

    Assert.assertEquals(
        "ENTRYPOINT [\"java\",\"-cp\",\"/app/resources:/app/classes:/app/libs/*\",\"MainClass\"]",
        getDockerfileLine("ENTRYPOINT"));
    Assert.assertNull(getDockerfileLine("CMD"));
  }

  @Test
  public void testEntrypoint_nonDefaultAppRoot() throws MojoExecutionException, IOException {
    appRoot = "/";
    mojo.execute();

    Assert.assertEquals(
        "ENTRYPOINT [\"java\",\"-cp\",\"/resources:/classes:/libs/*\",\"MainClass\"]",
        getDockerfileLine("ENTRYPOINT"));
    Assert.assertNull(getDockerfileLine("CMD"));
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

  @Test
  public void testGeneratedDockerContext_env() throws MojoExecutionException, IOException {
    mojo.execute();
    Assert.assertEquals("ENV envKey=\"envVal\"", getDockerfileLine("ENV"));
  }

  public void testBaseImage_nonWarPackaging() throws MojoExecutionException, IOException {
    mojo.execute();

    Assert.assertEquals("FROM gcr.io/distroless/java", getDockerfileLine("FROM"));
  }

  @Test
  public void testBaseImage_warPackaging() throws MojoExecutionException, IOException {
    Mockito.doReturn("war").when(project).getPackaging();
    Mockito.doReturn("final-name").when(build).getFinalName();
    projectRoot.newFolder("final-name", "WEB-INF", "lib");
    projectRoot.newFolder("final-name", "WEB-INF", "classes");
    Mockito.doReturn(projectRoot.getRoot().toString()).when(build).getDirectory();
    mojo.execute();

    Assert.assertEquals("FROM gcr.io/distroless/java/jetty", getDockerfileLine("FROM"));
  }

  @Test
  public void testBaseImage_nonDefault() throws MojoExecutionException, IOException {
    Mockito.doReturn("war").when(project).getPackaging();
    Mockito.doReturn("final-name").when(build).getFinalName();
    mojo =
        new BaseDockerContextMojo() {
          @Override
          String getBaseImage() {
            return "tomcat:8.5-jre8-alpine";
          }
        };
    mojo.targetDir = outputFolder.toString();

    projectRoot.newFolder("final-name", "WEB-INF", "lib");
    projectRoot.newFolder("final-name", "WEB-INF", "classes");
    Mockito.doReturn(projectRoot.getRoot().toString()).when(build).getDirectory();
    mojo.execute();

    Assert.assertEquals("FROM tomcat:8.5-jre8-alpine", getDockerfileLine("FROM"));
  }

  @Test
  public void testEntrypoint_defaultWarPackaging() throws MojoExecutionException, IOException {
    Mockito.doReturn("war").when(project).getPackaging();
    Mockito.doReturn("final-name").when(build).getFinalName();
    projectRoot.newFolder("final-name", "WEB-INF", "lib");
    projectRoot.newFolder("final-name", "WEB-INF", "classes");
    Mockito.doReturn(projectRoot.getRoot().toString()).when(build).getDirectory();
    mojo.execute();

    Assert.assertNull(getDockerfileLine("ENTRYPOINT"));
    Assert.assertNull(getDockerfileLine("CMD"));
  }

  @Test
  public void testEntrypoint_warPackaging() throws MojoExecutionException, IOException {
    Mockito.doReturn("war").when(project).getPackaging();
    Mockito.doReturn("final-name").when(build).getFinalName();
    projectRoot.newFolder("final-name", "WEB-INF", "lib");
    projectRoot.newFolder("final-name", "WEB-INF", "classes");
    Mockito.doReturn(projectRoot.getRoot().toString()).when(build).getDirectory();
    mojo =
        new BaseDockerContextMojo() {
          @Override
          List<String> getEntrypoint() {
            return ImmutableList.of("catalina.sh", "run");
          }
        };
    mojo.targetDir = outputFolder.toString();
    mojo.execute();
    Assert.assertEquals("ENTRYPOINT [\"catalina.sh\",\"run\"]", getDockerfileLine("ENTRYPOINT"));
    Assert.assertNull(getDockerfileLine("CMD"));
  }

  @Test
  public void testUser() throws IOException, MojoExecutionException {
    mojo =
        new BaseDockerContextMojo() {
          @Override
          String getUser() {
            return "tomcat";
          }
        };
    mojo.targetDir = outputFolder.toString();
    mojo.execute();
    Assert.assertEquals("USER tomcat", getDockerfileLine("USER"));
  }

  @Test
  public void testUser_null() throws IOException, MojoExecutionException {
    mojo = new BaseDockerContextMojo();
    mojo.targetDir = outputFolder.toString();
    mojo.execute();
    Assert.assertNull(getDockerfileLine("USER"));
  }

  private class BaseDockerContextMojo extends DockerContextMojo {

    @Override
    MavenProject getProject() {
      return project;
    }

    @Override
    Optional<Path> getExtraDirectoryPath() {
      return Optional.of(projectRoot.getRoot().toPath());
    }

    @Override
    String getMainClass() {
      return "MainClass";
    }

    @Override
    String getAppRoot() {
      return appRoot;
    }

    @Override
    Map<String, String> getEnvironment() {
      return ImmutableMap.of("envKey", "envVal");
    }
  }

  @Nullable
  private String getDockerfileLine(String command) throws IOException {
    Path dockerfile = projectRoot.getRoot().toPath().resolve("target/Dockerfile");
    List<String> lines = Files.readAllLines(dockerfile);
    return lines.stream().filter(line -> line.startsWith(command)).findFirst().orElse(null);
  }
}
