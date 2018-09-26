package com.google.cloud.tools.jib.frontend;

import com.google.api.client.repackaged.com.google.common.base.Supplier;
import com.google.cloud.tools.jib.configuration.LayerConfiguration;
import com.google.cloud.tools.jib.filesystem.AbsoluteUnixPath;
import com.google.cloud.tools.jib.image.LayerEntry;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URISyntaxException;
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
        .addDependencyFile(Paths.get("dependency"), AbsoluteUnixPath.get("/dependency/path"))
        .addSnapshotDependencyFile(
            Paths.get("snapshot dependency"), AbsoluteUnixPath.get("/snapshots"))
        .addResourceFile(Paths.get("resource"), AbsoluteUnixPath.get("/resources/here"))
        .addClassFile(Paths.get("class"), AbsoluteUnixPath.get("/classes/go/here"))
        .addExplodedWarFile(Paths.get("exploded war"), AbsoluteUnixPath.get("/for/war"))
        .addExtraFile(Paths.get("extra file"), AbsoluteUnixPath.get("/some/extras"))
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
  public void testAddFile() throws IOException {
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
  public void testAddFileRecursive_directories() throws IOException, URISyntaxException {
    Path sourceDirectory = Paths.get(Resources.getResource("application").toURI());
    sourceDirectory.getParent().relativize(sourceDirectory);

    JavaLayerConfigurations configurations =
        JavaLayerConfigurations.builder()
            .addDependencyFileRecursive(sourceDirectory, AbsoluteUnixPath.get("/libs/dir"))
            .addSnapshotDependencyFileRecursive(
                sourceDirectory, AbsoluteUnixPath.get("/snapshots/target"))
            .addResourceFileRecursive(sourceDirectory, AbsoluteUnixPath.get("/resources"))
            .addClassFileRecursive(sourceDirectory, AbsoluteUnixPath.get("/classes/here"))
            .addExplodedWarFileRecursive(sourceDirectory, AbsoluteUnixPath.get("/exploded-war"))
            .addExtraFileRecursive(sourceDirectory, AbsoluteUnixPath.get("/extra/files"))
            .build();

    verifyRecursiveAdd(configurations::getDependencyLayerEntries, sourceDirectory, "/libs/dir");
    verifyRecursiveAdd(
        configurations::getSnapshotDependencyLayerEntries, sourceDirectory, "/snapshots/target");
    verifyRecursiveAdd(configurations::getResourceLayerEntries, sourceDirectory, "/resources");
    verifyRecursiveAdd(configurations::getClassLayerEntries, sourceDirectory, "/classes/here");
    verifyRecursiveAdd(configurations::getExplodedWarEntries, sourceDirectory, "/exploded-war");
    verifyRecursiveAdd(configurations::getExtraFilesLayerEntries, sourceDirectory, "/extra/files");
  }

  @Test
  public void testAddFileRecursive_regularFiles() throws IOException, URISyntaxException {
    Path sourceFile = Paths.get(Resources.getResource("application/resources/world").toURI());

    JavaLayerConfigurations configurations =
        JavaLayerConfigurations.builder()
            .addDependencyFileRecursive(sourceFile, AbsoluteUnixPath.get("/libs/file"))
            .addSnapshotDependencyFileRecursive(
                sourceFile, AbsoluteUnixPath.get("/snapshots/target/file"))
            .addResourceFileRecursive(sourceFile, AbsoluteUnixPath.get("/resources-file"))
            .addClassFileRecursive(sourceFile, AbsoluteUnixPath.get("/classes/file"))
            .addExplodedWarFileRecursive(sourceFile, AbsoluteUnixPath.get("/exploded-war/file"))
            .addExtraFileRecursive(sourceFile, AbsoluteUnixPath.get("/some/file"))
            .build();

    Assert.assertEquals(
        Arrays.asList(new LayerEntry(sourceFile, AbsoluteUnixPath.get("/libs/file"))),
        configurations.getDependencyLayerEntries());
    Assert.assertEquals(
        Arrays.asList(new LayerEntry(sourceFile, AbsoluteUnixPath.get("/snapshots/target/file"))),
        configurations.getSnapshotDependencyLayerEntries());
    Assert.assertEquals(
        Arrays.asList(new LayerEntry(sourceFile, AbsoluteUnixPath.get("/resources-file"))),
        configurations.getResourceLayerEntries());
    Assert.assertEquals(
        Arrays.asList(new LayerEntry(sourceFile, AbsoluteUnixPath.get("/classes/file"))),
        configurations.getClassLayerEntries());
    Assert.assertEquals(
        Arrays.asList(new LayerEntry(sourceFile, AbsoluteUnixPath.get("/exploded-war/file"))),
        configurations.getExplodedWarEntries());
    Assert.assertEquals(
        Arrays.asList(new LayerEntry(sourceFile, AbsoluteUnixPath.get("/some/file"))),
        configurations.getExtraFilesLayerEntries());
  }

  private static void verifyRecursiveAdd(
      Supplier<List<LayerEntry>> layerEntriesSupplier, Path sourceRoot, String extractionRoot) {
    Assert.assertEquals(12, layerEntriesSupplier.get().size());

    for (LayerEntry entry : layerEntriesSupplier.get()) {
      Path relativeSourcePath = sourceRoot.relativize(entry.getSourceFile());
      AbsoluteUnixPath expectedPath =
          AbsoluteUnixPath.get(extractionRoot).resolve(relativeSourcePath);
      Assert.assertEquals(expectedPath, entry.getExtractionPath());
    }
  }
}
