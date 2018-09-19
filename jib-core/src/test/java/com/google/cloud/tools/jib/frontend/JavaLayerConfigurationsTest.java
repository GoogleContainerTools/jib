package com.google.cloud.tools.jib.frontend;

import com.google.cloud.tools.jib.configuration.LayerConfiguration;
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
        .setDependencyFiles(Collections.singletonList(Paths.get("dependency")), "/dependency/path")
        .setSnapshotDependencyFiles(
            Collections.singletonList(Paths.get("snapshot dependency")), "/snapshots")
        .setResourceFiles(Collections.singletonList(Paths.get("resource")), "/resources/here")
        .setClassFiles(Collections.singletonList(Paths.get("class")), "/classes/go/here")
        .setExplodedWarFiles(Collections.singletonList(Paths.get("exploded war")), "/for/war")
        .setExtraFiles(Collections.singletonList(Paths.get("extra file")), "/some/extras")
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

    Assert.assertEquals("/dependency/path", configurations.getDependencyExtractionPath());
    Assert.assertEquals("/snapshots", configurations.getSnapshotDependencyExtractionPath());
    Assert.assertEquals("/resources/here", configurations.getResourceExtractionPath());
    Assert.assertEquals("/classes/go/here", configurations.getClassExtractionPath());
    Assert.assertEquals("/for/war", configurations.getExplodedWarExtractionPath());
    Assert.assertEquals("/some/extras", configurations.getExtraFilesExtractionPath());

    Assert.assertEquals(
        Paths.get("/dependency/path/dependency"),
        configurations.getDependencyLayerEntries().get(0).getExtractionPath());
    Assert.assertEquals(
        Paths.get("/snapshots/snapshot dependency"),
        configurations.getSnapshotDependencyLayerEntries().get(0).getExtractionPath());
    Assert.assertEquals(
        Paths.get("/resources/here/resource"),
        configurations.getResourceLayerEntries().get(0).getExtractionPath());
    Assert.assertEquals(
        Paths.get("/classes/go/here/class"),
        configurations.getClassLayerEntries().get(0).getExtractionPath());
    Assert.assertEquals(
        Paths.get("/for/war/exploded war"),
        configurations.getExplodedWarEntries().get(0).getExtractionPath());
    Assert.assertEquals(
        Paths.get("/some/extras/extra file"),
        configurations.getExtraFilesLayerEntries().get(0).getExtractionPath());
  }
}
