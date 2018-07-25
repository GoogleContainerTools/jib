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

import com.google.cloud.tools.jib.filesystem.DirectoryWalker;
import com.google.cloud.tools.jib.image.LayerEntry;
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
import java.util.stream.Stream;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests for {@link DockerContextGenerator}. */
@RunWith(MockitoJUnitRunner.class)
public class DockerContextGeneratorTest {

  private static final String EXPECTED_DEPENDENCIES_PATH = "/app/libs/";
  private static final String EXPECTED_RESOURCES_PATH = "/app/resources/";
  private static final String EXPECTED_CLASSES_PATH = "/app/classes/";

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

  private static ImmutableList<Path> listFilesInDirectory(Path directory) throws IOException {
    try (Stream<Path> files = Files.list(directory)) {
      return files.collect(ImmutableList.toImmutableList());
    }
  }

  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void testGenerate() throws IOException, URISyntaxException {
    Path testDependencies = Paths.get(Resources.getResource("application/dependencies").toURI());
    Path testSnapshotDependencies =
        Paths.get(Resources.getResource("application/snapshot-dependencies").toURI());
    Path testResources = Paths.get(Resources.getResource("application/resources").toURI());
    Path testClasses = Paths.get(Resources.getResource("application/classes").toURI());
    Path testExtraFiles = Paths.get(Resources.getResource("layer").toURI());

    ImmutableList<Path> expectedDependenciesFiles = listFilesInDirectory(testDependencies);
    ImmutableList<Path> expectedSnapshotDependenciesFiles =
        listFilesInDirectory(testSnapshotDependencies);
    ImmutableList<Path> expectedResourcesFiles = listFilesInDirectory(testResources);
    ImmutableList<Path> expectedClassesFiles = listFilesInDirectory(testClasses);
    ImmutableList<Path> expectedExtraFiles = listFilesInDirectory(testExtraFiles);

    Path targetDirectory = temporaryFolder.newFolder().toPath();

    /*
     * Deletes the directory so that DockerContextGenerator#generate does not throw
     * InsecureRecursiveDeleteException.
     */
    Files.delete(targetDirectory);

    new DockerContextGenerator(
            new LayerEntry(expectedDependenciesFiles, EXPECTED_DEPENDENCIES_PATH),
            new LayerEntry(expectedSnapshotDependenciesFiles, EXPECTED_DEPENDENCIES_PATH),
            new LayerEntry(expectedResourcesFiles, EXPECTED_RESOURCES_PATH),
            new LayerEntry(expectedClassesFiles, EXPECTED_CLASSES_PATH),
            new LayerEntry(expectedExtraFiles, "/"))
        .setBaseImage("somebaseimage")
        .generate(targetDirectory);

    Assert.assertTrue(Files.exists(targetDirectory.resolve("Dockerfile")));
    assertSameFiles(targetDirectory.resolve("libs"), testDependencies);
    assertSameFiles(targetDirectory.resolve("snapshot-libs"), testSnapshotDependencies);
    assertSameFiles(targetDirectory.resolve("resources"), testResources);
    assertSameFiles(targetDirectory.resolve("classes"), testClasses);
    assertSameFiles(targetDirectory.resolve("root"), testExtraFiles);
  }

  @Test
  public void testMakeDockerfile() throws IOException {
    String expectedBaseImage = "somebaseimage";
    List<String> expectedJvmFlags = Arrays.asList("-flag", "another\"Flag");
    String expectedMainClass = "SomeMainClass";
    List<String> expectedJavaArguments = Arrays.asList("arg1", "arg2");
    List<String> exposedPorts = Arrays.asList("1000/tcp", "2000-2010/udp");

    String dockerfile =
        new DockerContextGenerator(
                new LayerEntry(ImmutableList.of(Paths.get("ignored")), EXPECTED_DEPENDENCIES_PATH),
                new LayerEntry(ImmutableList.of(Paths.get("ignored")), EXPECTED_DEPENDENCIES_PATH),
                new LayerEntry(ImmutableList.of(Paths.get("ignored")), EXPECTED_RESOURCES_PATH),
                new LayerEntry(ImmutableList.of(Paths.get("ignored")), EXPECTED_CLASSES_PATH),
                new LayerEntry(ImmutableList.of(Paths.get("ignored")), "/"))
            .setBaseImage(expectedBaseImage)
            .setJvmFlags(expectedJvmFlags)
            .setMainClass(expectedMainClass)
            .setJavaArguments(expectedJavaArguments)
            .setExposedPorts(exposedPorts)
            .makeDockerfile();

    // Need to split/rejoin the string here to avoid cross-platform troubles
    List<String> sampleDockerfile =
        Resources.readLines(Resources.getResource("sampleDockerfile"), StandardCharsets.UTF_8);
    Assert.assertEquals(String.join("\n", sampleDockerfile), dockerfile);
  }
}
