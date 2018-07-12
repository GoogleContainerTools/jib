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

package com.google.cloud.tools.jib.docker;

import com.google.cloud.tools.jib.builder.SourceFilesConfiguration;
import com.google.cloud.tools.jib.filesystem.DirectoryWalker;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
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

/** Tests for {@link DockerContextGenerator}. */
@RunWith(MockitoJUnitRunner.class)
public class DockerContextGeneratorTest {

  private static void assertSameFiles(Path directory1, Path directory2) throws IOException {
    Deque<Path> directory1Paths = new ArrayDeque<>(new DirectoryWalker(directory1).walk());

    new DirectoryWalker(directory2)
        .walk(
            directory2Path -> {
              Assert.assertEquals(
                  directory1.relativize(directory1Paths.pop()),
                  directory2.relativize(directory2Path));
            });

    Assert.assertEquals(0, directory1Paths.size());
  }

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Mock private SourceFilesConfiguration mockSourceFilesConfiguration;

  @Before
  public void setUpMocks() {
    String expectedDependenciesPath = "/app/libs/";
    String expectedSnapshotDependenciesPath = "/app/snapshot-libs/";
    String expectedResourcesPath = "/app/resources/";
    String expectedClassesPath = "/app/classes/";

    Mockito.when(mockSourceFilesConfiguration.getDependenciesPathOnImage())
        .thenReturn(expectedDependenciesPath);
    Mockito.when(mockSourceFilesConfiguration.getSnapshotDependenciesPathOnImage())
        .thenReturn(expectedSnapshotDependenciesPath);
    Mockito.when(mockSourceFilesConfiguration.getResourcesPathOnImage())
        .thenReturn(expectedResourcesPath);
    Mockito.when(mockSourceFilesConfiguration.getClassesPathOnImage())
        .thenReturn(expectedClassesPath);
  }

  @Test
  public void testGenerate() throws IOException, URISyntaxException {
    Path testDependencies = Paths.get(Resources.getResource("application/dependencies").toURI());
    Path testSnapshotDependencies =
        Paths.get(Resources.getResource("application/snapshot-dependencies").toURI());
    Path testResources = Paths.get(Resources.getResource("application/resources").toURI());
    Path testClasses = Paths.get(Resources.getResource("application/classes").toURI());

    ImmutableList<Path> expectedDependenciesFiles =
        new DirectoryWalker(testDependencies).filterRoot().walk();
    ImmutableList<Path> expectedSnapshotDependenciesFiles =
        new DirectoryWalker(testSnapshotDependencies).filterRoot().walk();
    ImmutableList<Path> expectedResourcesFiles =
        new DirectoryWalker(testResources).filterRoot().walk();
    ImmutableList<Path> expectedClassesFiles = new DirectoryWalker(testClasses).filterRoot().walk();
    Mockito.when(mockSourceFilesConfiguration.getDependenciesFiles())
        .thenReturn(expectedDependenciesFiles);
    Mockito.when(mockSourceFilesConfiguration.getSnapshotDependenciesFiles())
        .thenReturn(expectedSnapshotDependenciesFiles);
    Mockito.when(mockSourceFilesConfiguration.getResourcesFiles())
        .thenReturn(expectedResourcesFiles);
    Mockito.when(mockSourceFilesConfiguration.getClassesFiles()).thenReturn(expectedClassesFiles);

    Path targetDirectory = temporaryFolder.newFolder().toPath();

    /*
     * Deletes the directory so that DockerContextGenerator#generate does not throw
     * InsecureRecursiveDeleteException.
     */
    Files.delete(targetDirectory);

    new DockerContextGenerator(mockSourceFilesConfiguration)
        .setBaseImage("somebaseimage")
        .generate(targetDirectory);

    Assert.assertTrue(Files.exists(targetDirectory.resolve("Dockerfile")));
    assertSameFiles(targetDirectory.resolve("libs"), testDependencies);
    assertSameFiles(targetDirectory.resolve("snapshot-libs"), testSnapshotDependencies);
    assertSameFiles(targetDirectory.resolve("resources"), testResources);
    assertSameFiles(targetDirectory.resolve("classes"), testClasses);
  }

  @Test
  public void testMakeDockerfile() throws IOException {
    String expectedBaseImage = "somebaseimage";
    List<String> expectedJvmFlags = Arrays.asList("-flag", "another\"Flag");
    String expectedMainClass = "SomeMainClass";
    List<String> expectedJavaArguments = Arrays.asList("arg1", "arg2");
    List<String> exposedPorts = Arrays.asList("1000/tcp", "2000-2010/udp");

    String dockerfile =
        new DockerContextGenerator(mockSourceFilesConfiguration)
            .setBaseImage(expectedBaseImage)
            .setJvmFlags(expectedJvmFlags)
            .setMainClass(expectedMainClass)
            .setJavaArguments(expectedJavaArguments)
            .setExposedPorts(exposedPorts)
            .makeDockerfile();

    // Need to split/rejoin the string here to avoid cross-platform troubles
    List<String> sampleDockerfile =
        Resources.readLines(Resources.getResource("sampleDockerfile"), StandardCharsets.UTF_8);
    Assert.assertArrayEquals(
        String.join("\n", sampleDockerfile).getBytes(StandardCharsets.UTF_8),
        dockerfile.getBytes(StandardCharsets.UTF_8));
  }
}
