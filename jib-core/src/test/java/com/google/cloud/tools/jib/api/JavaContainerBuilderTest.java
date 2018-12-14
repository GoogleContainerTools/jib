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

package com.google.cloud.tools.jib.api;

import com.google.cloud.tools.jib.configuration.BuildConfiguration;
import com.google.cloud.tools.jib.configuration.CacheDirectoryCreationException;
import com.google.cloud.tools.jib.configuration.ContainerConfiguration;
import com.google.cloud.tools.jib.filesystem.AbsoluteUnixPath;
import com.google.cloud.tools.jib.image.InvalidImageReferenceException;
import com.google.cloud.tools.jib.image.LayerEntry;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Assert;
import org.junit.Test;

/** Tests for {@link JavaContainerBuilder}. */
public class JavaContainerBuilderTest {

  /** Gets a resource file in a singleton list. */
  private static List<Path> getResourceAsList(String directory) throws URISyntaxException {
    return Collections.singletonList(Paths.get(Resources.getResource(directory).toURI()));
  }

  /** Gets a resource file as a {@link Path}. */
  private static Path getResourceAsPath(String directory) throws URISyntaxException {
    return Paths.get(Resources.getResource(directory).toURI());
  }

  /** Gets the extraction paths in the specified layer of a give {@link BuildConfiguration}. */
  private static List<AbsoluteUnixPath> getExtractionPaths(
      BuildConfiguration buildConfiguration, String layerName) {
    return buildConfiguration
        .getLayerConfigurations()
        .stream()
        .filter(layerConfiguration -> layerConfiguration.getName().equals(layerName))
        .findFirst()
        .map(
            layerConfiguration ->
                layerConfiguration
                    .getLayerEntries()
                    .stream()
                    .map(LayerEntry::getExtractionPath)
                    .collect(Collectors.toList()))
        .orElse(ImmutableList.of());
  }

  @Test
  public void testToJibContainerBuilder_all()
      throws InvalidImageReferenceException, URISyntaxException, IOException,
          CacheDirectoryCreationException {
    BuildConfiguration buildConfiguration =
        JavaContainerBuilder.fromDistroless()
            .addClasses(getResourceAsPath("application/classes"))
            .addResources(getResourceAsPath("application/resources"))
            .addDependencies(getResourceAsList("application/dependencies"))
            .addDependencies(getResourceAsList("application/snapshot-dependencies"))
            .addToClasspath(getResourceAsList("fileA"))
            .addToClasspath(getResourceAsPath("fileB"))
            .setJvmFlags("-xflag1", "-xflag2")
            .setMainClass("HelloWorld")
            .toContainerBuilder()
            .toBuildConfiguration(
                Containerizer.to(RegistryImage.named("hello")),
                MoreExecutors.newDirectExecutorService());

    // Check entrypoint
    ContainerConfiguration containerConfiguration = buildConfiguration.getContainerConfiguration();
    Assert.assertNotNull(containerConfiguration);
    Assert.assertEquals(
        ImmutableList.of(
            "java",
            "-xflag1",
            "-xflag2",
            "-cp",
            "/app/classes:/app/resources:/app/libs/*:/app/other",
            "HelloWorld"),
        containerConfiguration.getEntrypoint());

    // Check dependencies
    List<AbsoluteUnixPath> expectedDependencies =
        ImmutableList.of(
            AbsoluteUnixPath.get("/app/libs/dependency-1.0.0.jar"),
            AbsoluteUnixPath.get("/app/libs/libraryA.jar"),
            AbsoluteUnixPath.get("/app/libs/libraryB.jar"));
    Assert.assertEquals(
        expectedDependencies, getExtractionPaths(buildConfiguration, "dependencies"));

    // Check snapshots
    List<AbsoluteUnixPath> expectedSnapshotDependencies =
        ImmutableList.of(AbsoluteUnixPath.get("/app/libs/dependency-1.0.0-SNAPSHOT.jar"));
    Assert.assertEquals(
        expectedSnapshotDependencies,
        getExtractionPaths(buildConfiguration, "snapshot dependencies"));

    // Check resources
    List<AbsoluteUnixPath> expectedResources =
        ImmutableList.of(
            AbsoluteUnixPath.get("/app/resources/resourceA"),
            AbsoluteUnixPath.get("/app/resources/resourceB"),
            AbsoluteUnixPath.get("/app/resources/world"));
    Assert.assertEquals(expectedResources, getExtractionPaths(buildConfiguration, "resources"));

    // Check classes
    List<AbsoluteUnixPath> expectedClasses =
        ImmutableList.of(
            AbsoluteUnixPath.get("/app/classes/HelloWorld.class"),
            AbsoluteUnixPath.get("/app/classes/some.class"));
    Assert.assertEquals(expectedClasses, getExtractionPaths(buildConfiguration, "classes"));

    // Check additional classpath files
    List<AbsoluteUnixPath> expectedOthers =
        ImmutableList.of(
            AbsoluteUnixPath.get("/app/other/fileA"), AbsoluteUnixPath.get("/app/other/fileB"));
    Assert.assertEquals(expectedOthers, getExtractionPaths(buildConfiguration, "extra files"));
  }

  @Test
  public void testToJibContainerBuilder_missingAndMultipleAdds()
      throws InvalidImageReferenceException, URISyntaxException, IOException,
          CacheDirectoryCreationException {
    BuildConfiguration buildConfiguration =
        JavaContainerBuilder.fromDistroless()
            .addClasses(getResourceAsPath("application/classes/"))
            .addClasses(getResourceAsPath("class-finder-tests/extension"))
            .addDependencies(
                getResourceAsPath("application/dependencies/libraryA.jar"),
                getResourceAsPath("application/dependencies/libraryB.jar"))
            .addDependencies(
                getResourceAsList(
                    "application/snapshot-dependencies/dependency-1.0.0-SNAPSHOT.jar"))
            .setMainClass("HelloWorld")
            .toContainerBuilder()
            .toBuildConfiguration(
                Containerizer.to(RegistryImage.named("hello")),
                MoreExecutors.newDirectExecutorService());

    // Check entrypoint
    ContainerConfiguration containerConfiguration = buildConfiguration.getContainerConfiguration();
    Assert.assertNotNull(containerConfiguration);
    Assert.assertEquals(
        ImmutableList.of("java", "-cp", "/app/classes:/app/libs/*", "HelloWorld"),
        containerConfiguration.getEntrypoint());

    // Check dependencies
    List<AbsoluteUnixPath> expectedDependencies =
        ImmutableList.of(
            AbsoluteUnixPath.get("/app/libs/libraryA.jar"),
            AbsoluteUnixPath.get("/app/libs/libraryB.jar"));
    Assert.assertEquals(
        expectedDependencies, getExtractionPaths(buildConfiguration, "dependencies"));

    // Check snapshots
    List<AbsoluteUnixPath> expectedSnapshotDependencies =
        ImmutableList.of(AbsoluteUnixPath.get("/app/libs/dependency-1.0.0-SNAPSHOT.jar"));
    Assert.assertEquals(
        expectedSnapshotDependencies,
        getExtractionPaths(buildConfiguration, "snapshot dependencies"));

    // Check classes
    List<AbsoluteUnixPath> expectedClasses =
        ImmutableList.of(
            AbsoluteUnixPath.get("/app/classes/HelloWorld.class"),
            AbsoluteUnixPath.get("/app/classes/some.class"),
            AbsoluteUnixPath.get("/app/classes/main/"),
            AbsoluteUnixPath.get("/app/classes/main/MainClass.class"),
            AbsoluteUnixPath.get("/app/classes/pack/"),
            AbsoluteUnixPath.get("/app/classes/pack/Apple.class"),
            AbsoluteUnixPath.get("/app/classes/pack/Orange.class"));
    Assert.assertEquals(expectedClasses, getExtractionPaths(buildConfiguration, "classes"));

    // Check empty layers
    Assert.assertEquals(ImmutableList.of(), getExtractionPaths(buildConfiguration, "resources"));
    Assert.assertEquals(ImmutableList.of(), getExtractionPaths(buildConfiguration, "extra files"));
  }

  @Test
  public void testToJibContainerBuilder_mainClassNull() throws InvalidImageReferenceException {
    try {
      JavaContainerBuilder.fromDistroless().toContainerBuilder();
      Assert.fail();

    } catch (IllegalArgumentException ex) {
      Assert.assertEquals(
          "mainClass is null on JavaContainerBuilder; specify the main class using "
              + "JavaContainerBuilder#setMainClass(String), or consider using a "
              + "jib.frontend.MainClassFinder to infer the main class",
          ex.getMessage());
    }
  }

  @Test
  public void testToJibContainerBuilder_classpathEmpty() throws InvalidImageReferenceException {
    try {
      JavaContainerBuilder.fromDistroless().setMainClass("Hello").toContainerBuilder();
      Assert.fail();

    } catch (IllegalArgumentException ex) {
      Assert.assertEquals(
          "Failed to construct entrypoint because no files were added to the JavaContainerBuilder",
          ex.getMessage());
    }
  }
}
