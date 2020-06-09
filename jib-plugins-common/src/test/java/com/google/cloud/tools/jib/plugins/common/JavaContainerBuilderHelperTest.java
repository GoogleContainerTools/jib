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

import com.google.cloud.tools.jib.api.CacheDirectoryCreationException;
import com.google.cloud.tools.jib.api.Containerizer;
import com.google.cloud.tools.jib.api.InvalidImageReferenceException;
import com.google.cloud.tools.jib.api.JavaContainerBuilder;
import com.google.cloud.tools.jib.api.JavaContainerBuilder.LayerType;
import com.google.cloud.tools.jib.api.JibContainerBuilder;
import com.google.cloud.tools.jib.api.JibContainerBuilderTestHelper;
import com.google.cloud.tools.jib.api.RegistryImage;
import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.buildplan.FileEntriesLayer;
import com.google.cloud.tools.jib.api.buildplan.FileEntry;
import com.google.cloud.tools.jib.api.buildplan.FilePermissions;
import com.google.cloud.tools.jib.configuration.BuildContext;
import com.google.cloud.tools.jib.filesystem.FileOperations;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
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
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Tests for {@link JavaContainerBuilderHelper}. */
public class JavaContainerBuilderHelperTest {

  private static <T> void assertLayerEntriesUnordered(
      List<T> expectedPaths, List<FileEntry> entries, Function<FileEntry, T> fieldSelector) {
    List<T> expected = expectedPaths.stream().sorted().collect(Collectors.toList());
    List<T> actual = entries.stream().map(fieldSelector).sorted().collect(Collectors.toList());
    Assert.assertEquals(expected, actual);
  }

  private static void assertSourcePathsUnordered(
      List<Path> expectedPaths, List<FileEntry> entries) {
    assertLayerEntriesUnordered(expectedPaths, entries, FileEntry::getSourceFile);
  }

  private static void assertExtractionPathsUnordered(
      List<String> expectedPaths, List<FileEntry> entries) {
    assertLayerEntriesUnordered(
        expectedPaths, entries, layerEntry -> layerEntry.getExtractionPath().toString());
  }

  private static List<FileEntriesLayer> getLayerConfigurationsByName(
      BuildContext buildContext, String name) {
    return buildContext
        .getLayerConfigurations()
        .stream()
        .filter(layer -> layer.getName().equals(name))
        .collect(Collectors.toList());
  }

  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void testExtraDirectoryLayerConfiguration() throws URISyntaxException, IOException {
    Path extraFilesDirectory = Paths.get(Resources.getResource("core/layer").toURI());
    FileEntriesLayer layerConfiguration =
        JavaContainerBuilderHelper.extraDirectoryLayerConfiguration(
            extraFilesDirectory,
            AbsoluteUnixPath.get("/"),
            Collections.emptyMap(),
            (ignored1, ignored2) -> Instant.EPOCH);
    assertSourcePathsUnordered(
        Arrays.asList(
            extraFilesDirectory.resolve("a"),
            extraFilesDirectory.resolve("a/b"),
            extraFilesDirectory.resolve("a/b/bar"),
            extraFilesDirectory.resolve("c"),
            extraFilesDirectory.resolve("c/cat"),
            extraFilesDirectory.resolve("foo")),
        layerConfiguration.getEntries());
  }

  @Test
  public void testExtraDirectoryLayerConfiguration_globPermissions()
      throws URISyntaxException, IOException {
    Path extraFilesDirectory = Paths.get(Resources.getResource("core/layer").toURI());
    Map<String, FilePermissions> permissionsMap =
        ImmutableMap.of(
            "/a",
            FilePermissions.fromOctalString("123"),
            "/a/*",
            FilePermissions.fromOctalString("456"),
            "**/bar",
            FilePermissions.fromOctalString("765"));
    FileEntriesLayer fileEntriesLayer =
        JavaContainerBuilderHelper.extraDirectoryLayerConfiguration(
            extraFilesDirectory,
            AbsoluteUnixPath.get("/"),
            permissionsMap,
            (ignored1, ignored2) -> Instant.EPOCH);
    assertExtractionPathsUnordered(
        Arrays.asList("/a", "/a/b", "/a/b/bar", "/c", "/c/cat", "/foo"),
        fileEntriesLayer.getEntries());

    Map<AbsoluteUnixPath, FilePermissions> expectedPermissions =
        ImmutableMap.<AbsoluteUnixPath, FilePermissions>builder()
            .put(AbsoluteUnixPath.get("/a"), FilePermissions.fromOctalString("123"))
            .put(AbsoluteUnixPath.get("/a/b"), FilePermissions.fromOctalString("456"))
            .put(AbsoluteUnixPath.get("/a/b/bar"), FilePermissions.fromOctalString("765"))
            .put(AbsoluteUnixPath.get("/c"), FilePermissions.DEFAULT_FOLDER_PERMISSIONS)
            .put(AbsoluteUnixPath.get("/c/cat"), FilePermissions.DEFAULT_FILE_PERMISSIONS)
            .put(AbsoluteUnixPath.get("/foo"), FilePermissions.DEFAULT_FILE_PERMISSIONS)
            .build();
    for (FileEntry entry : fileEntriesLayer.getEntries()) {
      Assert.assertEquals(
          expectedPermissions.get(entry.getExtractionPath()), entry.getPermissions());
    }
  }

  @Test
  public void testExtraDirectoryLayerConfiguration_overlappingPermissions()
      throws URISyntaxException, IOException {
    Path extraFilesDirectory = Paths.get(Resources.getResource("core/layer").toURI());
    Map<String, FilePermissions> permissionsMap =
        ImmutableMap.of(
            "/a**",
            FilePermissions.fromOctalString("123"),
            // Should be ignored, since first match takes priority
            "/a/b**",
            FilePermissions.fromOctalString("000"),
            // Should override first match since explicit path is used instead of glob
            "/a/b/bar",
            FilePermissions.fromOctalString("765"));
    FileEntriesLayer fileEntriesLayer =
        JavaContainerBuilderHelper.extraDirectoryLayerConfiguration(
            extraFilesDirectory,
            AbsoluteUnixPath.get("/"),
            permissionsMap,
            (ignored1, ignored2) -> Instant.EPOCH);
    assertExtractionPathsUnordered(
        Arrays.asList("/a", "/a/b", "/a/b/bar", "/c", "/c/cat", "/foo"),
        fileEntriesLayer.getEntries());

    Map<AbsoluteUnixPath, FilePermissions> expectedPermissions =
        ImmutableMap.<AbsoluteUnixPath, FilePermissions>builder()
            .put(AbsoluteUnixPath.get("/a"), FilePermissions.fromOctalString("123"))
            .put(AbsoluteUnixPath.get("/a/b"), FilePermissions.fromOctalString("123"))
            .put(AbsoluteUnixPath.get("/a/b/bar"), FilePermissions.fromOctalString("765"))
            .put(AbsoluteUnixPath.get("/c"), FilePermissions.DEFAULT_FOLDER_PERMISSIONS)
            .put(AbsoluteUnixPath.get("/c/cat"), FilePermissions.DEFAULT_FILE_PERMISSIONS)
            .put(AbsoluteUnixPath.get("/foo"), FilePermissions.DEFAULT_FILE_PERMISSIONS)
            .build();
    for (FileEntry entry : fileEntriesLayer.getEntries()) {
      Assert.assertEquals(
          expectedPermissions.get(entry.getExtractionPath()), entry.getPermissions());
    }
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
    Files.createFile(temporaryExplodedWar.resolve("WEB-INF/lib/project-dependency-1.0.0.jar"));
    Set<String> projectArtifacts = ImmutableSet.of("project-dependency-1.0.0.jar");

    JavaContainerBuilder javaContainerBuilder =
        JavaContainerBuilder.from(RegistryImage.named("base"))
            .setAppRoot(AbsoluteUnixPath.get("/my/app"));
    JibContainerBuilder jibContainerBuilder =
        JavaContainerBuilderHelper.fromExplodedWar(
            javaContainerBuilder, temporaryExplodedWar, projectArtifacts);
    BuildContext buildContext =
        JibContainerBuilderTestHelper.toBuildContext(
            jibContainerBuilder,
            Containerizer.to(RegistryImage.named("target"))
                .setExecutorService(MoreExecutors.newDirectExecutorService()));

    List<FileEntriesLayer> resourcesLayerConfigurations =
        getLayerConfigurationsByName(buildContext, LayerType.RESOURCES.getName());
    List<FileEntriesLayer> classesLayerConfigurations =
        getLayerConfigurationsByName(buildContext, LayerType.CLASSES.getName());
    List<FileEntriesLayer> dependenciesLayerConfigurations =
        getLayerConfigurationsByName(buildContext, LayerType.DEPENDENCIES.getName());
    List<FileEntriesLayer> snapshotsLayerConfigurations =
        getLayerConfigurationsByName(buildContext, LayerType.SNAPSHOT_DEPENDENCIES.getName());
    List<FileEntriesLayer> projectDependenciesLayerConfigurations =
        getLayerConfigurationsByName(buildContext, LayerType.PROJECT_DEPENDENCIES.getName());

    assertSourcePathsUnordered(
        Collections.singletonList(
            temporaryExplodedWar.resolve("WEB-INF/lib/project-dependency-1.0.0.jar")),
        projectDependenciesLayerConfigurations.get(0).getEntries());
    assertSourcePathsUnordered(
        Collections.singletonList(temporaryExplodedWar.resolve("WEB-INF/lib/dependency-1.0.0.jar")),
        dependenciesLayerConfigurations.get(0).getEntries());
    assertSourcePathsUnordered(
        Collections.singletonList(
            temporaryExplodedWar.resolve("WEB-INF/lib/dependencyX-1.0.0-SNAPSHOT.jar")),
        snapshotsLayerConfigurations.get(0).getEntries());
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
        resourcesLayerConfigurations.get(0).getEntries());
    assertSourcePathsUnordered(
        Arrays.asList(
            temporaryExplodedWar.resolve("WEB-INF/classes/HelloWorld.class"),
            temporaryExplodedWar.resolve("WEB-INF/classes/empty_dir"),
            temporaryExplodedWar.resolve("WEB-INF/classes/package"),
            temporaryExplodedWar.resolve("WEB-INF/classes/package/Other.class")),
        classesLayerConfigurations.get(0).getEntries());

    assertExtractionPathsUnordered(
        Collections.singletonList("/my/app/WEB-INF/lib/dependency-1.0.0.jar"),
        dependenciesLayerConfigurations.get(0).getEntries());
    assertExtractionPathsUnordered(
        Collections.singletonList("/my/app/WEB-INF/lib/dependencyX-1.0.0-SNAPSHOT.jar"),
        snapshotsLayerConfigurations.get(0).getEntries());
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
        resourcesLayerConfigurations.get(0).getEntries());
    assertExtractionPathsUnordered(
        Arrays.asList(
            "/my/app/WEB-INF/classes/HelloWorld.class",
            "/my/app/WEB-INF/classes/empty_dir",
            "/my/app/WEB-INF/classes/package",
            "/my/app/WEB-INF/classes/package/Other.class"),
        classesLayerConfigurations.get(0).getEntries());
  }
}
