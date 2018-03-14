/*
 * Copyright 2018 Google Inc.
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

import com.google.cloud.tools.jib.builder.SourceFilesConfiguration;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
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

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Mock private SourceFilesConfiguration mockSourceFilesConfiguration;

  @Before
  public void setUpMocks() {
    String expectedDependenciesPath = "/app/libs/";
    String expectedResourcesPath = "/app/resources/";
    String expectedClassesPath = "/app/classes/";

    Mockito.when(mockSourceFilesConfiguration.getDependenciesPathOnImage())
        .thenReturn(expectedDependenciesPath);
    Mockito.when(mockSourceFilesConfiguration.getResourcesPathOnImage())
        .thenReturn(expectedResourcesPath);
    Mockito.when(mockSourceFilesConfiguration.getClassesPathOnImage())
        .thenReturn(expectedClassesPath);
  }

  @Test
  public void testGetEntrypoint() {
    List<String> expectedJvmFlags = Arrays.asList("-flag", "another\"Flag");
    String expectedMainClass = "SomeMainClass";

    DockerContextMojo dockerContextMojo =
        new DockerContextMojo().setJvmFlags(expectedJvmFlags).setMainClass(expectedMainClass);

    Assert.assertEquals(
        "[\"java\",\"-flag\",\"another\\\"Flag\",\"-cp\",\"/app/libs/*:/app/resources/:/app/classes/\",\"SomeMainClass\"]",
        dockerContextMojo.getEntrypoint(mockSourceFilesConfiguration));
  }

  @Test
  public void testCopyFiles() throws IOException, URISyntaxException {
    Path destDir = temporaryFolder.newFolder().toPath();
    Path libraryA =
        Paths.get(Resources.getResource("application/dependencies/libraryA.jar").toURI());
    Path libraryB =
        Paths.get(Resources.getResource("application/dependencies/libraryB.jar").toURI());
    Path dirLayer = Paths.get(Resources.getResource("layer").toURI());

    DockerContextMojo.copyFiles(Arrays.asList(libraryA, libraryB, dirLayer), destDir);

    assertFilesEqual(libraryA, destDir.resolve("libraryA.jar"));
    assertFilesEqual(libraryB, destDir.resolve("libraryB.jar"));
    Assert.assertTrue(Files.exists(destDir.resolve("layer").resolve("a").resolve("b")));
    Assert.assertTrue(Files.exists(destDir.resolve("layer").resolve("c")));
    assertFilesEqual(
        dirLayer.resolve("a").resolve("b").resolve("bar"),
        destDir.resolve("layer").resolve("a").resolve("b").resolve("bar"));
    assertFilesEqual(
        dirLayer.resolve("c").resolve("cat"), destDir.resolve("layer").resolve("c").resolve("cat"));
    assertFilesEqual(dirLayer.resolve("foo"), destDir.resolve("layer").resolve("foo"));
  }

  @Test
  public void testMakeDockerfile() throws IOException, URISyntaxException {
    Path testTargetDir = temporaryFolder.newFolder().toPath();

    String expectedBaseImage = "somebaseimage";
    List<String> expectedJvmFlags = Arrays.asList("-flag", "another\"Flag");
    String expectedMainClass = "SomeMainClass";

    String dockerfile =
        new DockerContextMojo()
            .setTargetDir(testTargetDir.toString())
            .setFrom(expectedBaseImage)
            .setJvmFlags(expectedJvmFlags)
            .setMainClass(expectedMainClass)
            .makeDockerfile(mockSourceFilesConfiguration);

    Path sampleDockerfile = Paths.get(Resources.getResource("sampleDockerfile").toURI());
    Assert.assertArrayEquals(
        Files.readAllBytes(sampleDockerfile), dockerfile.getBytes(StandardCharsets.UTF_8));
  }

  private void assertFilesEqual(Path file1, Path file2) throws IOException {
    Assert.assertArrayEquals(Files.readAllBytes(file1), Files.readAllBytes(file2));
  }
}
