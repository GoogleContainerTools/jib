package com.google.cloud.tools.jib.frontend;

import com.google.cloud.tools.jib.configuration.LayerConfiguration;
import com.google.cloud.tools.jib.filesystem.AbsoluteUnixPath;
import com.google.cloud.tools.jib.image.LayerEntry;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Assert;
import org.junit.Test;

/** Tests for {@link JavaLayerConfigurations}. */
public class JavaLayerConfigurationsTest {

  private static JavaLayerConfigurations createFakeConfigurations() throws IOException {
    return JavaLayerConfigurations.builder()
        .setDependencyFiles(
            Collections.singletonList(Paths.get("dependency")),
            AbsoluteUnixPath.get("/dependency/path"))
        .setSnapshotDependencyFiles(
            Collections.singletonList(Paths.get("snapshot dependency")),
            AbsoluteUnixPath.get("/snapshots"))
        .setResourceFiles(
            Collections.singletonList(Paths.get("resource")),
            AbsoluteUnixPath.get("/resources/here"))
        .setClassFiles(
            Collections.singletonList(Paths.get("class")), AbsoluteUnixPath.get("/classes/go/here"))
        .setExplodedWarFiles(
            Collections.singletonList(Paths.get("exploded war")), AbsoluteUnixPath.get("/for/war"))
        .setExtraFiles(
            Collections.singletonList(Paths.get("extra file")),
            AbsoluteUnixPath.get("/some/extras"))
        .build();
  }

  private static List<Path> layerEntriesToSourceFiles(List<LayerEntry> entries) {
    return entries.stream().map(LayerEntry::getSourceFile).collect(Collectors.toList());
  }

  @Test
  public void testLabels() throws IOException {
    JavaLayerConfigurations javaLayerConfigurations = createFakeConfigurations();

    List<String> expectedLabels = new ArrayList<>();
    for (JavaLayerConfigurations.LayerType layerType : JavaLayerConfigurations.LayerType.values()) {
      expectedLabels.add(layerType.getName());
    }
    List<String> actualLabels = new ArrayList<>();
    for (LayerConfiguration layerConfiguration : javaLayerConfigurations.getLayerConfigurations()) {
      actualLabels.add(layerConfiguration.getName());
    }
    Assert.assertEquals(expectedLabels, actualLabels);
  }

  @Test
  public void testSetFiles_files() throws IOException {
    JavaLayerConfigurations javaLayerConfigurations = createFakeConfigurations();

    List<List<Path>> expectedFiles =
        Arrays.asList(
            Collections.singletonList(Paths.get("dependency")),
            Collections.singletonList(Paths.get("snapshot dependency")),
            Collections.singletonList(Paths.get("resource")),
            Collections.singletonList(Paths.get("class")),
            Collections.singletonList(Paths.get("exploded war")),
            Collections.singletonList(Paths.get("extra file")));
    List<List<Path>> actualFiles =
        javaLayerConfigurations
            .getLayerConfigurations()
            .stream()
            .map(LayerConfiguration::getLayerEntries)
            .map(JavaLayerConfigurationsTest::layerEntriesToSourceFiles)
            .collect(Collectors.toList());
    Assert.assertEquals(expectedFiles, actualFiles);
  }

  @Test
  public void testSetFiles_extractionPaths() throws IOException {
    JavaLayerConfigurations configurations = createFakeConfigurations();

    Assert.assertEquals(
        AbsoluteUnixPath.get("/dependency/path"), configurations.getDependencyExtractionPath());
    Assert.assertEquals(
        AbsoluteUnixPath.get("/snapshots"), configurations.getSnapshotDependencyExtractionPath());
    Assert.assertEquals(
        AbsoluteUnixPath.get("/resources/here"), configurations.getResourceExtractionPath());
    Assert.assertEquals(
        AbsoluteUnixPath.get("/classes/go/here"), configurations.getClassExtractionPath());
    Assert.assertEquals(
        AbsoluteUnixPath.get("/for/war"), configurations.getExplodedWarExtractionPath());
    Assert.assertEquals(
        AbsoluteUnixPath.get("/some/extras"), configurations.getExtraFilesExtractionPath());

    Assert.assertEquals(
        AbsoluteUnixPath.get("/dependency/path/dependency"),
        configurations.getDependencyLayerEntries().get(0).getExtractionPath());
    Assert.assertEquals(
        AbsoluteUnixPath.get("/snapshots/snapshot dependency"),
        configurations.getSnapshotDependencyLayerEntries().get(0).getExtractionPath());
    Assert.assertEquals(
        AbsoluteUnixPath.get("/resources/here/resource"),
        configurations.getResourceLayerEntries().get(0).getExtractionPath());
    Assert.assertEquals(
        AbsoluteUnixPath.get("/classes/go/here/class"),
        configurations.getClassLayerEntries().get(0).getExtractionPath());
    Assert.assertEquals(
        AbsoluteUnixPath.get("/for/war/exploded war"),
        configurations.getExplodedWarEntries().get(0).getExtractionPath());
    Assert.assertEquals(
        AbsoluteUnixPath.get("/some/extras/extra file"),
        configurations.getExtraFilesLayerEntries().get(0).getExtractionPath());
  }
}
