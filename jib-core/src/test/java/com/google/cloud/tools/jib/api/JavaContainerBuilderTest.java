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
import com.google.cloud.tools.jib.configuration.ContainerConfiguration;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Assert;
import org.junit.Test;

/** Tests for {@link JavaContainerBuilder}. */
public class JavaContainerBuilderTest {

  /** Gets a resource file as a {@link Path}. */
  private static Path getResource(String directory) throws URISyntaxException {
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
            .setAppRoot("/hello")
            .addResources(getResource("core/application/resources"))
            .addClasses(getResource("core/application/classes"))
            .addDependencies(
                getResource("core/application/dependencies/dependency-1.0.0.jar"),
                getResource("core/application/dependencies/more/dependency-1.0.0.jar"),
                getResource("core/application/dependencies/libraryA.jar"),
                getResource("core/application/dependencies/libraryB.jar"),
                getResource("core/application/snapshot-dependencies/dependency-1.0.0-SNAPSHOT.jar"))
            .addToClasspath(getResource("core/fileA"), getResource("core/fileB"))
            .setClassesDestination(RelativeUnixPath.get("different-classes"))
            .setResourcesDestination(RelativeUnixPath.get("different-resources"))
            .setDependenciesDestination(RelativeUnixPath.get("different-libs"))
            .setOthersDestination(RelativeUnixPath.get("different-classpath"))
            .addJvmFlags("-xflag1", "-xflag2")
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
            "/hello/different-resources:/hello/different-classes:/hello/different-libs/*:/hello/different-classpath",
            "HelloWorld"),
        containerConfiguration.getEntrypoint());

    // Check dependencies
    List<AbsoluteUnixPath> expectedDependencies =
        ImmutableList.of(
            AbsoluteUnixPath.get("/hello/different-libs/dependency-1.0.0-770.jar"),
            AbsoluteUnixPath.get("/hello/different-libs/dependency-1.0.0-200.jar"),
            AbsoluteUnixPath.get("/hello/different-libs/libraryA.jar"),
            AbsoluteUnixPath.get("/hello/different-libs/libraryB.jar"));
    Assert.assertEquals(
        expectedDependencies, getExtractionPaths(buildConfiguration, "dependencies"));

    // Check snapshots
    List<AbsoluteUnixPath> expectedSnapshotDependencies =
        ImmutableList.of(
            AbsoluteUnixPath.get("/hello/different-libs/dependency-1.0.0-SNAPSHOT.jar"));
    Assert.assertEquals(
        expectedSnapshotDependencies,
        getExtractionPaths(buildConfiguration, "snapshot dependencies"));

    // Check resources
    List<AbsoluteUnixPath> expectedResources =
        ImmutableList.of(
            AbsoluteUnixPath.get("/hello/different-resources/resourceA"),
            AbsoluteUnixPath.get("/hello/different-resources/resourceB"),
            AbsoluteUnixPath.get("/hello/different-resources/world"));
    Assert.assertEquals(expectedResources, getExtractionPaths(buildConfiguration, "resources"));

    // Check classes
    List<AbsoluteUnixPath> expectedClasses =
        ImmutableList.of(
            AbsoluteUnixPath.get("/hello/different-classes/HelloWorld.class"),
            AbsoluteUnixPath.get("/hello/different-classes/some.class"));
    Assert.assertEquals(expectedClasses, getExtractionPaths(buildConfiguration, "classes"));

    // Check additional classpath files
    List<AbsoluteUnixPath> expectedOthers =
        ImmutableList.of(
            AbsoluteUnixPath.get("/hello/different-classpath/fileA"),
            AbsoluteUnixPath.get("/hello/different-classpath/fileB"));
    Assert.assertEquals(expectedOthers, getExtractionPaths(buildConfiguration, "extra files"));
  }

  @Test
  public void testToJibContainerBuilder_missingAndMultipleAdds()
      throws InvalidImageReferenceException, URISyntaxException, IOException,
          CacheDirectoryCreationException {
    BuildConfiguration buildConfiguration =
        JavaContainerBuilder.fromDistroless()
            .addDependencies(getResource("core/application/dependencies/libraryA.jar"))
            .addDependencies(getResource("core/application/dependencies/libraryB.jar"))
            .addDependencies(
                getResource("core/application/snapshot-dependencies/dependency-1.0.0-SNAPSHOT.jar"))
            .addClasses(getResource("core/application/classes/"))
            .addClasses(getResource("core/class-finder-tests/extension"))
            .setMainClass("HelloWorld")
            .toContainerBuilder()
            .toBuildConfiguration(
                Containerizer.to(RegistryImage.named("hello")),
                MoreExecutors.newDirectExecutorService());

    // Check entrypoint
    ContainerConfiguration containerConfiguration = buildConfiguration.getContainerConfiguration();
    Assert.assertNotNull(containerConfiguration);
    Assert.assertEquals(
        ImmutableList.of("java", "-cp", "/app/libs/*:/app/classes", "HelloWorld"),
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
  public void testToJibContainerBuilder_setAppRootLate()
      throws URISyntaxException, IOException, InvalidImageReferenceException,
          CacheDirectoryCreationException {
    BuildConfiguration buildConfiguration =
        JavaContainerBuilder.fromDistroless()
            .addClasses(getResource("core/application/classes"))
            .addResources(getResource("core/application/resources"))
            .addDependencies(getResource("core/application/dependencies/libraryA.jar"))
            .addToClasspath(getResource("core/fileA"))
            .setAppRoot("/different")
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
            "-cp",
            "/different/classes:/different/resources:/different/libs/*:/different/classpath",
            "HelloWorld"),
        containerConfiguration.getEntrypoint());

    // Check classes
    List<AbsoluteUnixPath> expectedClasses =
        ImmutableList.of(
            AbsoluteUnixPath.get("/different/classes/HelloWorld.class"),
            AbsoluteUnixPath.get("/different/classes/some.class"));
    Assert.assertEquals(expectedClasses, getExtractionPaths(buildConfiguration, "classes"));

    // Check resources
    List<AbsoluteUnixPath> expectedResources =
        ImmutableList.of(
            AbsoluteUnixPath.get("/different/resources/resourceA"),
            AbsoluteUnixPath.get("/different/resources/resourceB"),
            AbsoluteUnixPath.get("/different/resources/world"));
    Assert.assertEquals(expectedResources, getExtractionPaths(buildConfiguration, "resources"));

    // Check dependencies
    List<AbsoluteUnixPath> expectedDependencies =
        ImmutableList.of(AbsoluteUnixPath.get("/different/libs/libraryA.jar"));
    Assert.assertEquals(
        expectedDependencies, getExtractionPaths(buildConfiguration, "dependencies"));

    Assert.assertEquals(expectedClasses, getExtractionPaths(buildConfiguration, "classes"));

    // Check additional classpath files
    List<AbsoluteUnixPath> expectedOthers =
        ImmutableList.of(AbsoluteUnixPath.get("/different/classpath/fileA"));
    Assert.assertEquals(expectedOthers, getExtractionPaths(buildConfiguration, "extra files"));
  }

  @Test
  public void testToJibContainerBuilder_mainClassNull()
      throws IOException, InvalidImageReferenceException, CacheDirectoryCreationException,
          URISyntaxException {
    BuildConfiguration buildConfiguration =
        JavaContainerBuilder.fromDistroless()
            .addClasses(getResource("core/application/classes/"))
            .toContainerBuilder()
            .toBuildConfiguration(
                Containerizer.to(RegistryImage.named("hello")),
                MoreExecutors.newDirectExecutorService());
    Assert.assertNotNull(buildConfiguration.getContainerConfiguration());
    Assert.assertNull(buildConfiguration.getContainerConfiguration().getEntrypoint());

    try {
      JavaContainerBuilder.fromDistroless().addJvmFlags("-flag1", "-flag2").toContainerBuilder();
      Assert.fail();

    } catch (IllegalStateException ex) {
      Assert.assertEquals(
          "Failed to construct entrypoint on JavaContainerBuilder; jvmFlags were set, but "
              + "mainClass is null. Specify the main class using "
              + "JavaContainerBuilder#setMainClass(String), or consider using a "
              + "jib.frontend.MainClassFinder to infer the main class.",
          ex.getMessage());
    }
  }

  @Test
  public void testToJibContainerBuilder_classpathEmpty() throws IOException {
    try {
      JavaContainerBuilder.fromDistroless().setMainClass("Hello").toContainerBuilder();
      Assert.fail();

    } catch (IllegalStateException ex) {
      Assert.assertEquals(
          "Failed to construct entrypoint because no files were added to the JavaContainerBuilder",
          ex.getMessage());
    }
  }
}
