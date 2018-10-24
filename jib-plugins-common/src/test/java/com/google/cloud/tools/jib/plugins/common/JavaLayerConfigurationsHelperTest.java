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

import com.google.cloud.tools.jib.filesystem.AbsoluteUnixPath;
import com.google.cloud.tools.jib.frontend.JavaLayerConfigurations;
import com.google.cloud.tools.jib.image.LayerEntry;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.Assert;
import org.junit.Test;

/** Tests for {@link JavaLayerConfigurationsHelper}. */
public class JavaLayerConfigurationsHelperTest {

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

  @Test
  public void testFromExplodedWar() throws URISyntaxException, IOException {
    Path explodedWar = Paths.get(Resources.getResource("exploded-war").toURI());
    Files.createDirectories(explodedWar.resolve("WEB-INF/classes/empty_dir"));
    Path extraFilesDirectory = Paths.get(Resources.getResource("layer").toURI());

    JavaLayerConfigurations configuration =
        JavaLayerConfigurationsHelper.fromExplodedWar(
            explodedWar,
            AbsoluteUnixPath.get("/my/app"),
            extraFilesDirectory,
            Collections.emptyMap());

    assertSourcePathsUnordered(
        Collections.singletonList(explodedWar.resolve("WEB-INF/lib/dependency-1.0.0.jar")),
        configuration.getDependencyLayerEntries());
    assertSourcePathsUnordered(
        Collections.singletonList(
            explodedWar.resolve("WEB-INF/lib/dependencyX-1.0.0-SNAPSHOT.jar")),
        configuration.getSnapshotDependencyLayerEntries());
    assertSourcePathsUnordered(
        Arrays.asList(
            explodedWar.resolve("META-INF"),
            explodedWar.resolve("META-INF/context.xml"),
            explodedWar.resolve("Test.jsp"),
            explodedWar.resolve("WEB-INF"),
            explodedWar.resolve("WEB-INF/classes"),
            explodedWar.resolve("WEB-INF/classes/empty_dir"),
            explodedWar.resolve("WEB-INF/classes/package"),
            explodedWar.resolve("WEB-INF/classes/package/test.properties"),
            explodedWar.resolve("WEB-INF/lib"),
            explodedWar.resolve("WEB-INF/web.xml")),
        configuration.getResourceLayerEntries());
    assertSourcePathsUnordered(
        Arrays.asList(
            explodedWar.resolve("WEB-INF/classes/HelloWorld.class"),
            explodedWar.resolve("WEB-INF/classes/empty_dir"),
            explodedWar.resolve("WEB-INF/classes/package"),
            explodedWar.resolve("WEB-INF/classes/package/Other.class")),
        configuration.getClassLayerEntries());
    assertSourcePathsUnordered(
        Arrays.asList(
            extraFilesDirectory.resolve("a"),
            extraFilesDirectory.resolve("a/b"),
            extraFilesDirectory.resolve("a/b/bar"),
            extraFilesDirectory.resolve("c"),
            extraFilesDirectory.resolve("c/cat"),
            extraFilesDirectory.resolve("foo")),
        configuration.getExtraFilesLayerEntries());

    assertExtractionPathsUnordered(
        Collections.singletonList("/my/app/WEB-INF/lib/dependency-1.0.0.jar"),
        configuration.getDependencyLayerEntries());
    assertExtractionPathsUnordered(
        Collections.singletonList("/my/app/WEB-INF/lib/dependencyX-1.0.0-SNAPSHOT.jar"),
        configuration.getSnapshotDependencyLayerEntries());
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
        configuration.getResourceLayerEntries());
    assertExtractionPathsUnordered(
        Arrays.asList(
            "/my/app/WEB-INF/classes/HelloWorld.class",
            "/my/app/WEB-INF/classes/empty_dir",
            "/my/app/WEB-INF/classes/package",
            "/my/app/WEB-INF/classes/package/Other.class"),
        configuration.getClassLayerEntries());
    assertExtractionPathsUnordered(
        Arrays.asList("/a", "/a/b", "/a/b/bar", "/c", "/c/cat", "/foo"),
        configuration.getExtraFilesLayerEntries());
  }
}
