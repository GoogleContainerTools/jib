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

public class JavaContainerBuilderTest {

  private static List<Path> getFromResources(String directory) throws URISyntaxException {
    return Collections.singletonList(Paths.get(Resources.getResource(directory).toURI()));
  }

  private static List<AbsoluteUnixPath> getExtractionPaths(
      BuildConfiguration buildConfiguration, String layerName) {
    return buildConfiguration
        .getLayerConfigurations()
        .stream()
        .filter(layerConfiguration -> layerConfiguration.getName().equals(layerName))
        .findFirst()
        .get()
        .getLayerEntries()
        .stream()
        .map(LayerEntry::getExtractionPath)
        .collect(Collectors.toList());
  }

  @Test
  public void testToJibContainerBuilder()
      throws InvalidImageReferenceException, URISyntaxException, IOException,
          CacheDirectoryCreationException {
    BuildConfiguration buildConfiguration =
        JavaContainerBuilder.fromDistroless()
            .addClasses(getFromResources("application/classes"))
            .addResources(getFromResources("application/resources"))
            .addDependencies(getFromResources("application/dependencies"))
            .addSnapshotDependencies(getFromResources("application/snapshot-dependencies"))
            .addToClasspath(getFromResources("fileA"))
            .setJvmFlags("-xflag1", "-xflag2")
            .setMainClass("HelloWorld")
            .toContainerBuilder()
            .toBuildConfiguration(
                Containerizer.to(RegistryImage.named("hello")),
                MoreExecutors.newDirectExecutorService());

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

    List<AbsoluteUnixPath> expectedDependencies =
        ImmutableList.of(
            AbsoluteUnixPath.get("/app/libs/dependency-1.0.0.jar"),
            AbsoluteUnixPath.get("/app/libs/libraryA.jar"),
            AbsoluteUnixPath.get("/app/libs/libraryB.jar"));
    List<AbsoluteUnixPath> actualDependencies =
        getExtractionPaths(buildConfiguration, "dependencies");
    Assert.assertEquals(expectedDependencies, actualDependencies);

    List<AbsoluteUnixPath> expectedSnapshotDependencies =
        ImmutableList.of(AbsoluteUnixPath.get("/app/libs/dependency-1.0.0-SNAPSHOT.jar"));
    List<AbsoluteUnixPath> actualSnapshotDependencies =
        getExtractionPaths(buildConfiguration, "snapshot dependencies");
    Assert.assertEquals(expectedSnapshotDependencies, actualSnapshotDependencies);

    List<AbsoluteUnixPath> expectedResources =
        ImmutableList.of(
            AbsoluteUnixPath.get("/app/resources/resourceA"),
            AbsoluteUnixPath.get("/app/resources/resourceB"),
            AbsoluteUnixPath.get("/app/resources/world"));
    List<AbsoluteUnixPath> actualResources = getExtractionPaths(buildConfiguration, "resources");
    Assert.assertEquals(expectedResources, actualResources);

    List<AbsoluteUnixPath> expectedClasses =
        ImmutableList.of(
            AbsoluteUnixPath.get("/app/classes/HelloWorld.class"),
            AbsoluteUnixPath.get("/app/classes/some.class"));
    List<AbsoluteUnixPath> actualClasses = getExtractionPaths(buildConfiguration, "classes");
    Assert.assertEquals(expectedClasses, actualClasses);

    List<AbsoluteUnixPath> expectedOthers =
        ImmutableList.of(AbsoluteUnixPath.get("/app/other/fileA"));
    List<AbsoluteUnixPath> actualOthers = getExtractionPaths(buildConfiguration, "extra files");
    Assert.assertEquals(expectedOthers, actualOthers);
  }
}
