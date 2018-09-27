package com.google.cloud.tools.jib.frontend;

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
import java.util.function.Supplier;
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

  private static List<AbsoluteUnixPath> layerEntriesToExtractionPaths(List<LayerEntry> entries) {
    return entries.stream().map(LayerEntry::getExtractionPath).collect(Collectors.toList());
  }

  private static <T> List<String> toSortedStrings(List<T> paths) {
    return paths.stream().map(T::toString).sorted().collect(Collectors.toList());
  }

  private static void verifyRecursiveAdd(
      Supplier<List<LayerEntry>> layerEntriesSupplier, Path sourceRoot, String extractionRoot) {
    AbsoluteUnixPath extractionRootPath = AbsoluteUnixPath.get(extractionRoot);
    List<String> expectedPaths =
        Arrays.asList(
            "",
            "file1",
            "file2",
            "sub-directory",
            "sub-directory/file3",
            "sub-directory/file4",
            "sub-directory/leaf",
            "sub-directory/leaf/file5",
            "sub-directory/leaf/file6");

    List<Path> expectedSourcePaths =
        expectedPaths.stream().map(sourceRoot::resolve).collect(Collectors.toList());
    List<AbsoluteUnixPath> expectedTargetPaths =
        expectedPaths.stream().map(extractionRootPath::resolve).collect(Collectors.toList());

    List<Path> sourcePaths = layerEntriesToSourceFiles(layerEntriesSupplier.get());
    Assert.assertEquals(toSortedStrings(expectedSourcePaths), toSortedStrings(sourcePaths));

    List<AbsoluteUnixPath> targetPaths = layerEntriesToExtractionPaths(layerEntriesSupplier.get());
    Assert.assertEquals(toSortedStrings(expectedTargetPaths), toSortedStrings(targetPaths));
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
  public void testAddFile_directories() throws IOException, URISyntaxException {
    Path sourceDirectory = Paths.get(Resources.getResource("random-contents").toURI());

    JavaLayerConfigurations configurations =
        JavaLayerConfigurations.builder()
            .addDependencyFile(sourceDirectory, AbsoluteUnixPath.get("/libs/dir"))
            .addSnapshotDependencyFile(sourceDirectory, AbsoluteUnixPath.get("/snapshots/target"))
            .addResourceFile(sourceDirectory, AbsoluteUnixPath.get("/resources"))
            .addClassFile(sourceDirectory, AbsoluteUnixPath.get("/classes/here"))
            .addExplodedWarFile(sourceDirectory, AbsoluteUnixPath.get("/exploded-war"))
            .addExtraFile(sourceDirectory, AbsoluteUnixPath.get("/extra/files"))
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
  public void testAddFile_regularFiles() throws IOException, URISyntaxException {
    Path sourceFile =
        Paths.get(Resources.getResource("random-contents/sub-directory/leaf/file6").toURI());

    JavaLayerConfigurations configurations =
        JavaLayerConfigurations.builder()
            .addDependencyFile(sourceFile, AbsoluteUnixPath.get("/libs/file"))
            .addSnapshotDependencyFile(sourceFile, AbsoluteUnixPath.get("/snapshots/target/file"))
            .addResourceFile(sourceFile, AbsoluteUnixPath.get("/resources-file"))
            .addClassFile(sourceFile, AbsoluteUnixPath.get("/classes/file"))
            .addExplodedWarFile(sourceFile, AbsoluteUnixPath.get("/exploded-war/file"))
            .addExtraFile(sourceFile, AbsoluteUnixPath.get("/some/file"))
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
}
