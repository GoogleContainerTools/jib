package com.google.cloud.tools.jib.frontend;

import com.google.cloud.tools.jib.configuration.LayerConfiguration;
import com.google.cloud.tools.jib.image.LayerEntry;
import com.google.common.collect.ImmutableList;
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

  @Test
  public void testDefault() throws IOException {
    JavaLayerConfigurations javaLayerConfigurations =
        JavaLayerConfigurations.builder()
            .setDependencyFiles(Collections.singletonList(Paths.get("dependency")))
            .setSnapshotDependencyFiles(Collections.singletonList(Paths.get("snapshotDependency")))
            .setResourceFiles(Collections.singletonList(Paths.get("resource")))
            .setClassFiles(Collections.singletonList(Paths.get("class")))
            .setExplodedWarFiles(Collections.singletonList(Paths.get("exploded-war")))
            .setExtraFiles(Collections.singletonList(Paths.get("extra")))
            .build();

    ImmutableList<LayerEntry> dependencyLayerEntries =
        javaLayerConfigurations.getDependencyLayerEntries();
    ImmutableList<LayerEntry> snapshotDependencyLayerEntries =
        javaLayerConfigurations.getSnapshotDependencyLayerEntries();
    ImmutableList<LayerEntry> resourceLayerEntries =
        javaLayerConfigurations.getResourceLayerEntries();
    ImmutableList<LayerEntry> classLayerEntries = javaLayerConfigurations.getClassLayerEntries();
    ImmutableList<LayerEntry> explodedWarLayerEntries =
        javaLayerConfigurations.getExplodedWarEntries();
    ImmutableList<LayerEntry> extraFilesLayerEntries =
        javaLayerConfigurations.getExtraFilesLayerEntries();

    Assert.assertEquals(
        Paths.get(JavaEntrypointConstructor.DEFAULT_DEPENDENCIES_PATH_ON_IMAGE)
            .resolve("dependency"),
        dependencyLayerEntries.get(0).getExtractionPath());
    Assert.assertEquals(
        Paths.get(JavaEntrypointConstructor.DEFAULT_DEPENDENCIES_PATH_ON_IMAGE)
            .resolve("snapshotDependency"),
        snapshotDependencyLayerEntries.get(0).getExtractionPath());
    Assert.assertEquals(
        Paths.get(JavaEntrypointConstructor.DEFAULT_RESOURCES_PATH_ON_IMAGE).resolve("resource"),
        resourceLayerEntries.get(0).getExtractionPath());
    Assert.assertEquals(
        Paths.get(JavaEntrypointConstructor.DEFAULT_CLASSES_PATH_ON_IMAGE).resolve("class"),
        classLayerEntries.get(0).getExtractionPath());
    Assert.assertEquals(
        Paths.get(JavaEntrypointConstructor.DEFAULT_JETTY_BASE_ON_IMAGE).resolve("exploded-war"),
        explodedWarLayerEntries.get(0).getExtractionPath());
    Assert.assertEquals(Paths.get("/extra"), extraFilesLayerEntries.get(0).getExtractionPath());

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
  public void testSetFiles() throws IOException {
    List<Path> dependencyFiles = Collections.singletonList(Paths.get("/dependency"));
    List<Path> snapshotDependencyFiles =
        Collections.singletonList(Paths.get("/snapshot dependency"));
    List<Path> resourceFiles = Collections.singletonList(Paths.get("/resource"));
    List<Path> classFiles = Collections.singletonList(Paths.get("/class"));
    List<Path> explodedWarFiles = Collections.singletonList(Paths.get("/exploded war"));
    List<Path> extraFiles = Collections.singletonList(Paths.get("/extra file"));

    JavaLayerConfigurations javaLayerConfigurations =
        JavaLayerConfigurations.builder()
            .setDependencyFiles(dependencyFiles)
            .setSnapshotDependencyFiles(snapshotDependencyFiles)
            .setResourceFiles(resourceFiles)
            .setClassFiles(classFiles)
            .setExplodedWarFiles(explodedWarFiles)
            .setExtraFiles(extraFiles)
            .build();

    List<List<Path>> expectedFiles =
        Arrays.asList(
            dependencyFiles,
            snapshotDependencyFiles,
            resourceFiles,
            classFiles,
            explodedWarFiles,
            extraFiles);
    List<List<Path>> actualFiles = new ArrayList<>();
    for (LayerConfiguration layerConfiguration : javaLayerConfigurations.getLayerConfigurations()) {
      actualFiles.add(
          layerConfiguration
              .getLayerEntries()
              .stream()
              .map(LayerEntry::getSourceFile)
              .collect(Collectors.toList()));
    }
    Assert.assertEquals(expectedFiles, actualFiles);
  }
}
