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

package com.google.cloud.tools.jib.plugins.common;

import com.google.cloud.tools.jib.api.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.CacheDirectoryCreationException;
import com.google.cloud.tools.jib.api.Containerizer;
import com.google.cloud.tools.jib.api.InvalidImageReferenceException;
import com.google.cloud.tools.jib.api.JavaContainerBuilder;
import com.google.cloud.tools.jib.api.JavaContainerBuilder.LayerType;
import com.google.cloud.tools.jib.api.JibContainerBuilder;
import com.google.cloud.tools.jib.api.JibContainerBuilderTestHelper;
import com.google.cloud.tools.jib.api.LayerConfiguration;
import com.google.cloud.tools.jib.api.LayerEntry;
import com.google.cloud.tools.jib.api.RegistryImage;
import com.google.cloud.tools.jib.configuration.BuildConfiguration;
import com.google.cloud.tools.jib.filesystem.FileOperations;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Tests for {@link JavaContainerBuilderHelper}. */
public class JavaContainerBuilderHelperTest {

  private static <T> void assertLayerEntriesUnordered(
      List<T> expectedPaths, List<LayerEntry> entries, Function<LayerEntry, T> fieldSelector) {
    List<T> expected = expectedPaths.stream().sorted().collect(Collectors.toList());
    List<T> actual = entries.stream().map(fieldSelector).sorted().collect(Collectors.toList());
    Assert.assertEquals(expected, actual);
  }

  private static void assertSourcePathsUnordered(
      List<Path> expectedPaths, List<LayerEntry> entries) {
    assertLayerEntriesUnordered(expectedPaths, entries, LayerEntry::getSourceFile);
  }

  private static void assertExtractionPathsUnordered(
      List<String> expectedPaths, List<LayerEntry> entries) {
    assertLayerEntriesUnordered(
        expectedPaths, entries, layerEntry -> layerEntry.getExtractionPath().toString());
  }

  private static List<LayerConfiguration> getLayerConfigurationsByName(
      BuildConfiguration buildConfiguration, String name) {
    return buildConfiguration
        .getLayerConfigurations()
        .stream()
        .filter(layer -> layer.getName().equals(name))
        .collect(Collectors.toList());
  }

  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void testExtraDirectoryLayerConfiguration() throws URISyntaxException, IOException {
    Path extraFilesDirectory = Paths.get(Resources.getResource("core/layer").toURI());
    LayerConfiguration layerConfiguration =
        JavaContainerBuilderHelper.extraDirectoryLayerConfiguration(
            extraFilesDirectory, Collections.emptyMap(), (ignored1, ignored2) -> Instant.EPOCH);
    assertSourcePathsUnordered(
        Arrays.asList(
            extraFilesDirectory.resolve("a"),
            extraFilesDirectory.resolve("a/b"),
            extraFilesDirectory.resolve("a/b/bar"),
            extraFilesDirectory.resolve("c"),
            extraFilesDirectory.resolve("c/cat"),
            extraFilesDirectory.resolve("foo")),
        layerConfiguration.getLayerEntries());
  }

  @Test
  public void testFromExplodedWar()
      throws URISyntaxException, IOException, InvalidImageReferenceException,
          CacheDirectoryCreationException {
    // Copy test files to a temporary directory that we can safely operate on
    Path resourceExplodedWar =
        Paths.get(Resources.getResource("plugins-common/exploded-war").toURI());
    FileOperations.copy(ImmutableList.of(resourceExplodedWar), temporaryFolder.getRoot().toPath());
    Path temporaryExplodedWar = temporaryFolder.getRoot().toPath().resolve("exploded-war");

    Files.createDirectories(temporaryExplodedWar.resolve("WEB-INF/classes/empty_dir"));

    JavaContainerBuilder javaContainerBuilder =
        JavaContainerBuilder.from(RegistryImage.named("base"))
            .setAppRoot(AbsoluteUnixPath.get("/my/app"));
    JibContainerBuilder jibContainerBuilder =
        JavaContainerBuilderHelper.fromExplodedWar(javaContainerBuilder, temporaryExplodedWar);
    BuildConfiguration configuration =
        JibContainerBuilderTestHelper.toBuildConfiguration(
            jibContainerBuilder,
            Containerizer.to(RegistryImage.named("target"))
                .setExecutorService(MoreExecutors.newDirectExecutorService()));

    List<LayerConfiguration> resourcesLayerConfigurations =
        getLayerConfigurationsByName(configuration, LayerType.RESOURCES.getName());
    List<LayerConfiguration> classesLayerConfigurations =
        getLayerConfigurationsByName(configuration, LayerType.CLASSES.getName());
    List<LayerConfiguration> dependenciesLayerConfigurations =
        getLayerConfigurationsByName(configuration, LayerType.DEPENDENCIES.getName());
    List<LayerConfiguration> snapshotsLayerConfigurations =
        getLayerConfigurationsByName(configuration, LayerType.SNAPSHOT_DEPENDENCIES.getName());

    assertSourcePathsUnordered(
        Collections.singletonList(temporaryExplodedWar.resolve("WEB-INF/lib/dependency-1.0.0.jar")),
        dependenciesLayerConfigurations.get(0).getLayerEntries());
    assertSourcePathsUnordered(
        Collections.singletonList(
            temporaryExplodedWar.resolve("WEB-INF/lib/dependencyX-1.0.0-SNAPSHOT.jar")),
        snapshotsLayerConfigurations.get(0).getLayerEntries());
    assertSourcePathsUnordered(
        Arrays.asList(
            temporaryExplodedWar.resolve("META-INF"),
            temporaryExplodedWar.resolve("META-INF/context.xml"),
            temporaryExplodedWar.resolve("Test.jsp"),
            temporaryExplodedWar.resolve("WEB-INF"),
            temporaryExplodedWar.resolve("WEB-INF/classes"),
            temporaryExplodedWar.resolve("WEB-INF/classes/empty_dir"),
            temporaryExplodedWar.resolve("WEB-INF/classes/package"),
            temporaryExplodedWar.resolve("WEB-INF/classes/package/test.properties"),
            temporaryExplodedWar.resolve("WEB-INF/lib"),
            temporaryExplodedWar.resolve("WEB-INF/web.xml")),
        resourcesLayerConfigurations.get(0).getLayerEntries());
    assertSourcePathsUnordered(
        Arrays.asList(
            temporaryExplodedWar.resolve("WEB-INF/classes/HelloWorld.class"),
            temporaryExplodedWar.resolve("WEB-INF/classes/empty_dir"),
            temporaryExplodedWar.resolve("WEB-INF/classes/package"),
            temporaryExplodedWar.resolve("WEB-INF/classes/package/Other.class")),
        classesLayerConfigurations.get(0).getLayerEntries());

    assertExtractionPathsUnordered(
        Collections.singletonList("/my/app/WEB-INF/lib/dependency-1.0.0.jar"),
        dependenciesLayerConfigurations.get(0).getLayerEntries());
    assertExtractionPathsUnordered(
        Collections.singletonList("/my/app/WEB-INF/lib/dependencyX-1.0.0-SNAPSHOT.jar"),
        snapshotsLayerConfigurations.get(0).getLayerEntries());
    assertExtractionPathsUnordered(
        Arrays.asList(
            "/my/app/META-INF",
            "/my/app/META-INF/context.xml",
            "/my/app/Test.jsp",
            "/my/app/WEB-INF",
            "/my/app/WEB-INF/classes",
            "/my/app/WEB-INF/classes/empty_dir",
            "/my/app/WEB-INF/classes/package",
            "/my/app/WEB-INF/classes/package/test.properties",
            "/my/app/WEB-INF/lib",
            "/my/app/WEB-INF/web.xml"),
        resourcesLayerConfigurations.get(0).getLayerEntries());
    assertExtractionPathsUnordered(
        Arrays.asList(
            "/my/app/WEB-INF/classes/HelloWorld.class",
            "/my/app/WEB-INF/classes/empty_dir",
            "/my/app/WEB-INF/classes/package",
            "/my/app/WEB-INF/classes/package/Other.class"),
        classesLayerConfigurations.get(0).getLayerEntries());
  }
}
