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

package com.google.cloud.tools.jib.frontend;

import com.google.cloud.tools.jib.filesystem.AbsoluteUnixPath;
import com.google.cloud.tools.jib.filesystem.DirectoryWalker;
import com.google.cloud.tools.jib.image.LayerEntry;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests for {@link JavaDockerContextGenerator}. */
@RunWith(MockitoJUnitRunner.class)
public class JavaDockerContextGeneratorTest {

  private static void assertSameFiles(Path directory1, Path directory2) throws IOException {
    ImmutableList<Path> directory1Files =
        new DirectoryWalker(directory1)
            .walk()
            .stream()
            .map(directory1::relativize)
            .collect(ImmutableList.toImmutableList());
    ImmutableList<Path> directory2Files =
        new DirectoryWalker(directory2)
            .walk()
            .stream()
            .map(directory2::relativize)
            .collect(ImmutableList.toImmutableList());
    Assert.assertEquals(directory1Files, directory2Files);
  }

  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Mock private JavaLayerConfigurations mockJavaLayerConfigurations;

  private ImmutableList<LayerEntry> filesToLayerEntries(Path directory, String extractionRootString)
      throws IOException {
    AbsoluteUnixPath extractionRoot = AbsoluteUnixPath.get(extractionRootString);
    return new DirectoryWalker(directory)
        .walk()
        .stream()
        .map(
            sourceFile ->
                new LayerEntry(
                    sourceFile, extractionRoot.resolve(directory.relativize(sourceFile))))
        .collect(ImmutableList.toImmutableList());
  }

  @Test
  public void testGenerate() throws IOException, URISyntaxException {
    Path testDependencies = Paths.get(Resources.getResource("application/dependencies").toURI());
    Path testSnapshotDependencies =
        Paths.get(Resources.getResource("application/snapshot-dependencies").toURI());
    Path testResources = Paths.get(Resources.getResource("application/resources").toURI());
    Path testClasses = Paths.get(Resources.getResource("application/classes").toURI());
    Path testExplodedWarFiles = Paths.get(Resources.getResource("exploded-war").toURI());
    Path testExtraFiles = Paths.get(Resources.getResource("layer").toURI());

    Path targetDirectory = temporaryFolder.newFolder().toPath();

    /*
     * Deletes the directory so that JavaDockerContextGenerator#generate does not throw
     * InsecureRecursiveDeleteException.
     */
    Files.delete(targetDirectory);

    Mockito.when(mockJavaLayerConfigurations.getDependencyLayerEntries())
        .thenReturn(filesToLayerEntries(testDependencies, "/app/libs"));
    Mockito.when(mockJavaLayerConfigurations.getSnapshotDependencyLayerEntries())
        .thenReturn(filesToLayerEntries(testSnapshotDependencies, "/snapshots"));
    Mockito.when(mockJavaLayerConfigurations.getResourceLayerEntries())
        .thenReturn(filesToLayerEntries(testResources, "/more/resources"));
    Mockito.when(mockJavaLayerConfigurations.getClassLayerEntries())
        .thenReturn(filesToLayerEntries(testClasses, "/my/classes"));
    Mockito.when(mockJavaLayerConfigurations.getExplodedWarEntries())
        .thenReturn(filesToLayerEntries(testExplodedWarFiles, "/exploded/war"));
    Mockito.when(mockJavaLayerConfigurations.getExtraFilesLayerEntries())
        .thenReturn(filesToLayerEntries(testExtraFiles, "/"));

    new JavaDockerContextGenerator(mockJavaLayerConfigurations)
        .setBaseImage("somebaseimage")
        .generate(targetDirectory);

    Assert.assertTrue(Files.exists(targetDirectory.resolve("Dockerfile")));
    assertSameFiles(targetDirectory.resolve("libs/app/libs"), testDependencies);
    assertSameFiles(targetDirectory.resolve("snapshot-libs/snapshots"), testSnapshotDependencies);
    assertSameFiles(targetDirectory.resolve("resources/more/resources"), testResources);
    assertSameFiles(targetDirectory.resolve("classes/my/classes"), testClasses);
    assertSameFiles(targetDirectory.resolve("exploded-war/exploded/war"), testExplodedWarFiles);
    assertSameFiles(targetDirectory.resolve("root"), testExtraFiles);
  }

  @Test
  public void testMakeDockerfile() throws IOException {
    String expectedBaseImage = "somebaseimage";
    List<String> expectedJvmFlags = Arrays.asList("-flag", "another\"Flag");
    String expectedMainClass = "SomeMainClass";
    List<String> expectedJavaArguments = Arrays.asList("arg1", "arg2");
    Map<String, String> expectedEnv = ImmutableMap.of("key1", "value1", "key2", "value2");
    List<String> exposedPorts = Arrays.asList("1000/tcp", "2000-2010/udp");
    Map<String, String> expectedLabels =
        ImmutableMap.of(
            "key1",
            "value",
            "key2",
            "value with\\backslashes\"and\\\\\"\"quotes\"\\",
            "key3",
            "value3");

    Path ignored = Paths.get("ignored");
    Mockito.when(mockJavaLayerConfigurations.getDependencyLayerEntries())
        .thenReturn(ImmutableList.of(new LayerEntry(ignored, AbsoluteUnixPath.get("/app/libs"))));
    Mockito.when(mockJavaLayerConfigurations.getSnapshotDependencyLayerEntries())
        .thenReturn(ImmutableList.of(new LayerEntry(ignored, AbsoluteUnixPath.get("/snapshots"))));
    Mockito.when(mockJavaLayerConfigurations.getResourceLayerEntries())
        .thenReturn(
            ImmutableList.of(new LayerEntry(ignored, AbsoluteUnixPath.get("/my/resources"))));
    Mockito.when(mockJavaLayerConfigurations.getClassLayerEntries())
        .thenReturn(
            ImmutableList.of(new LayerEntry(ignored, AbsoluteUnixPath.get("/my/classes/"))));
    Mockito.when(mockJavaLayerConfigurations.getExplodedWarEntries())
        .thenReturn(
            ImmutableList.of(new LayerEntry(ignored, AbsoluteUnixPath.get("/exploded/war"))));
    Mockito.when(mockJavaLayerConfigurations.getExtraFilesLayerEntries())
        .thenReturn(ImmutableList.of(new LayerEntry(ignored, AbsoluteUnixPath.get("/"))));

    String dockerfile =
        new JavaDockerContextGenerator(mockJavaLayerConfigurations)
            .setBaseImage(expectedBaseImage)
            .setEntrypoint(
                JavaEntrypointConstructor.makeDefaultEntrypoint(
                    AbsoluteUnixPath.get("/app"), expectedJvmFlags, expectedMainClass))
            .setJavaArguments(expectedJavaArguments)
            .setEnvironment(expectedEnv)
            .setExposedPorts(exposedPorts)
            .setLabels(expectedLabels)
            .makeDockerfile();

    // Need to split/rejoin the string here to avoid cross-platform troubles
    List<String> sampleDockerfile =
        Resources.readLines(Resources.getResource("sampleDockerfile"), StandardCharsets.UTF_8);
    Assert.assertEquals(String.join("\n", sampleDockerfile), dockerfile);
  }
}
