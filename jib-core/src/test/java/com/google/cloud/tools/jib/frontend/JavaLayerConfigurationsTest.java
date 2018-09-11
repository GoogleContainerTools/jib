package com.google.cloud.tools.jib.frontend;

import com.google.cloud.tools.jib.configuration.LayerConfiguration;
import com.google.cloud.tools.jib.image.LayerEntry;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

/** Tests for {@link JavaLayerConfigurations}. */
public class JavaLayerConfigurationsTest {

  @Test
  public void testDefault() {
    JavaLayerConfigurations javaLayerConfigurations = JavaLayerConfigurations.builder().build();

    LayerEntry dependenciesLayerEntry = javaLayerConfigurations.getDependenciesLayerEntry();
    LayerEntry snapshotDependenciesLayerEntry =
        javaLayerConfigurations.getSnapshotDependenciesLayerEntry();
    LayerEntry resourcesLayerEntry = javaLayerConfigurations.getResourcesLayerEntry();
    LayerEntry classesLayerEntry = javaLayerConfigurations.getClassesLayerEntry();
    LayerEntry explodedWarEntry = javaLayerConfigurations.getExplodedWarEntry();
    LayerEntry extraFilesLayerEntry = javaLayerConfigurations.getExtraFilesLayerEntry();

    Assert.assertEquals(
        JavaEntrypointConstructor.DEFAULT_DEPENDENCIES_PATH_ON_IMAGE,
        dependenciesLayerEntry.getExtractionPath());
    Assert.assertEquals(
        JavaEntrypointConstructor.DEFAULT_DEPENDENCIES_PATH_ON_IMAGE,
        snapshotDependenciesLayerEntry.getExtractionPath());
    Assert.assertEquals(
        JavaEntrypointConstructor.DEFAULT_RESOURCES_PATH_ON_IMAGE,
        resourcesLayerEntry.getExtractionPath());
    Assert.assertEquals(
        JavaEntrypointConstructor.DEFAULT_JETTY_BASE_ON_IMAGE,
        explodedWarEntry.getExtractionPath());
    Assert.assertEquals(
        JavaEntrypointConstructor.DEFAULT_CLASSES_PATH_ON_IMAGE,
        classesLayerEntry.getExtractionPath());
    Assert.assertEquals("/", extraFilesLayerEntry.getExtractionPath());
    Assert.assertTrue(dependenciesLayerEntry.getSourceFiles().isEmpty());
    Assert.assertTrue(snapshotDependenciesLayerEntry.getSourceFiles().isEmpty());
    Assert.assertTrue(resourcesLayerEntry.getSourceFiles().isEmpty());
    Assert.assertTrue(classesLayerEntry.getSourceFiles().isEmpty());
    Assert.assertTrue(explodedWarEntry.getSourceFiles().isEmpty());
    Assert.assertTrue(extraFilesLayerEntry.getSourceFiles().isEmpty());

    List<String> expectedLabels = new ArrayList<>();
    for (JavaLayerConfigurations.LayerType layerType : JavaLayerConfigurations.LayerType.values()) {
      expectedLabels.add(layerType.getLabel());
    }
    List<String> actualLabels = new ArrayList<>();
    for (LayerConfiguration layerConfiguration : javaLayerConfigurations.getLayerConfigurations()) {
      actualLabels.add(layerConfiguration.getLabel());
    }
    Assert.assertEquals(expectedLabels, actualLabels);
  }

  @Test
  public void testSetFiles() {
    List<Path> dependencyFiles = Collections.singletonList(Paths.get("dependency"));
    List<Path> snapshotDependencyFiles =
        Collections.singletonList(Paths.get("snapshot dependency"));
    List<Path> resourceFiles = Collections.singletonList(Paths.get("resource"));
    List<Path> classFiles = Collections.singletonList(Paths.get("class"));
    List<Path> explodedWarFiles = Collections.singletonList(Paths.get("exploded war"));
    List<Path> extraFiles = Collections.singletonList(Paths.get("extra file"));

    JavaLayerConfigurations javaLayerConfigurations =
        JavaLayerConfigurations.builder()
            .setDependenciesFiles(dependencyFiles)
            .setSnapshotDependenciesFiles(snapshotDependencyFiles)
            .setResourcesFiles(resourceFiles)
            .setClassesFiles(classFiles)
            .setExplodedWar(explodedWarFiles)
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
      actualFiles.add(layerConfiguration.getLayerEntries().get(0).getSourceFiles());
    }
    Assert.assertEquals(expectedFiles, actualFiles);
  }
}
