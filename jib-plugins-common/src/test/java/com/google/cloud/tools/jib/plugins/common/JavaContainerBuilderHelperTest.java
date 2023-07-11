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

import static com.google.common.truth.Truth.assertThat;

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
import com.google.common.truth.Correspondence;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests for {@link JavaContainerBuilderHelper}. */
class JavaContainerBuilderHelperTest {

  private static final Correspondence<FileEntry, Path> SOURCE_FILE_OF =
      Correspondence.transforming(FileEntry::getSourceFile, "has sourceFile of");
  private static final Correspondence<FileEntry, String> EXTRACTION_PATH_OF =
      Correspondence.transforming(
          entry -> entry.getExtractionPath().toString(), "has extractionPath of");

  private static FileEntriesLayer getLayerConfigurationByName(
      BuildContext buildContext, String name) {
    return buildContext.getLayerConfigurations().stream()
        .filter(layer -> layer.getName().equals(name))
        .findFirst()
        .get();
  }

  @TempDir public Path temporaryFolder;

  @Test
  void testExtraDirectoryLayerConfiguration() throws URISyntaxException, IOException {
    Path extraFilesDirectory = Paths.get(Resources.getResource("core/layer").toURI());
    FileEntriesLayer layerConfiguration =
        JavaContainerBuilderHelper.extraDirectoryLayerConfiguration(
            extraFilesDirectory,
            AbsoluteUnixPath.get("/"),
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyMap(),
            (ignored1, ignored2) -> Instant.EPOCH);
    assertThat(layerConfiguration.getEntries())
        .comparingElementsUsing(SOURCE_FILE_OF)
        .containsExactly(
            extraFilesDirectory.resolve("a"),
            extraFilesDirectory.resolve("a/b"),
            extraFilesDirectory.resolve("a/b/bar"),
            extraFilesDirectory.resolve("c"),
            extraFilesDirectory.resolve("c/cat"),
            extraFilesDirectory.resolve("foo"));
  }

  @Test
  void testExtraDirectoryLayerConfiguration_includes() throws URISyntaxException, IOException {
    Path extraFilesDirectory = Paths.get(Resources.getResource("core/layer").toURI());
    FileEntriesLayer layerConfiguration =
        JavaContainerBuilderHelper.extraDirectoryLayerConfiguration(
            extraFilesDirectory,
            AbsoluteUnixPath.get("/"),
            Arrays.asList("**/bar", "**/*a*"),
            Collections.emptyList(),
            Collections.emptyMap(),
            (ignored1, ignored2) -> Instant.EPOCH);
    assertThat(layerConfiguration.getEntries())
        .comparingElementsUsing(SOURCE_FILE_OF)
        .containsExactly(
            extraFilesDirectory.resolve("a/b/bar"), extraFilesDirectory.resolve("c/cat"));
  }

  @Test
  void testExtraDirectoryLayerConfiguration_excludes() throws URISyntaxException, IOException {
    Path extraFilesDirectory = Paths.get(Resources.getResource("core/layer").toURI());
    FileEntriesLayer layerConfiguration =
        JavaContainerBuilderHelper.extraDirectoryLayerConfiguration(
            extraFilesDirectory,
            AbsoluteUnixPath.get("/"),
            Collections.emptyList(),
            Arrays.asList("**/bar", "**/*a*"),
            Collections.emptyMap(),
            (ignored1, ignored2) -> Instant.EPOCH);
    assertThat(layerConfiguration.getEntries())
        .comparingElementsUsing(SOURCE_FILE_OF)
        .containsExactly(
            extraFilesDirectory.resolve("a"),
            extraFilesDirectory.resolve("a/b"),
            extraFilesDirectory.resolve("c"),
            extraFilesDirectory.resolve("foo"));
  }

  @Test
  void testExtraDirectoryLayerConfiguration_includesAndExcludesEverything()
      throws URISyntaxException, IOException {
    Path extraFilesDirectory = Paths.get(Resources.getResource("core/layer").toURI());
    FileEntriesLayer layerConfiguration =
        JavaContainerBuilderHelper.extraDirectoryLayerConfiguration(
            extraFilesDirectory,
            AbsoluteUnixPath.get("/"),
            Arrays.asList("**/*"),
            Arrays.asList("**/*"),
            Collections.emptyMap(),
            (ignored1, ignored2) -> Instant.EPOCH);
    assertThat(layerConfiguration.getEntries()).isEmpty();
  }

  @Test
  void testExtraDirectoryLayerConfiguration_includesAndExcludes()
      throws URISyntaxException, IOException {
    Path extraFilesDirectory = Paths.get(Resources.getResource("core/layer").toURI());
    FileEntriesLayer layerConfiguration =
        JavaContainerBuilderHelper.extraDirectoryLayerConfiguration(
            extraFilesDirectory,
            AbsoluteUnixPath.get("/"),
            Arrays.asList("**/*a*", "a"),
            Arrays.asList("**/*c*"),
            Collections.emptyMap(),
            (ignored1, ignored2) -> Instant.EPOCH);
    assertThat(layerConfiguration.getEntries())
        .comparingElementsUsing(SOURCE_FILE_OF)
        .containsExactly(extraFilesDirectory.resolve("a"), extraFilesDirectory.resolve("a/b/bar"));
  }

  @Test
  void testExtraDirectoryLayerConfiguration_globPermissions()
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
            Collections.emptyList(),
            Collections.emptyList(),
            permissionsMap,
            (ignored1, ignored2) -> Instant.EPOCH);
    assertThat(fileEntriesLayer.getEntries())
        .comparingElementsUsing(EXTRACTION_PATH_OF)
        .containsExactly("/a", "/a/b", "/a/b/bar", "/c", "/c/cat", "/foo");

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
      assertThat(entry.getPermissions())
          .isEqualTo(expectedPermissions.get(entry.getExtractionPath()));
    }
  }

  @Test
  void testExtraDirectoryLayerConfiguration_overlappingPermissions()
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
            Collections.emptyList(),
            Collections.emptyList(),
            permissionsMap,
            (ignored1, ignored2) -> Instant.EPOCH);
    assertThat(fileEntriesLayer.getEntries())
        .comparingElementsUsing(EXTRACTION_PATH_OF)
        .containsExactly("/a", "/a/b", "/a/b/bar", "/c", "/c/cat", "/foo");

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
      assertThat(entry.getPermissions())
          .isEqualTo(expectedPermissions.get(entry.getExtractionPath()));
    }
  }

  @Test
  void testFromExplodedWar()
      throws URISyntaxException, IOException, InvalidImageReferenceException,
          CacheDirectoryCreationException {
    // Copy test files to a temporary directory that we can safely operate on
    Path resourceExplodedWar =
        Paths.get(Resources.getResource("plugins-common/exploded-war").toURI());
    FileOperations.copy(ImmutableList.of(resourceExplodedWar), temporaryFolder);
    Path temporaryExplodedWar = temporaryFolder.resolve("exploded-war");
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

    FileEntriesLayer resourcesLayerConfigurations =
        getLayerConfigurationByName(buildContext, LayerType.RESOURCES.getName());
    FileEntriesLayer classesLayerConfigurations =
        getLayerConfigurationByName(buildContext, LayerType.CLASSES.getName());
    FileEntriesLayer dependenciesLayerConfigurations =
        getLayerConfigurationByName(buildContext, LayerType.DEPENDENCIES.getName());
    FileEntriesLayer snapshotsLayerConfigurations =
        getLayerConfigurationByName(buildContext, LayerType.SNAPSHOT_DEPENDENCIES.getName());
    FileEntriesLayer projectDependenciesLayerConfigurations =
        getLayerConfigurationByName(buildContext, LayerType.PROJECT_DEPENDENCIES.getName());

    assertThat(projectDependenciesLayerConfigurations.getEntries())
        .comparingElementsUsing(SOURCE_FILE_OF)
        .containsExactly(temporaryExplodedWar.resolve("WEB-INF/lib/project-dependency-1.0.0.jar"));
    assertThat(dependenciesLayerConfigurations.getEntries())
        .comparingElementsUsing(SOURCE_FILE_OF)
        .containsExactly(temporaryExplodedWar.resolve("WEB-INF/lib/dependency-1.0.0.jar"));
    assertThat(snapshotsLayerConfigurations.getEntries())
        .comparingElementsUsing(SOURCE_FILE_OF)
        .containsExactly(
            temporaryExplodedWar.resolve("WEB-INF/lib/dependencyX-1.0.0-SNAPSHOT.jar"));
    assertThat(resourcesLayerConfigurations.getEntries())
        .comparingElementsUsing(SOURCE_FILE_OF)
        .containsExactly(
            temporaryExplodedWar.resolve("META-INF"),
            temporaryExplodedWar.resolve("META-INF/context.xml"),
            temporaryExplodedWar.resolve("Test.jsp"),
            temporaryExplodedWar.resolve("WEB-INF"),
            temporaryExplodedWar.resolve("WEB-INF/classes"),
            temporaryExplodedWar.resolve("WEB-INF/classes/empty_dir"),
            temporaryExplodedWar.resolve("WEB-INF/classes/package"),
            temporaryExplodedWar.resolve("WEB-INF/classes/package/test.properties"),
            temporaryExplodedWar.resolve("WEB-INF/lib"),
            temporaryExplodedWar.resolve("WEB-INF/web.xml"));
    assertThat(classesLayerConfigurations.getEntries())
        .comparingElementsUsing(SOURCE_FILE_OF)
        .containsExactly(
            temporaryExplodedWar.resolve("WEB-INF/classes/HelloWorld.class"),
            temporaryExplodedWar.resolve("WEB-INF/classes/empty_dir"),
            temporaryExplodedWar.resolve("WEB-INF/classes/package"),
            temporaryExplodedWar.resolve("WEB-INF/classes/package/Other.class"));

    assertThat(dependenciesLayerConfigurations.getEntries())
        .comparingElementsUsing(EXTRACTION_PATH_OF)
        .containsExactly("/my/app/WEB-INF/lib/dependency-1.0.0.jar");
    assertThat(snapshotsLayerConfigurations.getEntries())
        .comparingElementsUsing(EXTRACTION_PATH_OF)
        .containsExactly("/my/app/WEB-INF/lib/dependencyX-1.0.0-SNAPSHOT.jar");
    assertThat(resourcesLayerConfigurations.getEntries())
        .comparingElementsUsing(EXTRACTION_PATH_OF)
        .containsExactly(
            "/my/app/META-INF",
            "/my/app/META-INF/context.xml",
            "/my/app/Test.jsp",
            "/my/app/WEB-INF",
            "/my/app/WEB-INF/classes",
            "/my/app/WEB-INF/classes/empty_dir",
            "/my/app/WEB-INF/classes/package",
            "/my/app/WEB-INF/classes/package/test.properties",
            "/my/app/WEB-INF/lib",
            "/my/app/WEB-INF/web.xml");
    assertThat(classesLayerConfigurations.getEntries())
        .comparingElementsUsing(EXTRACTION_PATH_OF)
        .containsExactly(
            "/my/app/WEB-INF/classes/HelloWorld.class",
            "/my/app/WEB-INF/classes/empty_dir",
            "/my/app/WEB-INF/classes/package",
            "/my/app/WEB-INF/classes/package/Other.class");
  }
}
