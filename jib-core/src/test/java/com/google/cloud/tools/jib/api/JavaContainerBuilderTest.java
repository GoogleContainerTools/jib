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

import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.buildplan.FileEntry;
import com.google.cloud.tools.jib.api.buildplan.RelativeUnixPath;
import com.google.cloud.tools.jib.configuration.BuildContext;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;
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

  /** Gets the extraction paths in the specified layer of a give {@link BuildContext}. */
  private static List<AbsoluteUnixPath> getExtractionPaths(
      BuildContext buildContext, String layerName) {
    return buildContext.getLayerConfigurations().stream()
        .filter(layerConfiguration -> layerConfiguration.getName().equals(layerName))
        .findFirst()
        .map(
            layerConfiguration ->
                layerConfiguration.getEntries().stream()
                    .map(FileEntry::getExtractionPath)
                    .collect(Collectors.toList()))
        .orElse(ImmutableList.of());
  }

  @Test
  public void testToJibContainerBuilder_all()
      throws InvalidImageReferenceException, URISyntaxException, IOException,
          CacheDirectoryCreationException {
    BuildContext buildContext =
        JavaContainerBuilder.from("scratch")
            .setAppRoot("/hello")
            .addResources(getResource("core/application/resources"))
            .addClasses(getResource("core/application/classes"))
            .addDependencies(
                getResource("core/application/dependencies/dependency-1.0.0.jar"),
                getResource("core/application/dependencies/more/dependency-1.0.0.jar"))
            .addSnapshotDependencies(
                getResource("core/application/snapshot-dependencies/dependency-1.0.0-SNAPSHOT.jar"))
            .addProjectDependencies(
                getResource("core/application/dependencies/libraryA.jar"),
                getResource("core/application/dependencies/libraryB.jar"))
            .addToClasspath(getResource("core/fileA"), getResource("core/fileB"))
            .setClassesDestination(RelativeUnixPath.get("different-classes"))
            .setResourcesDestination(RelativeUnixPath.get("different-resources"))
            .setDependenciesDestination(RelativeUnixPath.get("different-libs"))
            .setOthersDestination(RelativeUnixPath.get("different-classpath"))
            .addJvmFlags("-xflag1", "-xflag2")
            .setMainClass("HelloWorld")
            .toContainerBuilder()
            .toBuildContext(Containerizer.to(RegistryImage.named("hello")));

    // Check entrypoint
    Assert.assertEquals(
        ImmutableList.of(
            "java",
            "-xflag1",
            "-xflag2",
            "-cp",
            "/hello/different-resources:/hello/different-classes:/hello/different-libs/*:/hello/different-classpath",
            "HelloWorld"),
        buildContext.getContainerConfiguration().getEntrypoint());

    // Check dependencies
    List<AbsoluteUnixPath> expectedDependencies =
        ImmutableList.of(
            AbsoluteUnixPath.get("/hello/different-libs/dependency-1.0.0-770.jar"),
            AbsoluteUnixPath.get("/hello/different-libs/dependency-1.0.0-200.jar"));
    Assert.assertEquals(expectedDependencies, getExtractionPaths(buildContext, "dependencies"));

    // Check snapshots
    List<AbsoluteUnixPath> expectedSnapshotDependencies =
        ImmutableList.of(
            AbsoluteUnixPath.get("/hello/different-libs/dependency-1.0.0-SNAPSHOT.jar"));
    Assert.assertEquals(
        expectedSnapshotDependencies, getExtractionPaths(buildContext, "snapshot dependencies"));

    List<AbsoluteUnixPath> expectedProjectDependencies =
        ImmutableList.of(
            AbsoluteUnixPath.get("/hello/different-libs/libraryA.jar"),
            AbsoluteUnixPath.get("/hello/different-libs/libraryB.jar"));
    Assert.assertEquals(
        expectedProjectDependencies, getExtractionPaths(buildContext, "project dependencies"));

    // Check resources
    List<AbsoluteUnixPath> expectedResources =
        ImmutableList.of(
            AbsoluteUnixPath.get("/hello/different-resources/resourceA"),
            AbsoluteUnixPath.get("/hello/different-resources/resourceB"),
            AbsoluteUnixPath.get("/hello/different-resources/world"));
    Assert.assertEquals(expectedResources, getExtractionPaths(buildContext, "resources"));

    // Check classes
    List<AbsoluteUnixPath> expectedClasses =
        ImmutableList.of(
            AbsoluteUnixPath.get("/hello/different-classes/HelloWorld.class"),
            AbsoluteUnixPath.get("/hello/different-classes/some.class"));
    Assert.assertEquals(expectedClasses, getExtractionPaths(buildContext, "classes"));

    // Check additional classpath files
    List<AbsoluteUnixPath> expectedOthers =
        ImmutableList.of(
            AbsoluteUnixPath.get("/hello/different-classpath/fileA"),
            AbsoluteUnixPath.get("/hello/different-classpath/fileB"));
    Assert.assertEquals(expectedOthers, getExtractionPaths(buildContext, "extra files"));
  }

  @Test
  public void testToJibContainerBuilder_missingAndMultipleAdds()
      throws InvalidImageReferenceException, URISyntaxException, IOException,
          CacheDirectoryCreationException {
    BuildContext buildContext =
        JavaContainerBuilder.from("scratch")
            .addDependencies(getResource("core/application/dependencies/libraryA.jar"))
            .addDependencies(getResource("core/application/dependencies/libraryB.jar"))
            .addSnapshotDependencies(
                getResource("core/application/snapshot-dependencies/dependency-1.0.0-SNAPSHOT.jar"))
            .addClasses(getResource("core/application/classes/"))
            .addClasses(getResource("core/class-finder-tests/extension"))
            .setMainClass("HelloWorld")
            .toContainerBuilder()
            .toBuildContext(Containerizer.to(RegistryImage.named("hello")));

    // Check entrypoint
    Assert.assertEquals(
        ImmutableList.of("java", "-cp", "/app/libs/*:/app/classes", "HelloWorld"),
        buildContext.getContainerConfiguration().getEntrypoint());

    // Check dependencies
    List<AbsoluteUnixPath> expectedDependencies =
        ImmutableList.of(
            AbsoluteUnixPath.get("/app/libs/libraryA.jar"),
            AbsoluteUnixPath.get("/app/libs/libraryB.jar"));
    Assert.assertEquals(expectedDependencies, getExtractionPaths(buildContext, "dependencies"));

    // Check snapshots
    List<AbsoluteUnixPath> expectedSnapshotDependencies =
        ImmutableList.of(AbsoluteUnixPath.get("/app/libs/dependency-1.0.0-SNAPSHOT.jar"));
    Assert.assertEquals(
        expectedSnapshotDependencies, getExtractionPaths(buildContext, "snapshot dependencies"));

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
    Assert.assertEquals(expectedClasses, getExtractionPaths(buildContext, "classes"));

    // Check empty layers
    Assert.assertEquals(ImmutableList.of(), getExtractionPaths(buildContext, "resources"));
    Assert.assertEquals(ImmutableList.of(), getExtractionPaths(buildContext, "extra files"));
  }

  @Test
  public void testToJibContainerBuilder_setAppRootLate()
      throws URISyntaxException, IOException, InvalidImageReferenceException,
          CacheDirectoryCreationException {
    BuildContext buildContext =
        JavaContainerBuilder.from("scratch")
            .addClasses(getResource("core/application/classes"))
            .addResources(getResource("core/application/resources"))
            .addDependencies(getResource("core/application/dependencies/libraryA.jar"))
            .addToClasspath(getResource("core/fileA"))
            .setAppRoot("/different")
            .setMainClass("HelloWorld")
            .toContainerBuilder()
            .toBuildContext(Containerizer.to(RegistryImage.named("hello")));

    // Check entrypoint
    Assert.assertEquals(
        ImmutableList.of(
            "java",
            "-cp",
            "/different/classes:/different/resources:/different/libs/*:/different/classpath",
            "HelloWorld"),
        buildContext.getContainerConfiguration().getEntrypoint());

    // Check classes
    List<AbsoluteUnixPath> expectedClasses =
        ImmutableList.of(
            AbsoluteUnixPath.get("/different/classes/HelloWorld.class"),
            AbsoluteUnixPath.get("/different/classes/some.class"));
    Assert.assertEquals(expectedClasses, getExtractionPaths(buildContext, "classes"));

    // Check resources
    List<AbsoluteUnixPath> expectedResources =
        ImmutableList.of(
            AbsoluteUnixPath.get("/different/resources/resourceA"),
            AbsoluteUnixPath.get("/different/resources/resourceB"),
            AbsoluteUnixPath.get("/different/resources/world"));
    Assert.assertEquals(expectedResources, getExtractionPaths(buildContext, "resources"));

    // Check dependencies
    List<AbsoluteUnixPath> expectedDependencies =
        ImmutableList.of(AbsoluteUnixPath.get("/different/libs/libraryA.jar"));
    Assert.assertEquals(expectedDependencies, getExtractionPaths(buildContext, "dependencies"));

    Assert.assertEquals(expectedClasses, getExtractionPaths(buildContext, "classes"));

    // Check additional classpath files
    List<AbsoluteUnixPath> expectedOthers =
        ImmutableList.of(AbsoluteUnixPath.get("/different/classpath/fileA"));
    Assert.assertEquals(expectedOthers, getExtractionPaths(buildContext, "extra files"));
  }

  @Test
  public void testToJibContainerBuilder_mainClassNull()
      throws IOException, InvalidImageReferenceException, CacheDirectoryCreationException,
          URISyntaxException {
    BuildContext buildContext =
        JavaContainerBuilder.from("scratch")
            .addClasses(getResource("core/application/classes/"))
            .toContainerBuilder()
            .toBuildContext(Containerizer.to(RegistryImage.named("hello")));
    Assert.assertNull(buildContext.getContainerConfiguration().getEntrypoint());

    try {
      JavaContainerBuilder.from("scratch").addJvmFlags("-flag1", "-flag2").toContainerBuilder();
      Assert.fail();

    } catch (IllegalStateException ex) {
      Assert.assertEquals(
          "Failed to construct entrypoint on JavaContainerBuilder; jvmFlags were set, but "
              + "mainClass is null. Specify the main class using "
              + "JavaContainerBuilder#setMainClass(String), or consider using MainClassFinder to "
              + "infer the main class.",
          ex.getMessage());
    }
  }

  @Test
  public void testToJibContainerBuilder_classpathEmpty()
      throws IOException, InvalidImageReferenceException {
    try {
      JavaContainerBuilder.from("scratch").setMainClass("Hello").toContainerBuilder();
      Assert.fail();

    } catch (IllegalStateException ex) {
      Assert.assertEquals(
          "Failed to construct entrypoint because no files were added to the JavaContainerBuilder",
          ex.getMessage());
    }
  }
}
